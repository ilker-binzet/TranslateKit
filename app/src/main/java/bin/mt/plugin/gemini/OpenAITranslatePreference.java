package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import android.text.InputType;

import java.util.regex.Pattern;

import bin.mt.plugin.api.LocalString;
import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.preference.PluginPreference;

/**
 * Preference screen for configuring OpenAI access within the plugin.
 *
 * Provides a dedicated page so users can manage their OpenAI API key,
 * pick a default model, and review documentation/pricing links without
 * cluttering the Gemini settings page.
 */
public class OpenAITranslatePreference implements PluginPreference {

    private LocalString localString;
    private PluginContext context;

    @Override
    public void onBuild(PluginContext context, Builder builder) {
        this.context = context;
        this.localString = context.getAssetLocalString("GeminiTranslate");
        if (this.localString == null) {
            this.localString = context.getLocalString();
        }
        SharedPreferences preferences = context.getPreferences();

        builder.setLocalString(localString);
        builder.title(localString.get("pref_openai_title"))
                .subtitle(localString.get("pref_openai_subtitle"));

        // Overview
        builder.addHeader("{pref_openai_header_overview}");
        builder.addText("{pref_openai_overview_title}")
                .summary("{pref_openai_overview_summary}");
        builder.addText("{pref_openai_limits}")
                .summary("{pref_openai_limits_summary}");

        // API key configuration
        builder.addHeader("{pref_openai_header_api}");
        builder.addInput("{pref_openai_api_key_title}", GeminiConstants.PREF_OPENAI_API_KEY)
                .defaultValue(GeminiConstants.DEFAULT_API_KEY)
                .summary("{pref_openai_api_key_summary}")
                .valueAsSummary()
                .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        builder.addText("{pref_openai_status_title}")
                .summary(describeKeyStatus(
                        preferences.getString(GeminiConstants.PREF_OPENAI_API_KEY, "")));

        builder.addText("{pref_openai_validate}")
                .summary("{pref_openai_validate_summary}")
                .onClick((pluginUI, item) -> validateApiKey());

        builder.addText("{pref_openai_get_key}")
                .summary("{pref_openai_get_key_summary}")
                .url(GeminiConstants.URL_OPENAI_KEYS);

        // Model and endpoint
        builder.addHeader("{pref_openai_header_model}");
        builder.addList("{pref_openai_model_title}", GeminiConstants.PREF_OPENAI_MODEL)
                .defaultValue(GeminiConstants.DEFAULT_OPENAI_MODEL)
                .summary("{pref_openai_model_summary}")
                .addItem("{pref_openai_model_gpt4omini}", "gpt-4o-mini")
                .addItem("{pref_openai_model_gpt4o}", "gpt-4o")
                .addItem("{pref_openai_model_o1mini}", "o1-mini")
                .addItem("{pref_openai_model_o1}", "o1");

        builder.addInput("{pref_openai_endpoint_title}", GeminiConstants.PREF_OPENAI_ENDPOINT)
                .defaultValue(GeminiConstants.DEFAULT_OPENAI_ENDPOINT)
                .summary("{pref_openai_endpoint_summary}")
                .valueAsSummary();

        // Helpful resources
        builder.addHeader("{pref_openai_header_docs}");
        builder.addText("{pref_openai_docs}")
                .summary("{pref_openai_docs_summary}")
                .url(GeminiConstants.URL_OPENAI_DOCS);

        builder.addText("{pref_openai_pricing}")
                .summary("{pref_openai_pricing_summary}")
                .url(GeminiConstants.URL_OPENAI_PRICING);
    }

    private void validateApiKey() {
        SharedPreferences prefs = context.getPreferences();
        String apiKey = prefs.getString(GeminiConstants.PREF_OPENAI_API_KEY, "");
        if (apiKey == null || apiKey.isEmpty()) {
            context.showToast(localString.get("error_openai_no_api_key"));
            return;
        }

        if (isValidApiKey(apiKey)) {
            context.showToast(localString.get("msg_openai_key_valid_format"));
        } else {
            context.showToast(localString.get("error_openai_invalid_key_format"));
        }
    }

    private boolean isValidApiKey(String apiKey) {
        return Pattern.matches(GeminiConstants.OPENAI_API_KEY_PATTERN, apiKey);
    }

    private String describeKeyStatus(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return localString.get("pref_status_missing");
        }
        if (!isValidApiKey(apiKey)) {
            return localString.get("pref_status_invalid_format");
        }
        return localString.get("pref_status_ready");
    }
}
