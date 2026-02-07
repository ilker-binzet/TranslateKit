package bin.mt.plugin.gemini;

import android.text.InputType;

import bin.mt.plugin.api.LocalString;
import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.preference.PluginPreference;

/**
 * Sub-preference screen for Translation Settings.
 * Contains: Default AI Engine, Request Timeout, Max Retry Attempts.
 */
public class TranslationSubPreference implements PluginPreference {

    private PluginContext context;

    @Override
    public void onBuild(PluginContext context, Builder builder) {
        this.context = context;
        LocalString localString = context.getAssetLocalString("GeminiTranslate");
        if (localString == null) {
            localString = context.getLocalString();
        }
        builder.setLocalString(localString);

        // ==================== Default AI Engine ====================
        builder.addList("Default AI Engine", GeminiConstants.PREF_DEFAULT_ENGINE)
                .summary("Choose which AI provider to use by default")
                .addItem("Gemini (Fast & Free)", GeminiConstants.ENGINE_GEMINI)
                .addItem("OpenAI GPT (Powerful)", GeminiConstants.ENGINE_OPENAI)
                .addItem("Claude (Balanced)", GeminiConstants.ENGINE_CLAUDE);

        // ==================== Request Timeout ====================
        builder.addInput("Request Timeout (ms)", GeminiConstants.PREF_TIMEOUT)
                .defaultValue(String.valueOf(GeminiConstants.DEFAULT_TIMEOUT))
                .summary("Maximum wait time for API response")
                .valueAsSummary()
                .inputType(InputType.TYPE_CLASS_NUMBER);

        // ==================== Max Retry Attempts ====================
        builder.addInput("Max Retry Attempts", GeminiConstants.PREF_MAX_RETRIES)
                .defaultValue(String.valueOf(GeminiConstants.DEFAULT_MAX_RETRIES))
                .summary("Number of retry attempts on failures")
                .valueAsSummary()
                .inputType(InputType.TYPE_CLASS_NUMBER);

        // Preference change callback
        builder.onPreferenceChange((pluginUI, preferenceItem, newValue) -> {
            String key = preferenceItem.getKey();
            if (GeminiConstants.PREF_DEFAULT_ENGINE.equals(key)) {
                String engineName;
                switch ((String) newValue) {
                    case GeminiConstants.ENGINE_OPENAI: engineName = "OpenAI GPT"; break;
                    case GeminiConstants.ENGINE_CLAUDE: engineName = "Claude"; break;
                    default: engineName = "Gemini"; break;
                }
                context.showToast("Default engine: " + engineName);
            }
        });
    }
}
