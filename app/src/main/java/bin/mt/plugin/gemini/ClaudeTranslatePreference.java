package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import android.text.InputType;

import java.util.regex.Pattern;

import bin.mt.plugin.api.LocalString;
import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.preference.PluginPreference;

/**
 * Preference page dedicated to Anthropic Claude configuration.
 */
public class ClaudeTranslatePreference implements PluginPreference {

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
        builder.title(localString.get("pref_claude_title"))
                .subtitle(localString.get("pref_claude_subtitle"));

        // Overview
        builder.addHeader("{pref_claude_header_overview}");
        builder.addText("{pref_claude_overview_title}")
                .summary("{pref_claude_overview_summary}");
        builder.addText("{pref_claude_limits}")
                .summary("{pref_claude_limits_summary}");

        // API key configuration
        builder.addHeader("{pref_claude_header_api}");
        builder.addInput("{pref_claude_api_key_title}", GeminiConstants.PREF_CLAUDE_API_KEY)
                .defaultValue(GeminiConstants.DEFAULT_API_KEY)
                .summary("{pref_claude_api_key_summary}")
                .valueAsSummary()
                .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        builder.addText("{pref_claude_status_title}")
                .summary(describeKeyStatus(
                        preferences.getString(GeminiConstants.PREF_CLAUDE_API_KEY, "")));

        builder.addText("{pref_claude_validate}")
                .summary("{pref_claude_validate_summary}")
                .onClick((pluginUI, item) -> validateApiKey());

        builder.addText("{pref_claude_get_key}")
                .summary("{pref_claude_get_key_summary}")
                .url(GeminiConstants.URL_CLAUDE_KEYS);

        // Model & endpoint selection
        builder.addHeader("{pref_claude_header_model}");
        builder.addList("{pref_claude_model_title}", GeminiConstants.PREF_CLAUDE_MODEL)
                .defaultValue(GeminiConstants.DEFAULT_CLAUDE_MODEL)
                .summary("{pref_claude_model_summary}")
                .addItem("{pref_claude_model_sonnet35}", "claude-3-5-sonnet-20240620")
                .addItem("{pref_claude_model_haiku35}", "claude-3-5-haiku-20240620")
                .addItem("{pref_claude_model_sonnet3}", "claude-3-sonnet-20240229")
                .addItem("{pref_claude_model_haiku3}", "claude-3-haiku-20240307")
                .addItem("{pref_claude_model_opus3}", "claude-3-opus-20240229")
                .addItem("{pref_claude_model_opus35}", "claude-3-5-opus-20240229");

        builder.addText("{pref_claude_model_fallback_note}")
                .summary("{pref_claude_model_fallback_note}");

        builder.addInput("{pref_claude_endpoint_title}", GeminiConstants.PREF_CLAUDE_ENDPOINT)
                .defaultValue(GeminiConstants.DEFAULT_CLAUDE_ENDPOINT)
                .summary("{pref_claude_endpoint_summary}")
                .valueAsSummary();

        // Helpful resources
        builder.addHeader("{pref_claude_header_docs}");
        builder.addText("{pref_claude_docs}")
                .summary("{pref_claude_docs_summary}")
                .url(GeminiConstants.URL_CLAUDE_DOCS);

        builder.addText("{pref_claude_pricing}")
                .summary("{pref_claude_pricing_summary}")
                .url(GeminiConstants.URL_CLAUDE_PRICING);
    }

    private void validateApiKey() {
        SharedPreferences prefs = context.getPreferences();
        String apiKey = prefs.getString(GeminiConstants.PREF_CLAUDE_API_KEY, "");
        if (apiKey == null || apiKey.isEmpty()) {
            context.showToast(localString.get("error_claude_no_api_key"));
            return;
        }

        if (isValidApiKey(apiKey)) {
            context.showToast(localString.get("msg_claude_key_valid_format"));
        } else {
            context.showToast(localString.get("error_claude_invalid_key_format"));
        }
    }

    private boolean isValidApiKey(String apiKey) {
        return Pattern.matches(GeminiConstants.CLAUDE_API_KEY_PATTERN, apiKey);
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
