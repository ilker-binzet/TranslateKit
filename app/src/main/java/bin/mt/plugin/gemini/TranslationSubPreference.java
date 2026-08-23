package bin.mt.plugin.gemini;

import android.text.InputType;

import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.preference.PluginPreference;

/**
 * Sub-preference screen for Translation Settings.
 * Contains: Request Timeout, Max Retry Attempts, batch and bilingual options.
 *
 * <p>Choosing the provider belongs to the AI Providers screen, which is the
 * only place that lists every provider along with whether it is configured.
 */
public class TranslationSubPreference implements PluginPreference {

    @Override
    public void onBuild(PluginContext context, Builder builder) {
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

        // ==================== Batch Translation ====================
        builder.addSwitch(context.getString("{pref_batch_enabled}"), GeminiConstants.PREF_BATCH_ENABLED)
                .defaultValue(GeminiConstants.DEFAULT_BATCH_ENABLED)
                .summary(context.getString("{pref_batch_enabled_summary}"));

        builder.addInput(context.getString("{pref_batch_size}"), GeminiConstants.PREF_BATCH_SIZE)
                .defaultValue(String.valueOf(GeminiConstants.DEFAULT_BATCH_SIZE))
                .summary(context.getString("{pref_batch_size_summary}"))
                .valueAsSummary()
                .inputType(InputType.TYPE_CLASS_NUMBER);

        builder.addInput(context.getString("{pref_batch_max_chars}"), GeminiConstants.PREF_BATCH_MAX_CHARS)
                .defaultValue(String.valueOf(GeminiConstants.DEFAULT_BATCH_MAX_CHARS))
                .summary(context.getString("{pref_batch_max_chars_summary}"))
                .valueAsSummary()
                .inputType(InputType.TYPE_CLASS_NUMBER);

        // Bilingual output mode
        builder.addSwitch(context.getString("{pref_bilingual_mode}"), GeminiConstants.PREF_BILINGUAL_MODE)
                .defaultValue(GeminiConstants.DEFAULT_BILINGUAL_MODE)
                .summary(context.getString("{pref_bilingual_mode_summary}"));
    }
}
