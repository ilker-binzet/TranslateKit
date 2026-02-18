package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import bin.mt.plugin.api.LocalString;
import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.translation.BaseBatchTranslationEngine;
import bin.mt.plugin.api.translation.BatchTranslationEngine;

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
     * HTML tags, and shell/template variables ($PATH, ${var}).
     */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
        "(%(?:\\d+\\$)?[-+# 0,(]*\\d*\\.?\\d*[sdfiboxXeEgGcChHnAt%])" +
        "|(\\{\\{[^}]*\\}\\})" +
        "|(\\{[^}]*\\})" +
        "|(<[^>]+>)" +
        "|(\\$\\{[^}]+\\})" +
        "|(\\$[A-Za-z_]\\w*)"
    );

    /** Pattern for non-translatable strings (only symbols, numbers, whitespace) */
    private static final Pattern NON_TRANSLATABLE_PATTERN = Pattern.compile(
        "^[\\p{Punct}\\p{Symbol}\\d\\s]*$"
    );

    private LocalString localString;
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

    /**
     * Constructor with default configuration
     */
    public GeminiTranslationEngine() {
        super(new ConfigurationBuilder()
                .setForceNotToSkipTranslated(false)
                .build());
    }

    /**
     * Initialize the translation engine
     */
    @Override
    protected void init() {
        localString = getContext().getAssetLocalString("GeminiTranslate");
    }

    /**
     * Get the display name of this translation engine
     */
    @NonNull
    @Override
    public String name() {
        return localString != null ? localString.get("plugin_name") : "TranslateKit";
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
            return localString != null ? localString.get("lang_auto") : "Auto Detect";
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
        modelName = prefs.getString(GeminiConstants.PREF_MODEL_NAME, GeminiConstants.DEFAULT_MODEL);
        selectedEngine = prefs.getString(GeminiConstants.PREF_DEFAULT_ENGINE, GeminiConstants.DEFAULT_ENGINE);
        debugLogging = prefs.getBoolean(GeminiConstants.PREF_ENABLE_DEBUG, GeminiConstants.DEFAULT_ENABLE_DEBUG);
        debugLogger = new TranslationDebugLogger(getContext(), debugLogging);
        if (selectedEngine == null) {
            selectedEngine = GeminiConstants.DEFAULT_ENGINE;
        }

        switch (selectedEngine) {
            case GeminiConstants.ENGINE_OPENAI:
                openAiApiKey = trimKey(prefs.getString(GeminiConstants.PREF_OPENAI_API_KEY, ""));
                openAiModel = prefs.getString(GeminiConstants.PREF_OPENAI_MODEL, GeminiConstants.DEFAULT_OPENAI_MODEL);
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
                claudeModel = prefs.getString(GeminiConstants.PREF_CLAUDE_MODEL, GeminiConstants.DEFAULT_CLAUDE_MODEL);
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
    }

    /**
     * Configure batch size limits for the translation engine.
     * Controls how many texts are grouped per API call.
     *
     * @return BatchingStrategy with maxCount=25 items and maxDataSize=10000 chars
     */
    @Override
    public BatchTranslationEngine.BatchingStrategy createBatchingStrategy() {
        return new BatchTranslationEngine.DefaultBatchingStrategy(25, 10000);
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
    @NonNull
    @Override
    public String translate(String text, String sourceLanguage, String targetLanguage) throws IOException {
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
        if (texts.length == 0) return new String[0];

        // Single text optimization: use direct prompt (more precise, no parsing overhead)
        if (texts.length == 1) {
            return new String[]{ translate(texts[0], sourceLanguage, targetLanguage) };
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
        for (int j = 0; j < translatableIndices.size(); j++) {
            int idx = translatableIndices.get(j);
            tokenizedTexts[j] = phResults[idx].tokenizedText;
        }

        try {
            // Build batch prompt with tokenized texts
            String prompt = buildBatchTranslationPrompt(tokenizedTexts, sourceLanguage, targetLanguage);
            int totalChars = 0;
            for (String t : tokenizedTexts) {
                if (t != null) totalChars += t.length();
            }
            String preview = "[batch:" + tokenizedTexts.length + "] " + totalChars + " chars";

            logInfo("Batch translate via " + selectedEngine + " | count=" + tokenizedTexts.length
                    + " | src=" + sourceLanguage + " -> " + targetLanguage
                    + " | totalChars=" + totalChars);

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

            String[] batchResults = parseBatchResponse(rawResponse, tokenizedTexts);

            // Map batch results back to original indices and restore placeholders
            for (int j = 0; j < translatableIndices.size(); j++) {
                int idx = translatableIndices.get(j);
                String translated = batchResults[j];

                // Restore placeholders
                if (phResults[idx].hasPlaceholders()) {
                    translated = restorePlaceholders(translated, phResults[idx].placeholders);
                    if (!validatePlaceholders(texts[idx], translated)) {
                        logWarn("Placeholder validation failed for batch item " + (j + 1) + ", keeping original");
                        translated = texts[idx];
                    }
                }

                results[idx] = translated;
            }

            logSuccess("Batch translate complete: " + texts.length + " texts in single API call");
            return results;

        } catch (IOException e) {
            // Batch failed entirely — fall back to translating each text individually
            logWarn("Batch translation failed (" + e.getMessage() + "), falling back to individual translation");

            for (int idx : translatableIndices) {
                try {
                    results[idx] = translate(texts[idx], sourceLanguage, targetLanguage);
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
        prompt.append("Tokens like __PH0__, __PH1__ etc. are protected placeholders — keep them EXACTLY as-is, do not translate, modify, reorder, or remove them.\n");
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
        prompt.append("- Tokens like __PH0__, __PH1__ etc. are protected placeholders — keep them EXACTLY as-is.\n");
        prompt.append("- Do NOT translate, modify, reorder, or remove __PH*__ tokens.\n");
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
     * Falls back to original text for any missing translation.
     *
     * @param response Raw AI response
     * @param originalTexts Original texts for fallback
     * @return Array of translated texts in the same order
     * @throws IOException If response is completely unparseable
     */
    private String[] parseBatchResponse(String response, String[] originalTexts) throws IOException {
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

        // Fill missing translations with originals
        int missing = 0;
        for (int i = 0; i < count; i++) {
            if (results[i] == null || results[i].isEmpty()) {
                results[i] = originalTexts[i];
                missing++;
            }
        }

        if (bestFound == 0) {
            logWarn("Batch response could not be parsed, falling back to originals");
        } else if (missing > 0) {
            logWarn("Batch parse: " + missing + "/" + count + " translations missing, kept originals");
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
                ? (localString != null ? localString.get("lang_auto") : "Auto Detect")
                : getLanguageDisplayName(sourceLanguage);
        String targetLangName = getLanguageDisplayName(targetLanguage);

        StringBuilder sys = new StringBuilder();
        sys.append("You are a professional translation engine working on Android application strings. ")
                .append("Translate from ").append(sourceLangName).append(" to ").append(targetLangName).append(". ")
                .append("ABSOLUTE RULES: ")
                .append("1) Tokens like __PH0__, __PH1__ etc. are protected placeholders — keep them EXACTLY as-is in the translation. Do NOT translate, modify, reorder, or remove them. ")
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
            messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content", buildSystemPrompt(sourceLanguage, targetLanguage)));
            messages.put(new JSONObject()
                    .put("role", "user")
                    .put("content", prompt));
            request.put("messages", messages);
            request.put("temperature", 0.1);
            request.put("max_tokens", 2048);

            return request;
        } catch (JSONException e) {
            throw new IOException("Failed to build OpenAI request: " + e.getMessage(), e);
        }
    }

    private JSONObject buildClaudeRequest(String prompt, String sourceLanguage, String targetLanguage) throws IOException {
        try {
            JSONObject request = new JSONObject();
            request.put("model", claudeModel);
            request.put("max_tokens", 1024);
            request.put("system", buildSystemPrompt(sourceLanguage, targetLanguage));

            JSONArray messages = new JSONArray();
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            JSONArray content = new JSONArray();
            JSONObject textBlock = new JSONObject();
            textBlock.put("type", "text");
            textBlock.put("text", prompt);
            content.put(textBlock);
            userMessage.put("content", content);
            messages.put(userMessage);
            request.put("messages", messages);

            return request;
        } catch (JSONException e) {
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

            GeminiHttpUtils.Request httpRequest = GeminiHttpUtils.post(apiUrl);
            httpRequest.setTimeout(requestTimeout);
            httpRequest.jsonBody(request);

            JSONObject response = httpRequest.executeToJson();
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

            GeminiHttpUtils.Request httpRequest = GeminiHttpUtils.post(openAiEndpoint);
            httpRequest.header("Authorization", "Bearer " + openAiApiKey);
            httpRequest.setTimeout(requestTimeout);
            httpRequest.jsonBody(request);

            JSONObject response = httpRequest.executeToJson();
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

            GeminiHttpUtils.Request httpRequest = GeminiHttpUtils.post(claudeEndpoint);
            httpRequest.header("x-api-key", claudeApiKey);
            httpRequest.header("anthropic-version", GeminiConstants.CLAUDE_API_VERSION);
            httpRequest.setTimeout(requestTimeout);
            httpRequest.jsonBody(request);

            JSONObject response = httpRequest.executeToJson();
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
            parts.put(part);
            content.put("parts", parts);
            contents.put(content);
            request.put("contents", contents);

            // Generation config for better translation
            JSONObject generationConfig = new JSONObject();
            generationConfig.put("temperature", 0.1); // Low temperature for consistent translation
            generationConfig.put("maxOutputTokens", 2048); // Allow longer responses
            generationConfig.put("topP", 0.8);
            generationConfig.put("topK", 10);
            request.put("generationConfig", generationConfig);

            return request;
        } catch (JSONException e) {
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
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                if (item instanceof JSONObject) {
                    JSONObject obj = (JSONObject) item;
                    if (obj.has("text")) {
                        builder.append(obj.optString("text"));
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
                    Thread.sleep((long) Math.pow(2, attempt) * 1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Translation interrupted", ie);
                }
            }
        }

        throw lastException != null ? lastException : new IOException("Translation failed");
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
            if (json.has("error")) {
                JSONObject error = json.getJSONObject("error");
                int code = error.optInt("code", -1);
                String message = error.optString("message", "Unknown error");
                throw new IOException("❌ " + formatApiError(code, message));
            }

            // Extract translation
            JSONArray candidates = json.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                throw new IOException("⚠️ No translation returned from API");
            }

            JSONObject candidate = candidates.getJSONObject(0);
            JSONObject content = candidate.getJSONObject("content");
            JSONArray parts = content.getJSONArray("parts");

            if (parts.length() == 0) {
                throw new IOException("⚠️ Empty translation response");
            }

            String translation = parts.getJSONObject(0).getString("text");

            translation = translation.trim();
            if (translation.startsWith("\"") && translation.endsWith("\"")) {
                translation = translation.substring(1, translation.length() - 1);
            }

            return translation;

        } catch (JSONException e) {
            throw new IOException("❌ Failed to parse API response: " + e.getMessage(), e);
        }
    }

    private String parseOpenAiResponse(JSONObject response) throws IOException {
        try {
            JSONArray choices = response.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                throw new IOException("⚠️ OpenAI response did not include choices");
            }

            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            if (message == null) {
                throw new IOException("⚠️ OpenAI response missing message payload");
            }

            String translation = extractContentText(message.opt("content"));
            if (translation.isEmpty()) {
                throw new IOException("⚠️ OpenAI response was empty");
            }
            return translation;
        } catch (JSONException e) {
            throw new IOException("❌ Failed to parse OpenAI response: " + e.getMessage(), e);
        }
    }

    private String parseClaudeResponse(JSONObject response) throws IOException {
        JSONArray contentArray = response.optJSONArray("content");
        if (contentArray == null || contentArray.length() == 0) {
            throw new IOException("⚠️ Claude response did not include content");
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < contentArray.length(); i++) {
            JSONObject block = contentArray.optJSONObject(i);
            if (block != null && block.has("text")) {
                builder.append(block.optString("text"));
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
        String prefix = localString != null ? localString.get("error_api") : "API Error";

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
                message = localString != null ? localString.get("error_api") : "Translation error";
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
     */
    private String restorePlaceholders(String translatedText, List<String> placeholders) {
        String result = translatedText;
        for (int i = 0; i < placeholders.size(); i++) {
            String token = "__PH" + i + "__";
            if (result.contains(token)) {
                result = result.replace(token, placeholders.get(i));
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
        if (pluginContext != null && localString != null) {
            StringBuilder toast = new StringBuilder();
            if (messageKey != null) {
                toast.append(localString.get(messageKey)).append(" ");
            }
            toast.append(localString.get("msg_fallback_gemini"));
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
            if (pluginContext != null && localString != null) {
                pluginContext.showToast(localString.get("error_no_api_key"));
            }
            throw new RuntimeException(
                localString != null ? localString.get("error_no_api_key") : "API key not configured"
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
        if (pluginContext != null && localString != null) {
            pluginContext.showToast(localString.get("msg_claude_model_auto_selected") + " " + claudeModel);
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
        logDebug("⚠️", message);
    }

    private void logError(String message) {
        logDebug("❌", message);
    }

    private void logDebug(String emoji, String message) {
        if (!debugLogging || message == null) {
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
}
