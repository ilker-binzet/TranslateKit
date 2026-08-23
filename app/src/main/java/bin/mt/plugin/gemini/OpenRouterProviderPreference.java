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

    @Override
    public void onBuild(PluginContext context, Builder builder) {
        this.context = context;
        this.preferences = context.getPreferences();

        // ==================== API Configuration ====================
        builder.addText("🔑 API Configuration").summary("");

        builder.addInput("API Key", GeminiConstants.PREF_OPENROUTER_API_KEY)
                .defaultValue(GeminiConstants.DEFAULT_API_KEY)
                .summary("Get your API key at openrouter.ai/keys")
                .valueAsSummary()
                .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        builder.addText("API Key Status", KEY_STATUS_ROW).summary(getKeyStatus());

        builder.addText("Test API Key")
                .summary("Send a one-word request to verify the key")
                .onClick((pluginUI, item) -> runQuickTest(pluginUI));

        builder.addText("Get API Key")
                .summary("Open OpenRouter to manage API keys")
                .url(GeminiConstants.URL_OPENROUTER_KEYS);

        // ==================== Model Selection ====================
        builder.addText("🧠 Model Selection").summary("");

        String customKey = ProviderCatalogRefresher.customPrefKeyFor(GeminiConstants.PREF_OPENROUTER_MODEL);
        String effectiveModel = ProviderCatalogRefresher.resolveSelectedModel(
                preferences, GeminiConstants.PREF_OPENROUTER_MODEL,
                GeminiConstants.DEFAULT_OPENROUTER_MODEL);
        boolean isCustom = !TextUtils.isEmpty(preferences.getString(customKey, ""));

        builder.addText("Model")
                .summary(isCustom ? effectiveModel + "  (custom override)"
                                  : effectiveModel + "  (default)");

        builder.addText("Model Catalog")
                .summary(ModelCatalogManager.formatLastRefreshed(
                        preferences, GeminiConstants.PREF_CACHE_OPENROUTER_MODELS))
                .onClick((pluginUI, item) -> showModelCatalog(pluginUI));

        builder.addText("Refresh Model List")
                .summary("Fetch the current catalogue — no API key required")
                .onClick((pluginUI, item) -> refreshModels(pluginUI));

        builder.addInput("Custom Model (optional)", customKey)
                .defaultValue("")
                .summary("Overrides the model above when non-empty. Use the full OpenRouter id.")
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
        builder.addText("📊 Usage & Limits").summary("");

        builder.addText("Pricing")
                .summary("Per-model, pay as you go. Some models are free. Prices show in the catalogue.")
                .url(GeminiConstants.URL_OPENROUTER_PRICING);

        builder.addText("Documentation")
                .summary("View the OpenRouter API documentation")
                .url(GeminiConstants.URL_OPENROUTER_DOCS);

        // onBuild has no PluginUI, so the theme is only known here.
        builder.onCreated((pluginUI, screen) ->
                GeminiColorTokens.applyStatusSummary(pluginUI, screen, KEY_STATUS_ROW));

        builder.onPreferenceChange((pluginUI, preferenceItem, newValue) -> {
            if (GeminiConstants.PREF_OPENROUTER_API_KEY.equals(preferenceItem.getKey())) {
                context.showToast("API key updated. Re-open settings to refresh status.");
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
        return "🟢 Configured";
    }

    private void showModelCatalog(PluginUI pluginUI) {
        // Fully qualified: PluginPreference.Builder exposes its own List type.
        java.util.List<ModelCatalogManager.ModelInfo> models = ProviderCatalogRefresher.composeList(
                preferences,
                GeminiConstants.PREF_CACHE_OPENROUTER_MODELS,
                ModelCatalogManager.getDefaultSeedOpenRouter());

        if (models.isEmpty()) {
            pluginUI.buildDialog()
                    .setTitle("Model Catalog")
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
                                .setTitle("❌ Refresh Failed")
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
                    .setTitle("❌ No API Key")
                    .setMessage("Configure your OpenRouter API key first.")
                    .setPositiveButton("{ok}", null)
                    .show();
            return;
        }

        context.showToast("🔄 Testing…");
        new Thread(() -> {
            try {
                bin.mt.json.JSONObject request = ProviderClient.buildRequest(
                        provider, "Translate to Turkish: Hello",
                        "You are a translator. Return only the translation.");
                bin.mt.json.JSONObject response = bin.mt.plugin.common.HttpUtils.postJson(
                        provider.url(), provider.headers(), request.toString(),
                        GeminiConstants.DEFAULT_TIMEOUT);

                bin.mt.json.JSONObject apiError = ProviderClient.errorOf(response);
                if (apiError != null) {
                    showResult(pluginUI, "❌ Test Failed",
                            bin.mt.plugin.common.JSONCompat.optString(apiError, "message", "Unknown error"));
                    return;
                }
                String result = ProviderClient.parseResponse(provider, response);
                showResult(pluginUI, "✅ Test Successful",
                        "Model: " + provider.model + "\n\nOriginal: Hello\nTranslation: " + result);
            } catch (Throwable e) {
                // Throwable: an Error escaping this thread would crash MT Manager.
                showResult(pluginUI, "❌ Test Failed", String.valueOf(e.getMessage()));
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
