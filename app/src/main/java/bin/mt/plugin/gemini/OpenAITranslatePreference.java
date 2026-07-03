package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import android.text.InputType;

import java.util.regex.Pattern;

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

    private PluginContext context;

    @Override
    public void onBuild(PluginContext context, Builder builder) {
        this.context = context;
        SharedPreferences preferences = context.getPreferences();

        builder.title(context.getString("{pref_openai_title}"))
                .subtitle(context.getString("{pref_openai_subtitle}"));

        // Overview
        builder.addText("{pref_openai_header_overview}").summary("");
        builder.addText("{pref_openai_overview_title}")
                .summary("{pref_openai_overview_summary}");
        builder.addText("{pref_openai_limits}")
                .summary("{pref_openai_limits_summary}");

        // API key configuration
        builder.addText("{pref_openai_header_api}").summary("");
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
        builder.addText("{pref_openai_header_model}").summary("");
        builder.addList("{pref_openai_model_title}", GeminiConstants.PREF_OPENAI_MODEL)
                .summary("{pref_openai_model_summary}")
                .addItem("⭐ GPT-4.1 Mini (Recommended)", GeminiConstants.OPENAI_MODEL_GPT41_MINI)
                .addItem("GPT-5.2 (Latest)", GeminiConstants.OPENAI_MODEL_GPT52)
                .addItem("GPT-5.1", GeminiConstants.OPENAI_MODEL_GPT51)
                .addItem("GPT-4.1 (1M Context)", GeminiConstants.OPENAI_MODEL_GPT41)
                .addItem("GPT-4o", GeminiConstants.OPENAI_MODEL_GPT4O)
                .addItem("GPT-4o Mini", GeminiConstants.OPENAI_MODEL_GPT4O_MINI)
                .addItem("o3 (Reasoning)", GeminiConstants.OPENAI_MODEL_O3)
                .addItem("o4-mini (Reasoning)", GeminiConstants.OPENAI_MODEL_O4_MINI);

        builder.addInput("{pref_openai_endpoint_title}", GeminiConstants.PREF_OPENAI_ENDPOINT)
                .defaultValue(GeminiConstants.DEFAULT_OPENAI_ENDPOINT)
                .summary("{pref_openai_endpoint_summary}")
                .valueAsSummary();

        // Helpful resources
        builder.addText("{pref_openai_header_docs}").summary("");
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
        if (apiKey != null) apiKey = apiKey.trim();
        if (apiKey == null || apiKey.isEmpty()) {
            context.showToast(context.getString("{error_openai_no_api_key}"));
            return;
        }

        if (isValidApiKey(apiKey)) {
            context.showToast(context.getString("{msg_openai_key_valid_format}"));
        } else {
            context.showToast(context.getString("{error_openai_invalid_key_format}"));
        }
    }

    private boolean isValidApiKey(String apiKey) {
        return apiKey != null && Pattern.matches(GeminiConstants.OPENAI_API_KEY_PATTERN, apiKey.trim());
    }

    private String describeKeyStatus(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return context.getString("{pref_status_missing}");
        }
        if (!isValidApiKey(apiKey)) {
            return context.getString("{pref_status_invalid_format}");
        }
        return context.getString("{pref_status_ready}");
    }
}
