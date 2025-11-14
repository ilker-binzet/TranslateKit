package bin.mt.plugin.google;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import bin.mt.plugin.api.LocalString;
import bin.mt.plugin.api.translation.BaseTranslationEngine;

/**
 * Google Cloud Translation API Engine for MT Manager
 *
 * This plugin provides translation capabilities using Google Cloud Translation API v2 (Basic).
 * Supports 100+ languages with auto-detection capability.
 *
 * @author MT Manager Plugin Developer
 * @version 1.0.0
 *
 * API Documentation: https://cloud.google.com/translate/docs/reference/rest/v2/translate
 */
public class GoogleCloudTranslationEngine extends BaseTranslationEngine {

    // ISO 639-1 language codes supported by Google Cloud Translation API
    // Reference: https://cloud.google.com/translate/docs/languages
    private static final List<String> SOURCE_LANGUAGES = Arrays.asList(
        "auto", // Auto-detection
        "af", "sq", "am", "ar", "hy", "az", "eu", "be", "bn", "bs", "bg", "ca",
        "ceb", "zh-CN", "zh-TW", "co", "hr", "cs", "da", "nl", "en", "eo", "et",
        "fi", "fr", "fy", "gl", "ka", "de", "el", "gu", "ht", "ha", "haw", "he",
        "hi", "hmn", "hu", "is", "ig", "id", "ga", "it", "ja", "jv", "kn", "kk",
        "km", "rw", "ko", "ku", "ky", "lo", "la", "lv", "lt", "lb", "mk", "mg",
        "ms", "ml", "mt", "mi", "mr", "mn", "my", "ne", "no", "ny", "or", "ps",
        "fa", "pl", "pt", "pa", "ro", "ru", "sm", "gd", "sr", "st", "sn", "sd",
        "si", "sk", "sl", "so", "es", "su", "sw", "sv", "tl", "tg", "ta", "tt",
        "te", "th", "tr", "tk", "uk", "ur", "ug", "uz", "vi", "cy", "xh", "yi",
        "yo", "zu"
    );

    private static final List<String> TARGET_LANGUAGES = Arrays.asList(
        "af", "sq", "am", "ar", "hy", "az", "eu", "be", "bn", "bs", "bg", "ca",
        "ceb", "zh-CN", "zh-TW", "co", "hr", "cs", "da", "nl", "en", "eo", "et",
        "fi", "fr", "fy", "gl", "ka", "de", "el", "gu", "ht", "ha", "haw", "he",
        "hi", "hmn", "hu", "is", "ig", "id", "ga", "it", "ja", "jv", "kn", "kk",
        "km", "rw", "ko", "ku", "ky", "lo", "la", "lv", "lt", "lb", "mk", "mg",
        "ms", "ml", "mt", "mi", "mr", "mn", "my", "ne", "no", "ny", "or", "ps",
        "fa", "pl", "pt", "pa", "ro", "ru", "sm", "gd", "sr", "st", "sn", "sd",
        "si", "sk", "sl", "so", "es", "su", "sw", "sv", "tl", "tg", "ta", "tt",
        "te", "th", "tr", "tk", "uk", "ur", "ug", "uz", "vi", "cy", "xh", "yi",
        "yo", "zu"
    );

    private LocalString localString;
    private String apiKey;
    private int maxRetries;
    private int requestTimeout;
    private boolean useAdvancedModel;

    /**
     * Constructor with default configuration
     * Sets force translation mode to ensure all texts are processed
     */
    public GoogleCloudTranslationEngine() {
        super(new ConfigurationBuilder()
                .setForceNotToSkipTranslated(false) // Allow skipping already translated entries
                .build());
    }

    /**
     * Initialize the translation engine
     * Loads localized strings and validates configuration
     */
    @Override
    protected void init() {
        localString = getContext().getAssetLocalString("GoogleTranslate");
    }

    /**
     * Get the display name of this translation engine
     *
     * @return Localized engine name
     */
    @NonNull
    @Override
    public String name() {
        return localString != null ? localString.get("plugin_name") : "Google Cloud Translate";
    }

    /**
     * Load source languages including auto-detection
     *
     * @return List of source language codes
     */
    @NonNull
    @Override
    public List<String> loadSourceLanguages() {
        return new ArrayList<>(SOURCE_LANGUAGES);
    }

    /**
     * Load target languages (auto-detection not available for target)
     *
     * @param sourceLanguage The selected source language code
     * @return List of target language codes
     */
    @NonNull
    @Override
    public List<String> loadTargetLanguages(String sourceLanguage) {
        return new ArrayList<>(TARGET_LANGUAGES);
    }

    /**
     * Convert language code to display name
     * Uses the parent class's built-in language name mapping for ISO 639-1 codes
     *
     * @param language ISO 639-1 language code
     * @return Localized language display name
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
     * Loads user configuration from SharedPreferences
     */
    @Override
    public void onStart() {
        SharedPreferences prefs = getContext().getPreferences();

        // Load API key
        apiKey = prefs.getString(GoogleConstants.PREF_API_KEY, "");

        // Load advanced settings
        maxRetries = prefs.getInt(GoogleConstants.PREF_MAX_RETRIES, GoogleConstants.DEFAULT_MAX_RETRIES);
        requestTimeout = prefs.getInt(GoogleConstants.PREF_TIMEOUT, GoogleConstants.DEFAULT_TIMEOUT);
        useAdvancedModel = prefs.getBoolean(GoogleConstants.PREF_USE_ADVANCED_MODEL, false);

        // Validate API key
        if (apiKey.isEmpty()) {
            throw new RuntimeException(
                localString != null ? localString.get("error_no_api_key") : "API key not configured"
            );
        }
    }

    /**
     * Translate text using Google Cloud Translation API v2
     *
     * This method implements the core translation functionality using Google's REST API.
     * It handles API requests, response parsing, and error management.
     *
     * @param text The text to translate (max ~5000 characters recommended)
     * @param sourceLanguage Source language code (use "auto" for auto-detection)
     * @param targetLanguage Target language code
     * @return Translated text
     * @throws IOException If network error, API error, or invalid response occurs
     */
    @NonNull
    @Override
    public String translate(String text, String sourceLanguage, String targetLanguage) throws IOException {
        // Input validation
        if (text == null || text.trim().isEmpty()) {
            return text;
        }

        // Check character limit (Google recommends max 5000 chars per request)
        if (text.length() > 5000) {
            throw new IOException(
                localString != null ? localString.get("error_text_too_long") : "Text exceeds 5000 character limit"
            );
        }

        // Build API request URL
        String apiUrl = buildApiUrl(text, sourceLanguage, targetLanguage);

        // Perform translation with retry logic
        return performTranslationWithRetry(apiUrl, text);
    }

    /**
     * Build the Google Cloud Translation API v2 URL
     *
     * @param text Text to translate
     * @param sourceLanguage Source language code
     * @param targetLanguage Target language code
     * @return Complete API URL with query parameters
     * @throws IOException If URL encoding fails
     */
    private String buildApiUrl(String text, String sourceLanguage, String targetLanguage) throws IOException {
        try {
            StringBuilder url = new StringBuilder(GoogleConstants.API_BASE_URL);
            url.append("?key=").append(URLEncoder.encode(apiKey, "UTF-8"));
            url.append("&q=").append(URLEncoder.encode(text, "UTF-8"));
            url.append("&target=").append(URLEncoder.encode(targetLanguage, "UTF-8"));

            // Add source language if not auto-detect
            if (!"auto".equals(sourceLanguage)) {
                url.append("&source=").append(URLEncoder.encode(sourceLanguage, "UTF-8"));
            }

            // Set format (text or html)
            url.append("&format=text");

            // Use advanced NMT model if enabled
            if (useAdvancedModel) {
                url.append("&model=nmt");
            }

            return url.toString();
        } catch (Exception e) {
            throw new IOException("Failed to build API URL: " + e.getMessage(), e);
        }
    }

    /**
     * Perform translation with automatic retry on transient failures
     *
     * @param apiUrl Complete API URL
     * @param originalText Original text for error messages
     * @return Translated text
     * @throws IOException If all retry attempts fail
     */
    private String performTranslationWithRetry(String apiUrl, String originalText) throws IOException {
        IOException lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                // Perform HTTP GET request
                HttpUtils.Request request = HttpUtils.get(apiUrl);
                request.setTimeout(requestTimeout);

                String responseBody = request.execute();

                // Parse and return result
                return parseTranslationResponse(responseBody);

            } catch (IOException e) {
                lastException = e;

                // Don't retry on authentication errors or invalid requests
                if (isNonRetryableError(e)) {
                    throw e;
                }

                // Wait before retry (exponential backoff)
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep((long) Math.pow(2, attempt) * 1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Translation interrupted", ie);
                    }
                }
            }
        }

        // All retries failed
        throw new IOException(
            String.format("Translation failed after %d attempts: %s",
                maxRetries + 1,
                lastException != null ? lastException.getMessage() : "Unknown error"
            ),
            lastException
        );
    }

    /**
     * Parse Google Cloud Translation API JSON response
     *
     * Response format:
     * {
     *   "data": {
     *     "translations": [
     *       {
     *         "translatedText": "Translated text here",
     *         "detectedSourceLanguage": "en" (optional)
     *       }
     *     ]
     *   }
     * }
     *
     * @param responseBody Raw JSON response from API
     * @return Translated text
     * @throws IOException If parsing fails or API returns error
     */
    private String parseTranslationResponse(String responseBody) throws IOException {
        try {
            JSONObject json = new JSONObject(responseBody);

            // Check for API error
            if (json.has("error")) {
                JSONObject error = json.getJSONObject("error");
                int code = error.optInt("code", -1);
                String message = error.optString("message", "Unknown error");

                throw new IOException(formatApiError(code, message));
            }

            // Extract translation
            JSONObject data = json.getJSONObject("data");
            JSONArray translations = data.getJSONArray("translations");

            if (translations.length() == 0) {
                throw new IOException("No translation returned from API");
            }

            JSONObject translation = translations.getJSONObject(0);
            return translation.getString("translatedText");

        } catch (JSONException e) {
            throw new IOException("Failed to parse API response: " + e.getMessage(), e);
        }
    }

    /**
     * Format API error messages for user display
     *
     * @param errorCode HTTP or API error code
     * @param message Error message from API
     * @return Formatted, user-friendly error message
     */
    private String formatApiError(int errorCode, String message) {
        String prefix = localString != null ? localString.get("error_api") : "API Error";

        switch (errorCode) {
            case 400:
                return prefix + " (400): Invalid request - " + message;
            case 401:
                return prefix + " (401): Invalid API key";
            case 403:
                return prefix + " (403): API access forbidden - Check billing and API key permissions";
            case 429:
                return prefix + " (429): Rate limit exceeded - Please wait and try again";
            case 500:
            case 502:
            case 503:
                return prefix + " (" + errorCode + "): Server error - Please retry later";
            default:
                return prefix + " (" + errorCode + "): " + message;
        }
    }

    /**
     * Check if an error should not be retried
     *
     * @param e Exception to check
     * @return true if error is permanent and should not be retried
     */
    private boolean isNonRetryableError(IOException e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }

        // Don't retry authentication errors, invalid requests, or quota errors
        return message.contains("(400)") ||
               message.contains("(401)") ||
               message.contains("(403)") ||
               message.contains("(429)");
    }

    /**
     * Handle translation errors
     * Override to provide custom error handling or logging
     *
     * @param e Exception that occurred during translation
     * @return true to continue with next translation, false to abort batch
     */
    @Override
    public boolean onError(Exception e) {
        // Log error for debugging
        System.err.println("Google Cloud Translation Error: " + e.getMessage());

        // Return false to abort batch on critical errors
        if (e instanceof IOException && isNonRetryableError((IOException) e)) {
            return false;
        }

        // Continue with next translation for transient errors
        return true;
    }
}
