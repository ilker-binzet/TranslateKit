package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import bin.mt.json.JSONArray;
import bin.mt.json.JSONObject;
import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.translation.BaseBatchTranslationEngine;
import bin.mt.plugin.api.translation.BatchTranslationEngine;
import bin.mt.plugin.common.HttpUtils;
import bin.mt.plugin.common.JSONCompat;
import bin.mt.plugin.provider.Provider;
import bin.mt.plugin.provider.ProviderClient;
import bin.mt.plugin.provider.Providers;

/**
 * Multi-provider translation engine for MT Manager.
 *
 * Translates Android string resources by prompting a large language model.
 * The active provider comes from {@link Providers}; this class owns the
 * prompt, the batching, the placeholder protection and the retry budget,
 * while {@link ProviderClient} owns the wire format.
 *
 * API Documentation: https://ai.google.dev/gemini-api/docs
 *
 * @author Ilker Binzet
 * @version 1.0.0
 */
public class GeminiTranslationEngine extends BaseBatchTranslationEngine {

    /**
     * Pattern for detecting placeholders in Android strings.
     * Covers printf (%s, %1$s, %d), ICU ({0}, {name}), template ({{value}}),
     * HTML tags, shell/template variables ($PATH, ${var}),
     * Android escape sequences (\n, \t, \'), and HTML entities (&amp;, &#123;).
     */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
        "(%(?:\\d+\\$)?[-+# 0,(]*\\d*\\.?\\d*[sdfiboxXeEgGcChHnAt%])" +
        "|(\\{\\{[^}]*\\}\\})" +
        "|(\\{[^}]*\\})" +
        "|(<[^>]+>)" +
        "|(\\$\\{[^}]+\\})" +
        "|(\\$[A-Za-z_]\\w*)" +
        "|(\\\\[nrt'\\\"\\\\])" +
        "|(&(?:#\\d+|#x[0-9a-fA-F]+|[a-zA-Z]+);)"
    );

    /** Pattern for non-translatable strings (only symbols, numbers, whitespace) */
    private static final Pattern NON_TRANSLATABLE_PATTERN = Pattern.compile(
        "^[\\p{Punct}\\p{Symbol}\\d\\s]*$"
    );

    private String apiKey;
    private int maxRetries;
    private int requestTimeout;
    private String modelName;
    private String selectedEngine;

    /** The active provider: endpoint, credentials, model and wire format. */
    private Provider provider;

    private boolean debugLogging;
    private SharedPreferences preferences;
    private String userContextDirective = "";
    private TranslationDebugLogger debugLogger;
    private boolean batchEnabled;
    private int batchSize;
    private int batchMaxChars;

    /**
     * Constructor with default configuration
     */
    public GeminiTranslationEngine() {
        super();
    }

    /**
     * Configure the engine: enable separator-based batching, set max text length per call,
     * and keep the parent's defaults (notably autoRepairFormatSpecifiersError = true).
     *
     * Called automatically by the SDK when constructing the engine; the super() call
     * preserves the SDK's safe defaults (autoRepairFormatSpecifiersError = true).
     */
    @Override
    protected void onBuildConfiguration(ConfigurationBuilder builder) {
        super.onBuildConfiguration(builder);
        // MT will join multiple strings with a separator and send them in one
        // translate() call, then split the result. Our translate() is already
        // prompt-safe (preserves separators), so we can use this optimisation.
        builder.setAllowBatchTranslationBySeparator(true);
        // 10k chars per call matches our batch_max_chars default and keeps us
        // safely under every supported provider's input limit.
        builder.setMaxTranslationTextLength(10000);
        // Users may want to keep already-translated entries; respect that.
        builder.setForceNotToSkipTranslated(false);
    }

    /**
     * Initialize the translation engine
     */
    @Override
    protected void init() {
    }

    /**
     * Get the display name of this translation engine
     */
    @NonNull
    @Override
    public String name() {
        return "{plugin_name}";
    }

    /**
     * Load source languages including auto-detection.
     *
     * <p>The list is whatever the user left enabled in Translation Settings, so
     * a picker of two or three languages stays short instead of scrolling
     * through every language the engine can handle.
     */
    @NonNull
    @Override
    public List<String> loadSourceLanguages() {
        List<String> languages = new ArrayList<>();
        languages.add("auto"); // Auto-detection (the model will detect)
        languages.addAll(enabledLanguages());
        return languages;
    }

    /**
     * Load target languages
     */
    @NonNull
    @Override
    public List<String> loadTargetLanguages(String sourceLanguage) {
        return new ArrayList<>(enabledLanguages());
    }

    /**
     * The enabled selection, or every language when settings are unavailable.
     *
     * <p>{@code loadSourceLanguages} can run before {@code onStart}, so this
     * cannot rely on the preferences field the rest of the engine uses.
     */
    private List<String> enabledLanguages() {
        try {
            return Languages.parseEnabled(
                    getContext().getPreferences().getString(Languages.PREF_ENABLED_LANGUAGES, ""));
        } catch (Exception e) {
            return Languages.allCodes();
        }
    }

    /**
     * Convert language code to display name.
     *
     * <p>MT does not know every code we offer — Hebrew arrived in the picker as
     * a bare "he" — so the catalogue fills in whatever MT leaves unresolved.
     */
    @NonNull
    @Override
    public String getLanguageDisplayName(String language) {
        if ("auto".equals(language)) {
            return getContext().getString("{lang_auto}");
        }
        String name = super.getLanguageDisplayName(language);
        if (name == null || name.isEmpty() || name.equals(language)) {
            // The pack answers in the plugin's own language, so a Chinese user
            // reads the language list in Chinese rather than in English.
            return Languages.displayName(getContext(), language);
        }
        return name;
    }

    /**
     * Called before translation batch starts
     */
    @Override
    public void onStart() {
        this.preferences = getContext().getPreferences();
        SharedPreferences prefs = this.preferences;

        maxRetries = readIntPreference(prefs, GeminiConstants.PREF_MAX_RETRIES, GeminiConstants.DEFAULT_MAX_RETRIES);
        requestTimeout = readIntPreference(prefs, GeminiConstants.PREF_TIMEOUT, GeminiConstants.DEFAULT_TIMEOUT);
        debugLogging = prefs.getBoolean(GeminiConstants.PREF_ENABLE_DEBUG, GeminiConstants.DEFAULT_ENABLE_DEBUG);
        debugLogger = new TranslationDebugLogger(getContext(), debugLogging);

        provider = Providers.selected(prefs);

        // A non-Gemini provider that needs a key but has none falls back to
        // Gemini, exactly as earlier versions did for OpenAI and Claude.
        // Gemini itself has no fallback: loadGeminiConfig raises instead.
        if (!Providers.ID_GEMINI.equals(provider.id)
                && provider.requiresKey() && isNullOrEmpty(provider.apiKey)) {
            notifyAndFallbackToGemini(prefs, keyMissingMessageKey(provider.id));
        } else {
            if (!provider.hasValidKeyFormat()) {
                logWarn(provider.displayName + " API key format appears invalid");
            }
            if (Providers.ID_GEMINI.equals(provider.id)) {
                loadGeminiConfig(prefs);
            }
        }

        selectedEngine = provider.id;
        modelName = provider.model;
        logInfo("Using " + provider.displayName + " (model=" + provider.model + ")");

        userContextDirective = buildUserContextDirective(prefs);

        // Load batch configuration
        batchEnabled = prefs.getBoolean(GeminiConstants.PREF_BATCH_ENABLED, GeminiConstants.DEFAULT_BATCH_ENABLED);
        batchSize = readIntPreference(prefs, GeminiConstants.PREF_BATCH_SIZE, GeminiConstants.DEFAULT_BATCH_SIZE);
        batchMaxChars = readIntPreference(prefs, GeminiConstants.PREF_BATCH_MAX_CHARS, GeminiConstants.DEFAULT_BATCH_MAX_CHARS);
        if (batchSize < 1) batchSize = GeminiConstants.DEFAULT_BATCH_SIZE;
        if (batchMaxChars < 100) batchMaxChars = GeminiConstants.DEFAULT_BATCH_MAX_CHARS;
        logInfo("Batch config: enabled=" + batchEnabled + ", size=" + batchSize + ", maxChars=" + batchMaxChars);
    }

    /**
     * Configure batch size limits for the translation engine.
     * Controls how many texts are grouped per API call.
     * Values are user-configurable via SharedPreferences.
     *
     * @return BatchingStrategy with user-configured maxCount and maxDataSize
     */
    @Override
    public BatchTranslationEngine.BatchingStrategy createBatchingStrategy() {
        if (!batchEnabled) {
            return new SimpleBatchingStrategy(1, batchMaxChars);
        }
        return new SimpleBatchingStrategy(batchSize, batchMaxChars);
    }

    /**
     * Translate text using Gemini API (single-text path)
     *
     * This uses the generateContent endpoint with a translation prompt.
     * Gemini will act as a translator based on the prompt.
     *
     * @param text The text to translate
     * @param sourceLanguage Source language code (or "auto")
     * @param targetLanguage Target language code
     * @return Translated text
     * @throws IOException If translation fails
     */
    /**
     * Normalizes legacy Java Locale language codes to modern ISO 639-1.
     * Java's Locale.getLanguage() returns obsolete codes for some languages:
     * "iw" (Hebrew) → "he", "in" (Indonesian) → "id", "ji" (Yiddish) → "yi".
     */
    private static String normalizeLanguageCode(String code) {
        if (code == null) return code;
        switch (code) {
            case "iw": return "he";
            case "in": return "id";
            case "ji": return "yi";
            default: return code;
        }
    }

    private String translateSingle(String text, String sourceLanguage, String targetLanguage) throws IOException {
        sourceLanguage = normalizeLanguageCode(sourceLanguage);
        targetLanguage = normalizeLanguageCode(targetLanguage);

        // Input validation
        if (text == null || text.trim().isEmpty()) {
            return text;
        }

        // Skip translation for non-translatable strings (only symbols/numbers/punctuation)
        if (isNonTranslatable(text)) {
            logInfo("Skipping non-translatable: " + TranslationDebugLogger.sanitizePreview(text));
            return text;
        }

        // Tokenize placeholders for protection
        PlaceholderResult phResult = tokenizePlaceholders(text);

        // Build translation prompt with tokenized text
        String prompt = buildTranslationPrompt(phResult.tokenizedText, sourceLanguage, targetLanguage);
        int inputChars = text.length();
        String preview = TranslationDebugLogger.sanitizePreview(text);
        logInfo("Translate request via " + selectedEngine + " | src=" + sourceLanguage + " -> "
                + targetLanguage + " | chars=" + text.length());

        String result = translateVia(prompt, sourceLanguage, targetLanguage, inputChars, preview);

        // Restore placeholders and validate integrity
        if (phResult.hasPlaceholders()) {
            result = restorePlaceholders(result, phResult.placeholders);
            if (!validatePlaceholders(text, result)) {
                logWarn("Placeholder validation failed, returning original: " + preview);
                return text;
            }
        }

        return result;
    }

    /**
     * Batch translate multiple texts in a single API call.
     *
     * Groups all input texts into a numbered prompt and sends one request
     * instead of N individual requests, dramatically reducing API calls
     * and improving throughput.
     *
     * @param texts Array of texts to translate
     * @param sourceLanguage Source language code (or "auto")
     * @param targetLanguage Target language code
     * @return Array of translated texts in the same order
     * @throws IOException If translation fails
     */
    @NonNull
    @Override
    public String[] batchTranslate(@NonNull String[] texts, String sourceLanguage, String targetLanguage) throws IOException {
        sourceLanguage = normalizeLanguageCode(sourceLanguage);
        targetLanguage = normalizeLanguageCode(targetLanguage);

        if (texts.length == 0) return new String[0];

        // Single text optimization: use direct prompt (more precise, no parsing overhead)
        if (texts.length == 1) {
            return new String[]{ translateSingle(texts[0], sourceLanguage, targetLanguage) };
        }

        int count = texts.length;
        String[] results = new String[count];

        // Pre-process: detect non-translatable strings and tokenize placeholders
        boolean[] needsTranslation = new boolean[count];
        PlaceholderResult[] phResults = new PlaceholderResult[count];
        List<Integer> translatableIndices = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            results[i] = texts[i]; // default: keep original
            if (texts[i] == null || texts[i].trim().isEmpty() || isNonTranslatable(texts[i])) {
                needsTranslation[i] = false;
            } else {
                needsTranslation[i] = true;
                phResults[i] = tokenizePlaceholders(texts[i]);
                translatableIndices.add(i);
            }
        }

        if (translatableIndices.isEmpty()) {
            logInfo("All strings are non-translatable, returning originals");
            return results;
        }

        // Build tokenized texts array for batch (only translatable items)
        String[] tokenizedTexts = new String[translatableIndices.size()];
        int totalChars = 0;
        for (int j = 0; j < translatableIndices.size(); j++) {
            int idx = translatableIndices.get(j);
            tokenizedTexts[j] = phResults[idx].tokenizedText;
            if (tokenizedTexts[j] != null) totalChars += tokenizedTexts[j].length();
        }

        // Create batch span for structured debug logging
        TranslationDebugLogger.BatchSpan batchSpan = debugLogger.newBatchSpan(
                selectedEngine, modelName,
                sourceLanguage, targetLanguage,
                count, translatableIndices.size(), totalChars);
        batchSpan.logPreprocess(count - translatableIndices.size());

        try {
            // Build batch prompt with tokenized texts
            String prompt = buildBatchTranslationPrompt(tokenizedTexts, sourceLanguage, targetLanguage);
            String preview = "[batch:" + tokenizedTexts.length + "] " + totalChars + " chars";

            logInfo("Batch translate via " + selectedEngine + " | count=" + tokenizedTexts.length
                    + " | src=" + sourceLanguage + " -> " + targetLanguage
                    + " | totalChars=" + totalChars);
            batchSpan.logApiCall(prompt.length());

            String rawResponse = translateVia(prompt, sourceLanguage, targetLanguage, totalChars, preview);

            String[] batchResults = parseBatchResponse(rawResponse, tokenizedTexts, batchSpan);

            // Map batch results back to original indices and restore placeholders.
            // Items the model dropped (null) or whose placeholders failed
            // validation are RETRIED INDIVIDUALLY — never silently kept as
            // originals, which the user perceives as "skipped strings".
            List<Integer> retryIndices = new ArrayList<>();
            for (int j = 0; j < translatableIndices.size(); j++) {
                int idx = translatableIndices.get(j);
                String translated = batchResults[j];

                if (translated == null || translated.isEmpty()) {
                    retryIndices.add(idx);
                    continue;
                }

                // Restore placeholders
                if (phResults[idx].hasPlaceholders()) {
                    translated = restorePlaceholders(translated, phResults[idx].placeholders);
                    boolean valid = validatePlaceholders(texts[idx], translated);
                    batchSpan.logPlaceholderRestore(j + 1, valid, valid ? null : "validation failed, retrying individually");
                    if (!valid) {
                        logWarn("Placeholder validation failed for batch item " + (j + 1) + ", retrying individually");
                        retryIndices.add(idx);
                        continue;
                    }
                }

                results[idx] = translated;
            }

            if (!retryIndices.isEmpty()) {
                logWarn("Batch incomplete: " + retryIndices.size() + "/" + translatableIndices.size()
                        + " items missing or invalid — retrying each individually");
                for (int i = 0; i < retryIndices.size(); i++) {
                    int idx = retryIndices.get(i);
                    logInfo("Individual retry " + (i + 1) + "/" + retryIndices.size());
                    try {
                        results[idx] = translateSingle(texts[idx], sourceLanguage, targetLanguage);
                    } catch (IOException singleError) {
                        logError("Individual retry failed, keeping original: "
                                + TranslationDebugLogger.sanitizePreview(texts[idx])
                                + " | " + singleError.getMessage());
                        results[idx] = texts[idx]; // keep original
                    }
                }
            }

            batchSpan.markSuccess(translatableIndices.size() - retryIndices.size());
            logSuccess("Batch translate complete: " + texts.length + " texts ("
                    + retryIndices.size() + " retried individually)");
            return results;

        } catch (IOException e) {
            // Batch failed entirely — fall back to translating each text individually
            batchSpan.markFailure(e.getMessage());
            batchSpan.logFallbackToIndividual(e.getMessage());
            logWarn("Batch translation failed (" + e.getMessage() + "), falling back to individual translation");

            for (int idx : translatableIndices) {
                try {
                    results[idx] = translateSingle(texts[idx], sourceLanguage, targetLanguage);
                } catch (IOException singleError) {
                    logWarn("Individual fallback failed for item " + (idx + 1) + ": " + singleError.getMessage());
                    results[idx] = texts[idx]; // keep original
                }
            }

            return results;
        }
    }

    /**
     * Build translation prompt for Gemini
     *
     * Creates a clear instruction for Gemini to translate the text.
     */
    private String buildTranslationPrompt(String text, String sourceLanguage, String targetLanguage) {
        String sourceLangName = getLanguageDisplayName(sourceLanguage);
        String targetLangName = getLanguageDisplayName(targetLanguage);

        StringBuilder prompt = new StringBuilder();

        if ("auto".equals(sourceLanguage)) {
            prompt.append("Translate the following text to ").append(targetLangName).append(".\n");
        } else {
            prompt.append("Translate the following text from ").append(sourceLangName)
                  .append(" to ").append(targetLangName).append(".\n");
        }

        prompt.append("Context: This content belongs to an Android mobile application UI. Preserve semantics and ensure wording fits an app interface.\n");
        if (!isNullOrEmpty(userContextDirective)) {
            prompt.append(userContextDirective).append('\n');
        }
        prompt.append("IMPORTANT: Return ONLY the translated text, without any explanations, notes, or additional formatting.\n");
        prompt.append("Keep emojis exactly as they appear.\n");
        prompt.append("Tokens like __PH0__, __PH1__ etc. are protected placeholders — keep them EXACTLY as-is (case-sensitive, including double underscores), do not translate, modify, reorder, or remove them.\n");
        prompt.append("Translate only the human-readable words around them.\n");
        prompt.append("Do not add quotes, prefixes, or suffixes. Just the pure translation.\n\n");
        prompt.append("Text to translate:\n");
        prompt.append(text);

        return prompt.toString();
    }

    /**
     * Build a batch translation prompt with numbered texts.
     *
     * Uses [N] prefix format to send multiple texts in a single API call.
     * The AI model translates all texts at once and returns them in the same format.
     *
     * @param texts Array of texts to translate
     * @param sourceLanguage Source language code
     * @param targetLanguage Target language code
     * @return Combined prompt with numbered texts
     */
    private String buildBatchTranslationPrompt(String[] texts, String sourceLanguage, String targetLanguage) {
        String sourceLangName = getLanguageDisplayName(sourceLanguage);
        String targetLangName = getLanguageDisplayName(targetLanguage);

        StringBuilder prompt = new StringBuilder();

        if ("auto".equals(sourceLanguage)) {
            prompt.append("Translate each of the following numbered texts to ").append(targetLangName).append(".\n");
        } else {
            prompt.append("Translate each of the following numbered texts from ").append(sourceLangName)
                  .append(" to ").append(targetLangName).append(".\n");
        }

        prompt.append("Context: These are Android mobile application UI strings. Preserve semantics and ensure wording fits an app interface.\n");
        if (!isNullOrEmpty(userContextDirective)) {
            prompt.append(userContextDirective).append('\n');
        }
        prompt.append("ABSOLUTE RULES:\n");
        prompt.append("- Return ONLY the translations in the EXACT same numbered format: [N] translated text\n");
        prompt.append("- You MUST translate ALL ").append(texts.length).append(" items. Do not skip, merge, or reorder any.\n");
        prompt.append("- Each translation MUST be on its own line starting with [N] where N is the item number.\n");
        prompt.append("- Tokens like __PH0__, __PH1__ etc. are protected placeholders — keep them EXACTLY as-is (case-sensitive, including double underscores).\n");
        prompt.append("- Do NOT translate, modify, reorder, or remove __PH*__ tokens. Their count and order must match the input.\n");
        prompt.append("- Keep emojis exactly as they appear.\n");
        prompt.append("- Do not add quotes, explanations, notes, or any extra text.\n\n");

        for (int i = 0; i < texts.length; i++) {
            prompt.append('[').append(i + 1).append("] ");
            prompt.append(escapeForBatchPrompt(texts[i] != null ? texts[i] : ""));
            prompt.append('\n');
        }

        return prompt.toString();
    }

    /**
     * Parse a batch response with numbered translations.
     *
     * Expects format:
     * [1] Translation one
     * [2] Translation two
     * ...
     *
     * Missing translations are left {@code null} so the caller can retry
     * them individually.
     *
     * @param response Raw AI response
     * @param originalTexts Original texts (used for count/logging only)
     * @return Array of translated texts in the same order; {@code null} slots = missing
     * @throws IOException If response is completely unparseable
     */
    private String[] parseBatchResponse(String response, String[] originalTexts, TranslationDebugLogger.BatchSpan batchSpan) throws IOException {
        int count = originalTexts.length;
        String[] results = new String[count];

        if (response == null || response.trim().isEmpty()) {
            throw new IOException("Empty batch translation response");
        }

        // Strip markdown code blocks if the model wrapped the response
        String cleaned = response.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                cleaned = cleaned.substring(firstNewline + 1, lastFence).trim();
            }
        }

        // Try multiple numbered formats in order of preference, pick the best match
        int bestFound = 0;

        // Format 1: [N] text  (preferred)
        String[] temp1 = new String[count];
        Pattern pattern1 = Pattern.compile(
                "\\[(\\d+)]\\s*(.*?)(?=\\n\\s*\\[\\d+]|$)",
                Pattern.DOTALL
        );
        int found1 = matchBatchEntries(pattern1, cleaned, temp1, count);
        if (found1 > bestFound) {
            bestFound = found1;
            System.arraycopy(temp1, 0, results, 0, count);
        }

        // Format 2: N. text  (some models use this)
        if (bestFound < count) {
            String[] temp2 = new String[count];
            Pattern pattern2 = Pattern.compile(
                    "^(\\d+)\\.\\s+(.*?)(?=\\n\\d+\\.|$)",
                    Pattern.MULTILINE | Pattern.DOTALL
            );
            int found2 = matchBatchEntries(pattern2, cleaned, temp2, count);
            if (found2 > bestFound) {
                bestFound = found2;
                System.arraycopy(temp2, 0, results, 0, count);
            }
        }

        // Format 3: N) text
        if (bestFound < count) {
            String[] temp3 = new String[count];
            Pattern pattern3 = Pattern.compile(
                    "^(\\d+)\\)\\s*(.*?)(?=\\n\\d+\\)|$)",
                    Pattern.MULTILINE | Pattern.DOTALL
            );
            int found3 = matchBatchEntries(pattern3, cleaned, temp3, count);
            if (found3 > bestFound) {
                bestFound = found3;
                System.arraycopy(temp3, 0, results, 0, count);
            }
        }

        // Count missing translations — the caller retries them individually,
        // so slots are left null here rather than filled with originals.
        int missing = 0;
        for (int i = 0; i < count; i++) {
            if (results[i] == null || results[i].isEmpty()) {
                missing++;
            }
        }

        // Determine which format was used for logging
        String formatUsed = bestFound == 0 ? "none" : "numbered";
        batchSpan.logParseResult(formatUsed, bestFound, count);

        if (bestFound == 0) {
            batchSpan.logParseWarning(count, "No format matched");
            throw new IOException("Batch response could not be parsed: no numbered format matched (expected " + count + " items)");
        } else if (missing > 0) {
            batchSpan.logParseWarning(missing, missing + "/" + count + " translations missing, will retry individually");
            logWarn("Batch parse: " + missing + "/" + count + " translations missing, will retry individually");
        }

        return results;
    }

    /**
     * Match numbered entries in a batch response using the given pattern.
     * Pattern must have group(1) = number, group(2) = text.
     *
     * @return Number of successfully matched entries
     */
    private int matchBatchEntries(Pattern pattern, String text, String[] results, int count) {
        Matcher matcher = pattern.matcher(text);
        int found = 0;
        while (matcher.find()) {
            try {
                int index = Integer.parseInt(matcher.group(1)) - 1; // 0-based
                String value = matcher.group(2).trim();
                if (index >= 0 && index < count && !value.isEmpty()) {
                    results[index] = value;
                    found++;
                }
            } catch (NumberFormatException ignored) {
                // Skip malformed entries
            }
        }
        return found;
    }

    private String buildSystemPrompt(String sourceLanguage, String targetLanguage) {
        String sourceLangName = "auto".equals(sourceLanguage)
                ? getContext().getString("{lang_auto}")
                : getLanguageDisplayName(sourceLanguage);
        String targetLangName = getLanguageDisplayName(targetLanguage);

        StringBuilder sys = new StringBuilder();
        sys.append("You are a professional translation engine working on Android application strings. ")
                .append("Translate from ").append(sourceLangName).append(" to ").append(targetLangName).append(". ")
                .append("ABSOLUTE RULES: ")
                .append("1) Tokens like __PH0__, __PH1__ etc. are protected placeholders — keep them EXACTLY as-is (case-sensitive, including double underscores) in the translation. Do NOT translate, modify, reorder, or remove them. Their count and order must match. ")
                .append("2) Keep emojis exactly as they appear. ")
                .append("3) Return ONLY the translated text — no quotes, explanations, or commentary. ")
                .append("4) Keep the translation natural and appropriate for a mobile app UI.");
        if (!isNullOrEmpty(userContextDirective)) {
            sys.append(" Additional context: ").append(userContextDirective);
        }
        return sys.toString();
    }

    /**
     * Sends one prompt to the active provider.
     *
     * Wraps {@link #executeWithRetry} so the retry budget, the rate-limit
     * backoff and the debug spans are unchanged, and retries once against a
     * fallback model when the configured one has been retired.
     */
    private String translateVia(String prompt,
                                String sourceLanguage,
                                String targetLanguage,
                                int inputChars,
                                String preview) throws IOException {
        boolean retriedWithFallback = false;
        while (true) {
            try {
                return executeWithRetry(provider.id, provider.model, sourceLanguage,
                        targetLanguage, inputChars, preview, () -> {
                    String systemPrompt = buildSystemPrompt(sourceLanguage, targetLanguage);
                    JSONObject request = ProviderClient.buildRequest(provider, prompt, systemPrompt);
                    JSONObject response = HttpUtils.postJson(provider.url(), provider.headers(),
                            request.toString(), requestTimeout);

                    // Localise the API's own error before trying to read a
                    // translation out of a response that has none.
                    JSONObject error = ProviderClient.errorOf(response);
                    if (error != null) {
                        int code = JSONCompat.optInt(error, "code", -1);
                        String message = JSONCompat.optString(error, "message", "Unknown error");
                        throw new IOException("❌ " + formatApiError(code, message));
                    }

                    String translation = ProviderClient.parseResponse(provider, response);
                    logSuccess(provider.displayName + " response parsed, chars=" + translation.length());
                    return translation;
                });
            } catch (IOException e) {
                if (!retriedWithFallback && trySwitchFallbackModel(e)) {
                    retriedWithFallback = true;
                    continue;
                }
                throw e;
            }
        }
    }

    private String executeWithRetry(String engineName,
                                    String model,
                                    String sourceLanguage,
                                    String targetLanguage,
                                    int inputChars,
                                    String preview,
                                    TranslationCallable callable) throws IOException {
        IOException lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            TranslationDebugLogger.Span span = debugLogger != null
                    ? debugLogger.newSpan(engineName, model, sourceLanguage, targetLanguage,
                    attempt + 1, maxRetries + 1, inputChars, preview)
                    : TranslationDebugLogger.Span.disabled();
            try {
                logInfo("Attempt " + (attempt + 1) + " of " + (maxRetries + 1));
                String result = callable.call();
                span.markSuccess(result != null ? result.length() : 0);
                return result;
            } catch (IOException e) {
                lastException = e;
                logWarn("Attempt " + (attempt + 1) + " failed: " + e.getMessage());

                boolean willRetry = !(isNonRetryableError(e) || attempt == maxRetries);
                span.markFailure(e.getMessage(), willRetry);

                if (!willRetry) {
                    throw e;
                }

                try {
                    long waitMs = parseRetryAfterMs(e.getMessage());
                    if (waitMs <= 0) {
                        waitMs = (long) Math.pow(2, attempt) * 1000L;
                    }
                    // Cap the wait: servers sometimes send Retry-After values of
                    // many minutes — sleeping that long looks like a total hang.
                    waitMs = Math.min(waitMs, 60_000L);
                    logWarn("Attempt " + (attempt + 1) + " failed, retrying in "
                            + waitMs + "ms (" + e.getMessage() + ")");
                    Thread.sleep(waitMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Translation interrupted", ie);
                }
            }
        }

        throw lastException != null ? lastException : new IOException("Translation failed");
    }

    /**
     * Parse Retry-After value from error message.
     * Looks for pattern [Retry-After: N] embedded by HttpUtils.
     *
     * @param message Error message
     * @return Wait time in milliseconds, or -1 if not found
     */
    private long parseRetryAfterMs(String message) {
        if (message == null) return -1;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\[Retry-After: (\\d+)\\]")
                .matcher(message);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1)) * 1000L;
            } catch (NumberFormatException ignored) {}
        }
        return -1;
    }

    /**
     * Format API error messages
     */
    private String formatApiError(int errorCode, String message) {
        String prefix = getContext().getString("{error_api}");

        switch (errorCode) {
            case 400:
                return prefix + " (400): Invalid request - " + message;
            case 401:
            case 403:
                return prefix + " (401/403): Invalid API key or access denied";
            case 429:
                return prefix + " (429): Rate limit exceeded - Free tier: 60 req/min, 1500 req/day";
            case 500:
            case 503:
                return prefix + " (" + errorCode + "): Server error - Please retry later";
            default:
                return prefix + " (" + errorCode + "): " + message;
        }
    }

    /**
     * Check if error should not be retried
     */
    private boolean isNonRetryableError(IOException e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }

        // Don't retry authentication or invalid request errors
        return message.contains("(400)") ||
               message.contains("(401)") ||
               message.contains("(403)") ||
               message.contains("HTTP 400") ||
               message.contains("HTTP 401") ||
               message.contains("HTTP 403");
    }

    /**
     * Handle translation errors
     */
    @Override
    public boolean onError(Exception e) {
        System.err.println("Gemini Translation Error: " + e.getMessage());
        PluginContext pluginContext = getContext();
        if (pluginContext != null) {
            String message = e.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = getContext().getString("{error_api}");
            }
            pluginContext.showToast(message);
        }
        logError("onError: " + e.getMessage());

        // Return false to abort on critical errors
        if (e instanceof IOException && isNonRetryableError((IOException) e)) {
            return false;
        }

        // Continue with next translation for transient errors
        return true;
    }

    // ── Placeholder protection utilities ──────────────────────────────────────

    /**
     * Holds text with placeholders replaced by safe tokens, and the original placeholders
     * for later restoration.
     */
    private static class PlaceholderResult {
        final String tokenizedText;
        final List<String> placeholders;

        PlaceholderResult(String tokenizedText, List<String> placeholders) {
            this.tokenizedText = tokenizedText;
            this.placeholders = placeholders;
        }

        boolean hasPlaceholders() {
            return !placeholders.isEmpty();
        }
    }

    /**
     * Replace placeholders with safe tokens (__PH0__, __PH1__, ...) so the AI model
     * does not modify, reorder, or remove them during translation.
     */
    private PlaceholderResult tokenizePlaceholders(String text) {
        List<String> placeholders = new ArrayList<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        int index = 0;
        while (matcher.find()) {
            placeholders.add(matcher.group());
            matcher.appendReplacement(sb, Matcher.quoteReplacement("__PH" + index + "__"));
            index++;
        }
        matcher.appendTail(sb);
        return new PlaceholderResult(sb.toString(), placeholders);
    }

    /**
     * Restore placeholder tokens (__PH0__, __PH1__, ...) back to the original
     * placeholder strings captured during tokenization.
     * Uses case-insensitive matching to handle AI models that may alter token casing.
     */
    private String restorePlaceholders(String translatedText, List<String> placeholders) {
        String result = translatedText;
        for (int i = 0; i < placeholders.size(); i++) {
            String token = "__PH" + i + "__";
            if (result.contains(token)) {
                result = result.replace(token, placeholders.get(i));
            } else {
                // Case-insensitive fallback: AI may output __ph0__ or __Ph0__
                Pattern ciPattern = Pattern.compile(Pattern.quote(token), Pattern.CASE_INSENSITIVE);
                result = ciPattern.matcher(result).replaceAll(Matcher.quoteReplacement(placeholders.get(i)));
            }
        }
        return result;
    }

    /**
     * Validate that all original placeholders are present in the translated text.
     *
     * @param original  The original source text (with real placeholders)
     * @param translated The translated text (after placeholder restoration)
     * @return true if every placeholder from the original appears in the translation
     */
    private boolean validatePlaceholders(String original, String translated) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(original);
        // Collect unique placeholders with their expected counts
        Map<String, Integer> sourceCounts = new LinkedHashMap<>();
        while (matcher.find()) {
            String ph = matcher.group();
            sourceCounts.merge(ph, 1, Integer::sum);
        }
        if (sourceCounts.isEmpty()) return true;

        for (Map.Entry<String, Integer> entry : sourceCounts.entrySet()) {
            String ph = entry.getKey();
            int expectedCount = entry.getValue();
            int actualCount = countOccurrences(translated, ph);
            if (actualCount < expectedCount) {
                logWarn("Missing placeholder '" + ph + "': expected " + expectedCount + ", found " + actualCount);
                return false;
            }
            if (actualCount > expectedCount) {
                logWarn("Extra placeholder '" + ph + "': expected " + expectedCount + ", found " + actualCount);
                return false;
            }
        }
        return true;
    }

    /** Count non-overlapping occurrences of a substring */
    private int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    /**
     * Check if a string contains only symbols, numbers, and whitespace,
     * meaning it does not need translation.
     */
    private boolean isNonTranslatable(String text) {
        if (text == null || text.isEmpty()) return true;
        return NON_TRANSLATABLE_PATTERN.matcher(text).matches();
    }

    /**
     * Escape text for safe embedding in the [N] batch prompt format.
     * Replaces literal newlines with a single space to prevent breaking
     * the numbered-line structure that the AI model expects.
     */
    private String escapeForBatchPrompt(String text) {
        if (text == null) return "";
        return text.replace("\r\n", " ").replace("\n", " ").replace("\r", " ");
    }

    @FunctionalInterface
    private interface TranslationCallable {
        String call() throws IOException;
    }

    private void notifyAndFallbackToGemini(SharedPreferences prefs, String messageKey) {
        PluginContext pluginContext = getContext();
        if (pluginContext != null) {
            StringBuilder toast = new StringBuilder();
            if (messageKey != null) {
                toast.append(getContext().getString(messageKey)).append(" ");
            }
            toast.append(getContext().getString("{msg_fallback_gemini}"));
            pluginContext.showToast(toast.toString().trim());
        }
        logWarn("Falling back to Gemini due to " + messageKey);

        provider = Providers.gemini(prefs);
        selectedEngine = provider.id;
        modelName = provider.model;
        loadGeminiConfig(prefs);
    }

    /**
     * Localised "no key" message for a provider, falling back to the generic
     * one so a provider added later never shows a raw placeholder.
     */
    private String keyMissingMessageKey(String providerId) {
        switch (providerId) {
            case Providers.ID_OPENAI:     return "error_openai_no_api_key";
            case Providers.ID_CLAUDE:     return "error_claude_no_api_key";
            case Providers.ID_OPENROUTER: return "error_openrouter_no_api_key";
            default:                      return "error_no_api_key";
        }
    }

    private void loadGeminiConfig(SharedPreferences prefs) {
        apiKey = trimKey(prefs.getString(GeminiConstants.PREF_API_KEY, ""));
        if (isNullOrEmpty(apiKey)) {
            PluginContext pluginContext = getContext();
            if (pluginContext != null) {
                pluginContext.showToast(getContext().getString("{error_no_api_key}"));
            }
            throw new RuntimeException(
                getContext().getString("{error_no_api_key}")
            );
        }
    }

    private boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimKey(String key) {
        return key == null ? "" : key.trim();
    }

    /**
     * Claude retires model aliases without notice; the API answers
     * {@code not_found_error}. Retry once against the best model the account
     * can actually reach. Other providers have no equivalent, so they opt out.
     */
    private boolean trySwitchFallbackModel(IOException e) {
        if (!Providers.ID_CLAUDE.equals(provider.id)) {
            return false;
        }
        String message = e.getMessage();
        if (message == null || !message.contains("not_found_error")) {
            return false;
        }

        String autoModel = fetchAvailableClaudeModel();
        if (autoModel == null) {
            autoModel = GeminiConstants.CLAUDE_MODEL_FALLBACK;
        }

        if (autoModel.equals(provider.model)) {
            return false;
        }

        provider = provider.withModel(autoModel);
        modelName = provider.model;
        if (preferences != null) {
            preferences.edit().putString(GeminiConstants.PREF_CLAUDE_MODEL, autoModel).apply();
        }
        PluginContext pluginContext = getContext();
        if (pluginContext != null) {
            pluginContext.showToast(getContext().getString("{msg_claude_model_auto_selected}") + " " + autoModel);
        }
        logWarn("Claude model unavailable; auto-selected " + autoModel);
        return true;
    }

    private String fetchAvailableClaudeModel() {
        try {
            List<ModelCatalogManager.ModelInfo> models = ModelCatalogManager.fetchClaudeModels(provider.apiKey);
            return ModelCatalogManager.selectBestModel(models, GeminiConstants.CLAUDE_MODEL_FALLBACK);
        } catch (IOException ex) {
            logWarn("Unable to fetch Claude models: " + ex.getMessage());
            return null;
        }
    }

    private int readIntPreference(SharedPreferences prefs, String key, int defaultValue) {
        try {
            return prefs.getInt(key, defaultValue);
        } catch (ClassCastException ignored) {
            String value = prefs.getString(key, null);
            if (!isNullOrEmpty(value)) {
                try {
                    return Integer.parseInt(value.trim());
                } catch (NumberFormatException e) {
                    logWarn("Failed to parse int preference " + key + ": " + value);
                }
            }
        }
        return defaultValue;
    }

    private String buildUserContextDirective(SharedPreferences prefs) {
        String appName = prefs.getString(GeminiConstants.PREF_CONTEXT_APP_NAME, "");
        // PREF_CONTEXT_APP_TYPE is deprecated — merged into APP_NAME as "App Description"
        String appType = prefs.getString(GeminiConstants.PREF_CONTEXT_APP_TYPE, "");
        String audience = prefs.getString(GeminiConstants.PREF_CONTEXT_AUDIENCE, "");
        String tone = prefs.getString(GeminiConstants.PREF_CONTEXT_TONE, GeminiConstants.DEFAULT_CONTEXT_TONE);
        String notes = prefs.getString(GeminiConstants.PREF_CONTEXT_NOTES, "");

        StringBuilder sb = new StringBuilder();
        if (!isNullOrEmpty(appName)) {
            sb.append("App: ").append(appName).append(". ");
        }
        if (!isNullOrEmpty(appType)) {
            // Legacy: still read if user has old data
            sb.append("Type: ").append(appType).append(". ");
        }
        if (!isNullOrEmpty(audience)) {
            sb.append("Audience: ").append(audience).append(". ");
        }
        if (!isNullOrEmpty(tone)) {
            sb.append("Tone: ").append(tone).append(". ");
        }
        if (!isNullOrEmpty(notes)) {
            sb.append("Notes: ").append(notes).append(' ');
        }
        return sb.toString().trim();
    }

    private void logInfo(String message) {
        logDebug("ℹ️", message);
    }

    private void logSuccess(String message) {
        logDebug("✅", message);
    }

    private void logWarn(String message) {
        // Warnings always reach the MT log — skipped/retried strings must be
        // diagnosable even when verbose debug logging is off.
        logDebug("⚠️", message, true);
    }

    private void logError(String message) {
        logDebug("❌", message, true);
    }

    private void logError(String message, Throwable error) {
        logDebug("❌", message, true);
        if (error != null && debugLogger != null && debugLogger.isEnabled()) {
            debugLogger.logLine("  ", error.toString());
        }
    }

    private void logDebug(String emoji, String message) {
        logDebug(emoji, message, false);
    }

    private void logDebug(String emoji, String message, boolean always) {
        if (message == null || (!debugLogging && !always)) {
            return;
        }
        if (debugLogger != null && debugLogger.isEnabled()) {
            debugLogger.logLine(emoji, message);
            return;
        }
        String entry = (emoji != null ? emoji + " " : "") + "[TranslateKit] " + message;
        PluginContext pluginContext = getContext();
        if (pluginContext != null) {
            pluginContext.log(entry);
        } else {
            System.out.println(entry);
        }
    }

    /**
     * Simple batching strategy that limits batch by count and total text length.
     */
    private static class SimpleBatchingStrategy implements BatchTranslationEngine.BatchingStrategy {
        private final int maxCount;
        private final int maxTextLength;
        private int count;
        private int totalTextLength;

        SimpleBatchingStrategy(int maxCount, int maxTextLength) {
            this.maxCount = maxCount;
            this.maxTextLength = maxTextLength;
        }

        @Override
        public void reset() {
            count = 0;
            totalTextLength = 0;
        }

        @Override
        public boolean tryAdd(String text) {
            if (maxCount > 0 && count >= maxCount) return false;
            int len = text.length();
            if (maxTextLength > 0 && totalTextLength + len > maxTextLength) return false;
            count++;
            totalTextLength += len;
            return true;
        }
    }
}
