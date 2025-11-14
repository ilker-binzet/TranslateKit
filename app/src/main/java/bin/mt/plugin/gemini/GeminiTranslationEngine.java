package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import bin.mt.plugin.api.LocalString;
import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.translation.BaseTranslationEngine;

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
public class GeminiTranslationEngine extends BaseTranslationEngine {

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
        return localString != null ? localString.get("plugin_name") : "AI Translation Hub";
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
                openAiApiKey = prefs.getString(GeminiConstants.PREF_OPENAI_API_KEY, "");
                openAiModel = prefs.getString(GeminiConstants.PREF_OPENAI_MODEL, GeminiConstants.DEFAULT_OPENAI_MODEL);
                openAiEndpoint = prefs.getString(GeminiConstants.PREF_OPENAI_ENDPOINT, GeminiConstants.DEFAULT_OPENAI_ENDPOINT);
                if (isNullOrEmpty(openAiApiKey)) {
                    notifyAndFallbackToGemini(prefs, "error_openai_no_api_key");
                } else {
                    logInfo("Using OpenAI engine (model=" + openAiModel + ")");
                }
                break;
            case GeminiConstants.ENGINE_CLAUDE:
                claudeApiKey = prefs.getString(GeminiConstants.PREF_CLAUDE_API_KEY, "");
                claudeModel = prefs.getString(GeminiConstants.PREF_CLAUDE_MODEL, GeminiConstants.DEFAULT_CLAUDE_MODEL);
                claudeEndpoint = prefs.getString(GeminiConstants.PREF_CLAUDE_ENDPOINT, GeminiConstants.DEFAULT_CLAUDE_ENDPOINT);
                if (isNullOrEmpty(claudeApiKey)) {
                    notifyAndFallbackToGemini(prefs, "error_claude_no_api_key");
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
     * Translate text using Gemini API
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

        // Build translation prompt
        String prompt = buildTranslationPrompt(text, sourceLanguage, targetLanguage);
        int inputChars = text.length();
        String preview = TranslationDebugLogger.sanitizePreview(text);
        logInfo("Translate request via " + selectedEngine + " | src=" + sourceLanguage + " -> "
                + targetLanguage + " | chars=" + text.length());

        switch (selectedEngine) {
            case GeminiConstants.ENGINE_OPENAI:
                return translateWithOpenAI(prompt, sourceLanguage, targetLanguage, inputChars, preview);
            case GeminiConstants.ENGINE_CLAUDE:
                return translateWithClaudeWithFallback(prompt, sourceLanguage, targetLanguage, inputChars, preview);
            case GeminiConstants.ENGINE_GEMINI:
            default:
                return translateWithGemini(prompt, sourceLanguage, targetLanguage, inputChars, preview);
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
        prompt.append("Keep emojis exactly as they appear. Preserve placeholders/tokens (e.g., %1$s, %d, {0}, {name}, {{value}}, <b>, $PATH, `code`) verbatim and in their original order. Translate only the human-readable words around them.\n");
        prompt.append("Do not translate or reorder placeholders, tags, or markup. Do not add quotes, prefixes, or suffixes. Just the pure translation.\n\n");
        prompt.append("Text to translate:\n");
        prompt.append(text);

        return prompt.toString();
    }

    private String buildSystemPrompt(String sourceLanguage, String targetLanguage) {
        String sourceLangName = "auto".equals(sourceLanguage)
                ? (localString != null ? localString.get("lang_auto") : "Auto Detect")
                : getLanguageDisplayName(sourceLanguage);
        String targetLangName = getLanguageDisplayName(targetLanguage);

        StringBuilder sys = new StringBuilder();
        sys.append("You are a professional translation engine working on Android application strings. ")
                .append("Translate from ").append(sourceLangName).append(" to ").append(targetLangName)
                .append(". Preserve placeholders/tokens (printf, ICU, HTML tags, {{templating}}, $VARS, code fences, etc.) and emojis exactly as-is, keep the sentence natural for app UI, and return only the translated text without quotes or commentary.");
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
        apiKey = prefs.getString(GeminiConstants.PREF_API_KEY, "");
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
        String appType = prefs.getString(GeminiConstants.PREF_CONTEXT_APP_TYPE, "");
        String audience = prefs.getString(GeminiConstants.PREF_CONTEXT_AUDIENCE, "");
        String tone = prefs.getString(GeminiConstants.PREF_CONTEXT_TONE, GeminiConstants.DEFAULT_CONTEXT_TONE);
        String notes = prefs.getString(GeminiConstants.PREF_CONTEXT_NOTES, "");

        StringBuilder sb = new StringBuilder();
        if (!isNullOrEmpty(appName)) {
            sb.append("App Name: ").append(appName).append(". ");
        }
        if (!isNullOrEmpty(appType)) {
            sb.append("App Type: ").append(appType).append(". ");
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
        String entry = (emoji != null ? emoji + " " : "") + "[AI Translation Hub] " + message;
        PluginContext pluginContext = getContext();
        if (pluginContext != null) {
            pluginContext.log(entry);
        } else {
            System.out.println(entry);
        }
    }
}
