package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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

/**
 * Gemini API Translation Engine for MT Manager
 *
 * Uses Google's Gemini AI model (gemini-pro) for translation via generative AI prompting.
 * This is similar to using ChatGPT or Claude for translation.
 *
 * FREE TIER: 60 requests per minute, 1500 requests per day
 * API Documentation: https://ai.google.dev/gemini-api/docs
 *
 * @author MT Manager Plugin Developer
 * @version 1.0.0
 */
public class GeminiTranslationEngine extends BaseBatchTranslationEngine {

    // ISO 639-1 language codes
    private static final List<String> SOURCE_LANGUAGES = Arrays.asList(
        "auto", // Auto-detection (Gemini will detect)
        "en", "tr", "de", "fr", "es", "it", "pt", "ru", "ja", "ko", "zh-CN", "zh-TW",
        "ar", "hi", "nl", "sv", "pl", "uk", "cs", "el", "he", "id", "th", "vi",
        "ro", "hu", "da", "fi", "no", "bg", "hr", "sr", "sk", "sl", "lt", "lv", "et"
    );

    private static final List<String> TARGET_LANGUAGES = Arrays.asList(
        "en", "tr", "de", "fr", "es", "it", "pt", "ru", "ja", "ko", "zh-CN", "zh-TW",
        "ar", "hi", "nl", "sv", "pl", "uk", "cs", "el", "he", "id", "th", "vi",
        "ro", "hu", "da", "fi", "no", "bg", "hr", "sr", "sk", "sl", "lt", "lv", "et"
    );

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

    // OpenAI configuration
    private String openAiApiKey;
    private String openAiModel;
    private String openAiEndpoint;

    // Claude configuration
    private String claudeApiKey;
    private String claudeModel;
    private String claudeEndpoint;

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
     * Load source languages including auto-detection
     */
    @NonNull
    @Override
    public List<String> loadSourceLanguages() {
        return new ArrayList<>(SOURCE_LANGUAGES);
    }

    /**
     * Load target languages
     */
    @NonNull
    @Override
    public List<String> loadTargetLanguages(String sourceLanguage) {
        return new ArrayList<>(TARGET_LANGUAGES);
    }

    /**
     * Convert language code to display name
     */
    @NonNull
    @Override
    public String getLanguageDisplayName(String language) {
        if ("auto".equals(language)) {
            return getContext().getString("{lang_auto}");
        }
        return super.getLanguageDisplayName(language);
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
        // Resolve model name: custom override > saved selection > provider default.
        modelName = ProviderCatalogRefresher.resolveSelectedModel(
                prefs, GeminiConstants.PREF_MODEL_NAME, GeminiConstants.DEFAULT_MODEL);
        selectedEngine = prefs.getString(GeminiConstants.PREF_DEFAULT_ENGINE, GeminiConstants.DEFAULT_ENGINE);
        debugLogging = prefs.getBoolean(GeminiConstants.PREF_ENABLE_DEBUG, GeminiConstants.DEFAULT_ENABLE_DEBUG);
        debugLogger = new TranslationDebugLogger(getContext(), debugLogging);
        if (selectedEngine == null) {
            selectedEngine = GeminiConstants.DEFAULT_ENGINE;
        }

        switch (selectedEngine) {
            case GeminiConstants.ENGINE_OPENAI:
                openAiApiKey = trimKey(prefs.getString(GeminiConstants.PREF_OPENAI_API_KEY, ""));
                openAiModel = ProviderCatalogRefresher.resolveSelectedModel(
                        prefs, GeminiConstants.PREF_OPENAI_MODEL, GeminiConstants.DEFAULT_OPENAI_MODEL);
                openAiEndpoint = prefs.getString(GeminiConstants.PREF_OPENAI_ENDPOINT, GeminiConstants.DEFAULT_OPENAI_ENDPOINT);
                if (isNullOrEmpty(openAiApiKey)) {
                    notifyAndFallbackToGemini(prefs, "error_openai_no_api_key");
                } else if (!java.util.regex.Pattern.matches(GeminiConstants.OPENAI_API_KEY_PATTERN, openAiApiKey)) {
                    logWarn("OpenAI API key format appears invalid (expected: sk-...)");
                } else {
                    logInfo("Using OpenAI engine (model=" + openAiModel + ")");
                }
                break;
            case GeminiConstants.ENGINE_CLAUDE:
                claudeApiKey = trimKey(prefs.getString(GeminiConstants.PREF_CLAUDE_API_KEY, ""));
                claudeModel = ProviderCatalogRefresher.resolveSelectedModel(
                        prefs, GeminiConstants.PREF_CLAUDE_MODEL, GeminiConstants.DEFAULT_CLAUDE_MODEL);
                claudeEndpoint = prefs.getString(GeminiConstants.PREF_CLAUDE_ENDPOINT, GeminiConstants.DEFAULT_CLAUDE_ENDPOINT);
                if (isNullOrEmpty(claudeApiKey)) {
                    notifyAndFallbackToGemini(prefs, "error_claude_no_api_key");
                } else if (!java.util.regex.Pattern.matches(GeminiConstants.CLAUDE_API_KEY_PATTERN, claudeApiKey)) {
                    logWarn("Claude API key format appears invalid (expected: sk-ant-...)");
                } else {
                    logInfo("Using Claude engine (model=" + claudeModel + ")");
                }
                break;
            case GeminiConstants.ENGINE_GEMINI:
            default:
                selectedEngine = GeminiConstants.ENGINE_GEMINI;
                loadGeminiConfig(prefs);
                logInfo("Using Gemini engine (model=" + modelName + ")");
                break;
        }

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

        String result;
        switch (selectedEngine) {
            case GeminiConstants.ENGINE_OPENAI:
                result = translateWithOpenAI(prompt, sourceLanguage, targetLanguage, inputChars, preview);
                break;
            case GeminiConstants.ENGINE_CLAUDE:
                result = translateWithClaudeWithFallback(prompt, sourceLanguage, targetLanguage, inputChars, preview);
                break;
            case GeminiConstants.ENGINE_GEMINI:
            default:
                result = translateWithGemini(prompt, sourceLanguage, targetLanguage, inputChars, preview);
                break;
        }

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

            String rawResponse;
            switch (selectedEngine) {
                case GeminiConstants.ENGINE_OPENAI:
                    rawResponse = translateWithOpenAI(prompt, sourceLanguage, targetLanguage, totalChars, preview);
                    break;
                case GeminiConstants.ENGINE_CLAUDE:
                    rawResponse = translateWithClaudeWithFallback(prompt, sourceLanguage, targetLanguage, totalChars, preview);
                    break;
                case GeminiConstants.ENGINE_GEMINI:
                default:
                    rawResponse = translateWithGemini(prompt, sourceLanguage, targetLanguage, totalChars, preview);
                    break;
            }

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

    private JSONObject buildOpenAiRequest(String prompt, String sourceLanguage, String targetLanguage) throws IOException {
        try {
            JSONObject request = new JSONObject();
            request.put("model", openAiModel);

            JSONArray messages = new JSONArray();
            messages.add(new JSONObject()
                    .put("role", "system")
                    .put("content", buildSystemPrompt(sourceLanguage, targetLanguage)));
            messages.add(new JSONObject()
                    .put("role", "user")
                    .put("content", prompt));
            request.put("messages", messages);
            request.put("temperature", 0.1);
            // Scale with input size — a fixed 2048 truncates large batches.
            request.put("max_tokens", clampTokens(prompt.length() / 2, 2048, 8192));

            return request;
        } catch (Exception e) {
            throw new IOException("Failed to build OpenAI request: " + e.getMessage(), e);
        }
    }

    /** Clamp a token budget between min and max. */
    private static int clampTokens(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private JSONObject buildClaudeRequest(String prompt, String sourceLanguage, String targetLanguage) throws IOException {
        try {
            JSONObject request = new JSONObject();
            request.put("model", claudeModel);
            // Dynamic max_tokens: scale with prompt size (~1 token per 3-4 chars),
            // clamped so we never exceed a model's output ceiling.
            request.put("max_tokens", clampTokens(prompt.length() / 3, 2048, 8192));
            request.put("system", buildSystemPrompt(sourceLanguage, targetLanguage));

            JSONArray messages = new JSONArray();
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            JSONArray content = new JSONArray();
            JSONObject textBlock = new JSONObject();
            textBlock.put("type", "text");
            textBlock.put("text", prompt);
            content.add(textBlock);
            userMessage.put("content", content);
            messages.add(userMessage);
            request.put("messages", messages);

            return request;
        } catch (Exception e) {
            throw new IOException("Failed to build Claude request: " + e.getMessage(), e);
        }
    }

    /**
     * Perform translation with automatic retry
     */
    private String translateWithGemini(String prompt,
                                       String sourceLanguage,
                                       String targetLanguage,
                                       int inputChars,
                                       String preview) throws IOException {
        return executeWithRetry("gemini", modelName, sourceLanguage, targetLanguage, inputChars, preview, () -> {
            JSONObject request = buildGeminiRequest(prompt);

            String apiUrl = String.format("%s/%s:generateContent?key=%s",
                GeminiConstants.API_BASE_URL,
                modelName,
                apiKey
            );

            JSONObject response = HttpUtils.postJson(apiUrl, null, request.toString(), requestTimeout);
            String translation = parseGeminiResponse(response);
            logSuccess("Gemini response parsed, chars=" + translation.length());
            return translation;
        });
    }

    private String translateWithOpenAI(String prompt,
                                       String sourceLanguage,
                                       String targetLanguage,
                                       int inputChars,
                                       String preview) throws IOException {
        return executeWithRetry("openai", openAiModel, sourceLanguage, targetLanguage, inputChars, preview, () -> {
            JSONObject request = buildOpenAiRequest(prompt, sourceLanguage, targetLanguage);

            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + openAiApiKey);
            JSONObject response = HttpUtils.postJson(openAiEndpoint, headers, request.toString(), requestTimeout);
            String translation = parseOpenAiResponse(response);
            logSuccess("OpenAI response parsed, chars=" + translation.length());
            return translation;
        });
    }

    private String translateWithClaude(String prompt,
                                       String sourceLanguage,
                                       String targetLanguage,
                                       int inputChars,
                                       String preview) throws IOException {
        return executeWithRetry("claude", claudeModel, sourceLanguage, targetLanguage, inputChars, preview, () -> {
            JSONObject request = buildClaudeRequest(prompt, sourceLanguage, targetLanguage);

            Map<String, String> headers = new HashMap<>();
            headers.put("x-api-key", claudeApiKey);
            headers.put("anthropic-version", GeminiConstants.CLAUDE_API_VERSION);
            JSONObject response = HttpUtils.postJson(claudeEndpoint, headers, request.toString(), requestTimeout);
            String translation = parseClaudeResponse(response);
            logSuccess("Claude response parsed, chars=" + translation.length());
            return translation;
        });
    }

    private String translateWithClaudeWithFallback(String prompt,
                                                   String sourceLanguage,
                                                   String targetLanguage,
                                                   int inputChars,
                                                   String preview) throws IOException {
        boolean retriedWithFallback = false;
        while (true) {
            try {
                return translateWithClaude(prompt, sourceLanguage, targetLanguage, inputChars, preview);
            } catch (IOException e) {
                if (!retriedWithFallback && trySwitchClaudeFallbackModel(e)) {
                    retriedWithFallback = true;
                    continue;
                }
                throw e;
            }
        }
    }

    /**
     * Build Gemini API request JSON
     *
     * Request format:
     * {
     *   "contents": [{
     *     "parts": [{"text": "prompt here"}]
     *   }],
     *   "generationConfig": {
     *     "temperature": 0.1,
     *     "maxOutputTokens": 1024
     *   }
     * }
     */
    private JSONObject buildGeminiRequest(String prompt) throws IOException {
        try {
            JSONObject request = new JSONObject();

            // Contents array
            JSONArray contents = new JSONArray();
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            part.put("text", prompt);
            JSONCompat.put(parts, part);
            content.put("parts", parts);
            JSONCompat.put(contents, content);
            request.put("contents", contents);

            // Generation config for better translation
            JSONObject generationConfig = new JSONObject();
            generationConfig.put("temperature", 0.1); // Low temperature for consistent translation
            // Scale the output budget with the input: a fixed 2048 truncates
            // large batches mid-list (every item after the cut is silently
            // lost), and on "thinking" models (Gemini 2.5+) hidden reasoning
            // tokens ALSO count against this cap. prompt chars ≈ 3x the tokens
            // the translation needs, which leaves thinking headroom.
            generationConfig.put("maxOutputTokens", clampTokens(prompt.length(), 4096, 32768));
            generationConfig.put("topP", 0.8);
            generationConfig.put("topK", 10);
            request.put("generationConfig", generationConfig);

            return request;
        } catch (Exception e) {
            throw new IOException("Failed to build request: " + e.getMessage(), e);
        }
    }

    private String extractContentText(Object content) throws IOException {
        if (content == null) {
            return "";
        }
        if (content instanceof String) {
            return ((String) content).trim();
        }
        if (content instanceof JSONArray) {
            JSONArray array = (JSONArray) content;
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < JSONCompat.size(array); i++) {
                Object item = array.get(i);
                if (item instanceof JSONObject) {
                    JSONObject obj = (JSONObject) item;
                    String text = JSONCompat.optString(obj, "text", null);
                    if (text != null && !text.isEmpty()) {
                        builder.append(text);
                    }
                } else if (item instanceof String) {
                    builder.append((String) item);
                }
            }
            return builder.toString().trim();
        }
        return content.toString().trim();
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
     * Parse Gemini API response
     *
     * Response format:
     * {
     *   "candidates": [{
     *     "content": {
     *       "parts": [{"text": "translated text"}]
     *     }
     *   }]
     * }
     */
    private String parseGeminiResponse(JSONObject json) throws IOException {
        try {
            // Check for API error
            JSONObject error = JSONCompat.optJSONObject(json, "error");
            if (error != null) {
                int code = JSONCompat.optInt(error, "code", -1);
                String message = JSONCompat.optString(error, "message", "Unknown error");
                throw new IOException("❌ " + formatApiError(code, message));
            }

            // Extract translation
            JSONArray candidates = JSONCompat.optJSONArray(json, "candidates");
            if (candidates == null || JSONCompat.size(candidates) == 0) {
                throw new IOException("⚠️ No translation returned from API");
            }

            JSONObject candidate = JSONCompat.optJSONObject(candidates, 0);
            if (candidate == null) {
                throw new IOException("⚠️ Invalid candidate in response");
            }
            JSONObject content = candidate.getJSONObject("content");
            JSONArray parts = content.getJSONArray("parts");

            if (JSONCompat.size(parts) == 0) {
                throw new IOException("⚠️ Empty translation response");
            }

            String translation = JSONCompat.optJSONObject(parts, 0).getString("text");

            translation = translation.trim();
            if (translation.startsWith("\"") && translation.endsWith("\"")) {
                translation = translation.substring(1, translation.length() - 1);
            }

            return translation;

        } catch (Exception e) {
            logError("Failed to parse Gemini response: " + e.getMessage(), e);
            throw new IOException("Failed to parse Gemini response: " + e.getMessage(), e);
        }
    }

    private String parseOpenAiResponse(JSONObject response) throws IOException {
        try {
            JSONArray choices = JSONCompat.optJSONArray(response, "choices");
            if (choices == null || JSONCompat.size(choices) == 0) {
                throw new IOException("⚠️ OpenAI response did not include choices");
            }

            JSONObject message = JSONCompat.optJSONObject(choices, 0);
            if (message != null) {
                message = JSONCompat.optJSONObject(message, "message");
            }
            if (message == null) {
                throw new IOException("⚠️ OpenAI response missing message payload");
            }

            // OpenAI's `content` may be a plain string or an array of content parts.
            // Try string first; fall back to JSONArray.
            String translation;
            try {
                translation = extractContentText(message.getString("content"));
            } catch (Exception stringFail) {
                JSONArray contentArr = JSONCompat.optJSONArray(message, "content");
                translation = extractContentText(contentArr);
            }
            if (translation.isEmpty()) {
                throw new IOException("⚠️ OpenAI response was empty");
            }
            return translation;
        } catch (Exception e) {
            logError("Failed to parse OpenAI response: " + e.getMessage(), e);
            throw new IOException("Failed to parse OpenAI response: " + e.getMessage(), e);
        }
    }

    private String parseClaudeResponse(JSONObject response) throws IOException {
        JSONArray contentArray = JSONCompat.optJSONArray(response, "content");
        if (contentArray == null || JSONCompat.size(contentArray) == 0) {
            throw new IOException("⚠️ Claude response did not include content");
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < JSONCompat.size(contentArray); i++) {
            JSONObject block = JSONCompat.optJSONObject(contentArray, i);
            if (block != null) {
                String text = JSONCompat.optString(block, "text", "");
                if (!text.isEmpty()) {
                    builder.append(text);
                }
            }
        }

        String translation = builder.toString().trim();
        if (translation.isEmpty()) {
            throw new IOException("⚠️ Claude response was empty");
        }
        return translation;
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

        selectedEngine = GeminiConstants.ENGINE_GEMINI;
        loadGeminiConfig(prefs);
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

    private boolean trySwitchClaudeFallbackModel(IOException e) {
        if (!GeminiConstants.ENGINE_CLAUDE.equals(selectedEngine)) {
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

        if (autoModel.equals(claudeModel)) {
            return false;
        }

        claudeModel = autoModel;
        if (preferences != null) {
            preferences.edit().putString(GeminiConstants.PREF_CLAUDE_MODEL, claudeModel).apply();
        }
        PluginContext pluginContext = getContext();
        if (pluginContext != null) {
            pluginContext.showToast(getContext().getString("{msg_claude_model_auto_selected}") + " " + claudeModel);
        }
        logWarn("Claude model unavailable; auto-selected " + claudeModel);
        return true;
    }

    private String fetchAvailableClaudeModel() {
        try {
            List<ModelCatalogManager.ModelInfo> models = ModelCatalogManager.fetchClaudeModels(claudeApiKey);
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
