package bin.mt.plugin.google;

import android.content.SharedPreferences;
import android.text.InputType;

import java.util.regex.Pattern;

import bin.mt.plugin.api.LocalString;
import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.preference.PluginPreference;

/**
 * Preference screen for Google Cloud Translation plugin configuration
 *
 * This class provides a user-friendly settings interface for:
 * - API key configuration
 * - Advanced model selection
 * - Performance tuning (timeouts, retries)
 * - Feature toggles (caching, etc.)
 *
 * @author MT Manager Plugin Developer
 * @version 1.0.0
 */
public class GoogleTranslatePreference implements PluginPreference {

    private LocalString localString;
    private PluginContext context;

    /**
     * Build the preference screen UI
     *
     * This method is called by MT Manager to construct the settings interface.
     * It uses a fluent Builder pattern to add various preference items.
     *
     * @param context Plugin context for accessing resources and preferences
     * @param builder Builder for constructing the preference screen
     */
    @Override
    public void onBuild(PluginContext context, Builder builder) {
        this.context = context;
        this.localString = context.getAssetLocalString("GoogleTranslate");
        if (this.localString == null) {
            this.localString = context.getLocalString();
        }

        // Set localization support
        builder.setLocalString(localString);

        // ==================== Header Section ====================
        builder.addText("{pref_header_main}").summary("");

        builder.addText("{pref_plugin_name}")
                .summary("{pref_plugin_description}");

        // ==================== API Configuration Section ====================
        builder.addText("{pref_header_api_config}").summary("");

        // API Key input
        builder.addInput("{pref_api_key_title}", GoogleConstants.PREF_API_KEY)
                .defaultValue(GoogleConstants.DEFAULT_API_KEY)
                .summary("{pref_api_key_summary}")
                .valueAsSummary()
                .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        // Test API key button
        builder.addText("{pref_test_api_key}")
                .summary("{pref_test_api_key_summary}")
                .onClick((pluginUI, item) -> {
                    if (isApiKeyPresent()) {
                        testApiKey();
                    } else {
                        context.showToast(localString.get("error_no_api_key"));
                    }
                });

        // Link to Google Cloud Console
        builder.addText("{pref_get_api_key}")
                .summary("{pref_get_api_key_summary}")
                .url(GoogleConstants.URL_GOOGLE_CONSOLE);

        // ==================== Translation Settings Section ====================
        builder.addText("{pref_header_translation_settings}").summary("");

        // Default target language
        builder.addList("{pref_default_target_lang}", GoogleConstants.PREF_DEFAULT_TARGET_LANG)
                .summary("{pref_default_target_lang_summary}")
                .addItem("{lang_en}", "en")
                .addItem("{lang_zh-CN}", "zh-CN")
                .addItem("{lang_tr}", "tr")
                .addItem("{lang_de}", "de")
                .addItem("{lang_fr}", "fr")
                .addItem("{lang_es}", "es")
                .addItem("{lang_ja}", "ja")
                .addItem("{lang_ko}", "ko");

        // Advanced NMT model toggle
        builder.addSwitch("{pref_use_advanced_model}", GoogleConstants.PREF_USE_ADVANCED_MODEL)
                .defaultValue(false)
                .summary("{pref_use_advanced_model_summary}");

        // ==================== Performance Section ====================
        builder.addText("{pref_header_performance}").summary("");

        // Request timeout
        builder.addInput("{pref_timeout}", GoogleConstants.PREF_TIMEOUT)
                .defaultValue(String.valueOf(GoogleConstants.DEFAULT_TIMEOUT))
                .summary("{pref_timeout_summary}")
                .valueAsSummary()
                .inputType(InputType.TYPE_CLASS_NUMBER);

        // Max retry attempts
        builder.addInput("{pref_max_retries}", GoogleConstants.PREF_MAX_RETRIES)
                .defaultValue(String.valueOf(GoogleConstants.DEFAULT_MAX_RETRIES))
                .summary("{pref_max_retries_summary}")
                .valueAsSummary()
                .inputType(InputType.TYPE_CLASS_NUMBER);

        // ==================== Advanced Features Section ====================
        if (GoogleConstants.FEATURE_CACHE) {
            builder.addText("{pref_header_advanced}").summary("");

            // Enable caching
            builder.addSwitch("{pref_enable_cache}", GoogleConstants.PREF_ENABLE_CACHE)
                    .defaultValue(false)
                    .summary("{pref_enable_cache_summary}");

            // Cache expiration time
            builder.addInput("{pref_cache_expiration}", GoogleConstants.PREF_CACHE_EXPIRATION)
                    .defaultValue(String.valueOf(GoogleConstants.DEFAULT_CACHE_EXPIRATION))
                    .summary("{pref_cache_expiration_summary}")
                    .valueAsSummary()
                    .inputType(InputType.TYPE_CLASS_NUMBER);

            // Clear cache button
            builder.addText("{pref_clear_cache}")
                    .summary("{pref_clear_cache_summary}")
                    .onClick((pluginUI, item) -> clearCache());
        }

        // ==================== Statistics Section ====================
        if (GoogleConstants.FEATURE_STATISTICS) {
            builder.addText("{pref_header_statistics}").summary("");

            SharedPreferences prefs = context.getPreferences();
            int cacheHits = prefs.getInt(GoogleConstants.PREF_CACHE_HITS, 0);
            int cacheMisses = prefs.getInt(GoogleConstants.PREF_CACHE_MISSES, 0);

            builder.addText("{pref_stats_cache_hits}")
                    .summary(String.format(localString.get("pref_stats_value"), cacheHits));

            builder.addText("{pref_stats_cache_misses}")
                    .summary(String.format(localString.get("pref_stats_value"), cacheMisses));

            // Reset statistics button
            builder.addText("{pref_reset_stats}")
                    .summary("{pref_reset_stats_summary}")
                    .onClick((pluginUI, item) -> resetStatistics());
        }

        // ==================== Information Section ====================
        builder.addText("{pref_header_info}").summary("");

        // Plugin version
        builder.addText("{pref_version}")
                .summary(GoogleConstants.PLUGIN_VERSION_NAME);

        // API documentation link
        builder.addText("{pref_api_docs}")
                .summary("{pref_api_docs_summary}")
                .url(GoogleConstants.URL_API_DOCS);

        // Pricing information
        builder.addText("{pref_pricing}")
                .summary("{pref_pricing_summary}")
                .url(GoogleConstants.URL_PRICING);

        // GitHub repository
        builder.addText("{pref_github}")
                .summary("{pref_github_summary}")
                .url(GoogleConstants.URL_PLUGIN_GITHUB);
    }

    /**
     * Validate API key format
     *
     * Google Cloud API keys follow the pattern: AIzaSy[A-Za-z0-9_-]{33}
     *
     * @param apiKey API key to validate
     * @return true if format is valid
     */
    private boolean isValidApiKey(String apiKey) {
        return Pattern.matches(GoogleConstants.API_KEY_PATTERN, apiKey);
    }

    /**
     * Test API key by making a simple translation request
     *
     * @param pluginUI UI context for displaying results
     */
    private boolean isApiKeyPresent() {
        SharedPreferences prefs = context.getPreferences();
        String apiKey = prefs.getString(GoogleConstants.PREF_API_KEY, "");
        return !apiKey.isEmpty();
    }

    /**
     * Test API key by making a simple translation request
     */
    private void testApiKey() {
        SharedPreferences prefs = context.getPreferences();
        String apiKey = prefs.getString(GoogleConstants.PREF_API_KEY, "");

        if (apiKey.isEmpty()) {
            return;
        }
        if (!isValidApiKey(apiKey)) {
            context.showToast(localString.get("error_invalid_api_key_format"));
            return;
        }

        // Perform test in background
        new Thread(() -> {
            try {
                // Simple test translation: "hello" to Spanish
                String testUrl = GoogleConstants.API_BASE_URL +
                    "?key=" + java.net.URLEncoder.encode(apiKey, "UTF-8") +
                    "&q=" + java.net.URLEncoder.encode("hello", "UTF-8") +
                    "&target=es&format=text";

                HttpUtils.Request request = HttpUtils.get(testUrl);
                request.setTimeout(10000); // 10 second timeout for test

                org.json.JSONObject response = request.executeToJson();

                // Check for successful response
                if (response.has("data")) {
                    context.showToast(localString.get("msg_api_key_valid"));
                } else {
                    context.showToast(localString.get("error_api_key_invalid"));
                }

            } catch (Exception e) {
                String errorMsg = localString.get("error_api_test_failed") + ": " + e.getMessage();
                context.showToast(errorMsg);
            }
        }).start();
    }

    /**
     * Clear translation cache
     */
    private void clearCache() {
        SharedPreferences prefs = context.getPreferences();
        SharedPreferences.Editor editor = prefs.edit();

        // Remove all cache entries
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(GoogleConstants.CACHE_KEY_PREFIX)) {
                editor.remove(key);
            }
        }

        editor.apply();
        context.showToast(localString.get("msg_cache_cleared"));
    }

    /**
     * Reset usage statistics
     */
    private void resetStatistics() {
        SharedPreferences prefs = context.getPreferences();
        SharedPreferences.Editor editor = prefs.edit();

        editor.putInt(GoogleConstants.PREF_CACHE_HITS, 0);
        editor.putInt(GoogleConstants.PREF_CACHE_MISSES, 0);

        editor.apply();
        context.showToast(localString.get("msg_stats_reset"));
    }
}
