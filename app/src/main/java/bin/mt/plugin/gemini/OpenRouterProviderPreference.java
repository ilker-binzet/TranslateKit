package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;

import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.preference.PluginPreference;
import bin.mt.plugin.api.ui.PluginUI;
import bin.mt.plugin.provider.Provider;
import bin.mt.plugin.provider.ProviderClient;
import bin.mt.plugin.provider.Providers;

/**
 * OpenRouter provider settings.
 *
 * <p>OpenRouter speaks the OpenAI chat/completions wire, so it needs no
 * request code of its own — only a key, a model and a catalogue. Its model
 * listing is public, which is why the catalogue works before a key is set.
 *
 * @author Ilker Binzet
 */
public class OpenRouterProviderPreference implements PluginPreference {

    /** The catalogue runs to several hundred entries; the picker shows the top slice. */
    private static final int CATALOG_LIMIT = 150;

    /** Row whose summary is recoloured by status once the theme is known. */
    private static final String KEY_STATUS_ROW = "key_status_row";

    private PluginContext context;
    private SharedPreferences preferences;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Localized text for {@code key}; the language packs live in assets. */
    private String str(String key) {
        return context.getString(key);
    }

    @Override
    public void onBuild(PluginContext context, Builder builder) {
        this.context = context;
        this.preferences = context.getPreferences();

        // ==================== API Configuration ====================
        builder.addText(str("{prov_head_api}")).summary("");

        builder.addInput(str("{prov_api_key}"), GeminiConstants.PREF_OPENROUTER_API_KEY)
                .defaultValue(GeminiConstants.DEFAULT_API_KEY)
                .summary(str("{openrouter_key_summary}"))
                .valueAsSummary()
                .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        builder.addText(str("{prov_api_key_status}"), KEY_STATUS_ROW).summary(getKeyStatus());

        builder.addText(str("{prov_test_key}"))
                .summary(str("{openrouter_test_summary}"))
                .onClick((pluginUI, item) -> runQuickTest(pluginUI));

        builder.addText(str("{prov_get_key}"))
                .summary(str("{openrouter_get_key_summary}"))
                .url(GeminiConstants.URL_OPENROUTER_KEYS);

        // ==================== Model Selection ====================
        builder.addText(str("{prov_head_model}")).summary("");

        String customKey = ProviderCatalogRefresher.customPrefKeyFor(GeminiConstants.PREF_OPENROUTER_MODEL);
        String effectiveModel = ProviderCatalogRefresher.resolveSelectedModel(
                preferences, GeminiConstants.PREF_OPENROUTER_MODEL,
                GeminiConstants.DEFAULT_OPENROUTER_MODEL);
        boolean isCustom = !TextUtils.isEmpty(preferences.getString(customKey, ""));

        builder.addText(str("{prov_model}"))
                .summary(isCustom ? effectiveModel + str("{prov_custom_override}")
                                  : effectiveModel + str("{prov_default}"));

        builder.addText(str("{prov_model_catalog}"))
                .summary(ModelCatalogManager.formatLastRefreshed(
                        preferences, GeminiConstants.PREF_CACHE_OPENROUTER_MODELS))
                .onClick((pluginUI, item) -> showModelCatalog(pluginUI));

        builder.addText(str("{prov_refresh_models}"))
                .summary(str("{openrouter_refresh_summary}"))
                .onClick((pluginUI, item) -> refreshModels(pluginUI));

        builder.addInput(str("{prov_custom_model}"), customKey)
                .defaultValue("")
                .summary(str("{openrouter_custom_summary}"))
                .hint("e.g. anthropic/claude-opus-4.6")
                .valueAsSummary()
                .inputType(InputType.TYPE_CLASS_TEXT);

        // Public catalogue: refreshes even with no key configured.
        ProviderCatalogRefresher.scheduleAutoRefresh(
                preferences,
                GeminiConstants.PREF_CACHE_OPENROUTER_MODELS,
                preferences.getString(GeminiConstants.PREF_OPENROUTER_API_KEY, ""),
                ModelCatalogManager.Provider.OPENROUTER);

        // ==================== Usage & Limits ====================
        builder.addText(str("{prov_head_usage}")).summary("");

        builder.addText(str("{prov_pricing_short}"))
                .summary(str("{openrouter_pricing}"))
                .url(GeminiConstants.URL_OPENROUTER_PRICING);

        builder.addText(str("{prov_documentation}"))
                .summary(str("{openrouter_docs_summary}"))
                .url(GeminiConstants.URL_OPENROUTER_DOCS);

        // onBuild has no PluginUI, so the theme is only known here.
        builder.onCreated((pluginUI, screen) ->
                GeminiColorTokens.applyStatusSummary(pluginUI, screen, KEY_STATUS_ROW));

        builder.onPreferenceChange((pluginUI, preferenceItem, newValue) -> {
            if (GeminiConstants.PREF_OPENROUTER_API_KEY.equals(preferenceItem.getKey())) {
                context.showToast(str("{prov_key_updated}"));
            }
        });
    }

    private String getKeyStatus() {
        String apiKey = preferences.getString(GeminiConstants.PREF_OPENROUTER_API_KEY, "");
        if (apiKey == null || apiKey.isEmpty()) {
            return "⚪ Not Configured — the catalogue still works, translation does not";
        }
        if (!apiKey.matches(GeminiConstants.OPENROUTER_API_KEY_PATTERN)) {
            return "🔴 Invalid Format — OpenRouter keys start with 'sk-or-v1-'";
        }
        return str("{prov_status_configured}");
    }

    private void showModelCatalog(PluginUI pluginUI) {
        // Fully qualified: PluginPreference.Builder exposes its own List type.
        java.util.List<ModelCatalogManager.ModelInfo> models = ProviderCatalogRefresher.composeList(
                preferences,
                GeminiConstants.PREF_CACHE_OPENROUTER_MODELS,
                ModelCatalogManager.getDefaultSeedOpenRouter());

        if (models.isEmpty()) {
            pluginUI.buildDialog()
                    .setTitle(str("{prov_model_catalog}"))
                    .setMessage("No models cached yet. Tap \"Refresh Model List\" first.")
                    .setPositiveButton("{ok}", null)
                    .show();
            return;
        }

        int shown = Math.min(models.size(), CATALOG_LIMIT);
        CharSequence[] labels = new CharSequence[shown];
        for (int i = 0; i < shown; i++) {
            ModelCatalogManager.ModelInfo m = models.get(i);
            labels[i] = TextUtils.isEmpty(m.detail)
                    ? m.displayName + "\n" + m.id
                    : m.displayName + "\n" + m.id + "  •  " + m.detail;
        }

        // Say what was left out rather than quietly truncating.
        String title = shown < models.size()
                ? "Model Catalog (" + shown + " of " + models.size() + ")"
                : "Model Catalog (" + shown + ")";

        pluginUI.buildDialog()
                .setTitle(title)
                .setItems(labels, (dialog, which) -> {
                    String id = models.get(which).id;
                    preferences.edit()
                            .putString(GeminiConstants.PREF_OPENROUTER_MODEL, id)
                            .apply();
                    context.showToast("Model set to " + id + ". Re-open settings to refresh.");
                    dialog.dismiss();
                })
                .setNegativeButton("{cancel}", null)
                .show();
    }

    private void refreshModels(PluginUI pluginUI) {
        context.showToast("🔄 Fetching model catalogue…");
        ModelCatalogManager.forceRefresh(
                preferences,
                GeminiConstants.PREF_CACHE_OPENROUTER_MODELS,
                preferences.getString(GeminiConstants.PREF_OPENROUTER_API_KEY, ""),
                ModelCatalogManager.Provider.OPENROUTER,
                (models, error) -> mainHandler.post(() -> {
                    if (error != null) {
                        pluginUI.buildDialog()
                                .setTitle(str("{prov_refresh_failed}"))
                                .setMessage(error.getMessage())
                                .setPositiveButton("{ok}", null)
                                .show();
                        return;
                    }
                    context.showToast("✅ " + models.size() + " models cached");
                }));
    }

    private void runQuickTest(PluginUI pluginUI) {
        Provider provider = Providers.openrouter(preferences);

        if (provider.apiKey.isEmpty()) {
            pluginUI.buildDialog()
                    .setTitle(str("{prov_no_key}"))
                    .setMessage(str("{openrouter_need_key}"))
                    .setPositiveButton("{ok}", null)
                    .show();
            return;
        }

        context.showToast(str("{prov_testing}"));
        new Thread(() -> {
            try {
                bin.mt.json.JSONObject request = ProviderClient.buildRequest(
                        provider, str("{prov_test_prompt}"),
                        "You are a translator. Return only the translation.");
                bin.mt.json.JSONObject response = bin.mt.plugin.common.HttpUtils.postJson(
                        provider.url(), provider.headers(), request.toString(),
                        GeminiConstants.DEFAULT_TIMEOUT);

                bin.mt.json.JSONObject apiError = ProviderClient.errorOf(response);
                if (apiError != null) {
                    showResult(pluginUI, str("{prov_test_failed}"),
                            bin.mt.plugin.common.JSONCompat.optString(apiError, "message", str("{prov_unknown_error}")));
                    return;
                }
                String result = ProviderClient.parseResponse(provider, response);
                showResult(pluginUI, str("{prov_test_ok}"),
                        "Model: " + provider.model + "\n\nOriginal: Hello\nTranslation: " + result);
            } catch (Throwable e) {
                // Throwable: an Error escaping this thread would crash MT Manager.
                showResult(pluginUI, str("{prov_test_failed}"), String.valueOf(e.getMessage()));
            }
        }).start();
    }

    private void showResult(PluginUI pluginUI, String title, String message) {
        // Success titles are marked with a check; tint those green so the
        // outcome reads at a glance rather than from the wording.
        CharSequence styled = title.startsWith("✅")
                ? GeminiColorTokens.success(pluginUI, title)
                : title;
        mainHandler.post(() -> pluginUI.buildDialog()
                .setTitle(styled)
                .setMessage(message)
                .setPositiveButton("{ok}", null)
                .show());
    }
}
