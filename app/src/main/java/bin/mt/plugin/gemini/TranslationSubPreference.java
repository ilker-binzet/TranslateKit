package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import android.text.InputType;

import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.preference.PluginPreference;
import bin.mt.plugin.api.ui.PluginUI;

/**
 * Sub-preference screen for Translation Settings.
 * Contains: Request Timeout, Max Retry Attempts, batch and bilingual options.
 *
 * <p>Choosing the provider belongs to the AI Providers screen, which is the
 * only place that lists every provider along with whether it is configured.
 */
public class TranslationSubPreference implements PluginPreference {

    /** Row whose summary counts the enabled languages. */
    private static final String KEY_LANGUAGES_ROW = "languages_row";

    private PluginContext context;
    private SharedPreferences preferences;
    private PreferenceScreen screen;

    @Override
    public void onBuild(PluginContext context, Builder builder) {
        this.context = context;
        this.preferences = context.getPreferences();

        // ==================== Languages ====================
        builder.addText("Languages", KEY_LANGUAGES_ROW)
                .summary(buildLanguagesSummary())
                .onClick((pluginUI, item) -> showLanguagePicker(pluginUI));

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

        builder.onCreated((pluginUI, createdScreen) -> this.screen = createdScreen);
    }

    private String buildLanguagesSummary() {
        int enabled = Languages.enabled(preferences).size();
        int total = Languages.allCodes().size();
        return enabled == total
                ? "All " + total + " languages shown in the translate dialog"
                : enabled + " of " + total + " shown in the translate dialog";
    }

    /**
     * Picks which languages appear in MT's Source and Target dropdowns.
     *
     * <p>The engine supports far more languages than anyone translates
     * between, and those dropdowns are a short scrolling list inside a dialog.
     * Ticking a handful here makes them usable.
     */
    private void showLanguagePicker(PluginUI pluginUI) {
        java.util.List<String> codes = Languages.allCodes();
        java.util.List<String> enabled = Languages.enabled(preferences);

        CharSequence[] labels = new CharSequence[codes.size()];
        boolean[] checked = new boolean[codes.size()];
        for (int i = 0; i < codes.size(); i++) {
            labels[i] = Languages.nameOf(codes.get(i)) + "  (" + codes.get(i) + ")";
            checked[i] = enabled.contains(codes.get(i));
        }

        pluginUI.buildDialog()
                .setTitle("Languages")
                .setMultiChoiceItems(labels, checked,
                        (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("{ok}", (dialog, which) -> {
                    java.util.List<String> picked = new java.util.ArrayList<>();
                    for (int i = 0; i < codes.size(); i++) {
                        if (checked[i]) {
                            picked.add(codes.get(i));
                        }
                    }
                    if (picked.isEmpty()) {
                        // An empty list would leave MT with nothing to pick.
                        context.showToast("Pick at least one language — keeping all of them");
                    }
                    Languages.saveEnabled(preferences, picked);
                    refreshLanguagesSummary();
                    context.showToast("Reopen the translate dialog to see the new list");
                    dialog.dismiss();
                })
                .setNegativeButton("{cancel}", null)
                .show();
    }

    private void refreshLanguagesSummary() {
        if (screen == null) return;
        PreferenceItem item = screen.findPreference(KEY_LANGUAGES_ROW);
        if (item != null) {
            item.setSummary(buildLanguagesSummary());
        }
    }
}
