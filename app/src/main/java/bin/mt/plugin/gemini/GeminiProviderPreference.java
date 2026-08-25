package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;

import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.preference.PluginPreference;

/**
 * Gemini AI Provider Settings
 * Dedicated settings page for Gemini configuration
 *
 * @author Ilker Binzet
 * @version 0.4.0-beta - Auto-refreshing model catalog + custom model override
 */
public class GeminiProviderPreference implements PluginPreference {

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
        builder.addText(str("{prov_head_api}"))
                .summary("");

        builder.addInput(str("{prov_api_key}"), GeminiConstants.PREF_API_KEY)
                .defaultValue(GeminiConstants.DEFAULT_API_KEY)
                .summary(str("{gemini_key_summary}"))
                .valueAsSummary()
                .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        builder.addText(str("{prov_api_key_status}"), KEY_STATUS_ROW)
                .summary(getKeyStatus());

        builder.addText(str("{prov_test_key}"))
                .summary(str("{prov_test_key_summary}"))
                .onClick((pluginUI, item) -> {
                    testApiKey(pluginUI, item);
                });

        builder.addText(str("{gemini_get_key}"))
                .summary(str("{gemini_get_key_summary}"))
                .url(GeminiConstants.URL_GET_API_KEY);

        // ==================== Model Selection ====================
        builder.addText(str("{prov_head_model}"))
                .summary("");

        // The model choice is now an implementation detail. The default is the
        // recommended Gemini model; the user can override via the Custom Model
        // field below. The list is intentionally removed — the user is in the
        // Gemini provider preference, so the provider name is already implied.
        String customGeminiKey = ProviderCatalogRefresher.customPrefKeyFor(GeminiConstants.PREF_MODEL_NAME);
        String effectiveGeminiModel = ProviderCatalogRefresher.resolveSelectedModel(
                preferences, GeminiConstants.PREF_MODEL_NAME, GeminiConstants.DEFAULT_MODEL);
        boolean isCustomGemini = !TextUtils.isEmpty(preferences.getString(customGeminiKey, ""));

        builder.addText(str("{prov_provider}"))
                .summary("Gemini");
        builder.addText(str("{prov_model}"))
                .summary(isCustomGemini
                        ? effectiveGeminiModel + str("{prov_custom_override}")
                        : effectiveGeminiModel + str("{prov_default}"));

        // Catalog viewer + manual refresh
        builder.addText(str("{prov_model_catalog}"))
                .summary(ModelCatalogManager.formatLastRefreshed(
                        preferences, GeminiConstants.PREF_CACHE_GEMINI_MODELS))
                .onClick((pluginUI, item) -> showModelCatalog(pluginUI));

        builder.addText(str("{prov_refresh_models}"))
                .summary(str("{gemini_refresh_summary}"))
                .onClick((pluginUI, item) -> refreshGeminiModels(pluginUI));

        // Custom model name (forward-compat for any model name)
        builder.addInput(str("{prov_custom_model}"), customGeminiKey)
                .defaultValue("")
                .summary(str("{prov_custom_model_summary}"))
                .hint(str("{gemini_model_hint}"))
                .valueAsSummary()
                .inputType(InputType.TYPE_CLASS_TEXT);

        // Trigger silent background refresh on first open
        ProviderCatalogRefresher.scheduleAutoRefresh(
                preferences,
                GeminiConstants.PREF_CACHE_GEMINI_MODELS,
                preferences.getString(GeminiConstants.PREF_API_KEY, ""),
                ModelCatalogManager.Provider.GEMINI);

        // ==================== Usage & Limits ====================
        builder.addText(str("{prov_head_usage}"))
                .summary("");

        builder.addText(str("{prov_free_limits}"))
                .summary(str("{gemini_limits}"));

        builder.addText(str("{prov_api_docs}"))
                .summary(str("{gemini_docs_summary}"))
                .url(GeminiConstants.URL_API_DOCS);

        // ==================== Test & Debug ====================
        builder.addText(str("{prov_head_test}"))
                .summary("");

        builder.addText(str("{prov_quick_test}"))
                .summary(str("{prov_quick_test_summary}"))
                .onClick((pluginUI, item) -> runQuickTranslationTest(pluginUI));

        builder.addText(str("{prov_view_logs}"))
                .summary(str("{prov_view_logs_summary}"))
                .onClick((pluginUI, item) -> context.openLogViewer());

        // SDK Beta2+ callbacks enabled (minMTVersion >= 26020300)
        // onBuild has no PluginUI, so the theme is only known here.
        builder.onCreated((pluginUI, screen) ->
                GeminiColorTokens.applyStatusSummary(pluginUI, screen, KEY_STATUS_ROW));

        builder.onPreferenceChange((pluginUI, preferenceItem, newValue) -> {
            String key = preferenceItem.getKey();
            if (GeminiConstants.PREF_API_KEY.equals(key)) {
                context.showToast(str("{prov_key_updated}"));
            }
        });
    }

    private String getKeyStatus() {
        String apiKey = preferences.getString(GeminiConstants.PREF_API_KEY, "");

        if (apiKey.isEmpty()) {
            return "⚪ Not Configured - Click 'Get FREE API Key' above";
        } else if (!java.util.regex.Pattern.matches(GeminiConstants.API_KEY_PATTERN, apiKey)) {
            return "🔴 Invalid Format - Please check your API key";
        } else {
            return "🟢 Ready - Click 'Test API Key' to verify connectivity";
        }
    }

    private void runQuickTranslationTest(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        SharedPreferences prefs = context.getPreferences();
        String apiKey = prefs.getString(GeminiConstants.PREF_API_KEY, "");
        String model = prefs.getString(GeminiConstants.PREF_MODEL_NAME, GeminiConstants.DEFAULT_MODEL);

        if (apiKey.isEmpty()) {
            pluginUI.buildDialog()
                    .setTitle(str("{prov_no_key}"))
                    .setMessage(str("{gemini_need_key}"))
                    .setPositiveButton("{ok}", null)
                    .show();
            return;
        }

        // Show toast instead of LoadingDialog to avoid SDK compatibility issues
        context.showToast("🔄 Translating...");

        new Thread(() -> {
            try {
                String text = "Hello";
                String prompt = "Translate this text to Turkish: " + text;

                bin.mt.json.JSONObject request = new bin.mt.json.JSONObject();
                bin.mt.json.JSONArray contents = new bin.mt.json.JSONArray();
                bin.mt.json.JSONObject part = new bin.mt.json.JSONObject();
                part.put("text", prompt);
                bin.mt.json.JSONObject content = new bin.mt.json.JSONObject();
                content.put("parts", new bin.mt.json.JSONArray().add(part));
                contents.add(content);
                request.put("contents", contents);

                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model
                        + ":generateContent?key=" + apiKey;
                bin.mt.json.JSONObject response = bin.mt.plugin.common.HttpUtils.postJson(url, null, request.toString());

                if (response.contains("candidates")) {
                    String result = response.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text").trim();

                    runOnMainThread(() -> pluginUI.buildDialog()
                            .setTitle(GeminiColorTokens.success(pluginUI, "✅ Translation Successful"))
                            .setMessage(str("{prov_test_result}") + result)
                            .setPositiveButton("{ok}", null)
                            .show());
                } else {
                    runOnMainThread(() -> pluginUI.buildDialog()
                            .setTitle(str("{prov_translation_failed}"))
                            .setMessage(str("{prov_bad_response}"))
                            .setPositiveButton("{ok}", null)
                            .show());
                }
            } catch (Throwable e) { // Throwable: an Error escaping this thread would crash MT Manager
                runOnMainThread(() -> pluginUI.buildDialog()
                        .setTitle(str("{prov_test_failed}"))
                        .setMessage("Error: " + e.getMessage())
                        .setPositiveButton("{ok}", null)
                        .show());
            }
        }).start();
    }

    private void testApiKey(bin.mt.plugin.api.ui.PluginUI pluginUI, Object item) {
        SharedPreferences prefs = context.getPreferences();
        String apiKey = prefs.getString(GeminiConstants.PREF_API_KEY, "");
        String model = prefs.getString(GeminiConstants.PREF_MODEL_NAME, GeminiConstants.DEFAULT_MODEL);

        if (apiKey.isEmpty()) {
            pluginUI.buildDialog()
                    .setTitle(str("{prov_no_key}"))
                    .setMessage(str("{gemini_need_key}"))
                    .setPositiveButton("{ok}", null)
                    .show();
            return;
        }

        // Show toast instead of LoadingDialog to avoid SDK compatibility issues
        context.showToast("🔄 Testing API Connection...");

        new Thread(() -> {
            try {
                // Build test request
                bin.mt.json.JSONObject request = new bin.mt.json.JSONObject();
                bin.mt.json.JSONArray contents = new bin.mt.json.JSONArray();
                bin.mt.json.JSONObject content = new bin.mt.json.JSONObject();
                bin.mt.json.JSONArray parts = new bin.mt.json.JSONArray();
                bin.mt.json.JSONObject part = new bin.mt.json.JSONObject();
                part.put("text", str("{prov_test_prompt}"));
                parts.add(part);
                content.put("parts", parts);
                contents.add(content);
                request.put("contents", contents);

                // Test API
                String apiUrl = String.format("%s/%s:generateContent?key=%s",
                        GeminiConstants.API_BASE_URL, model, apiKey);

                bin.mt.json.JSONObject response = bin.mt.plugin.common.HttpUtils.postJson(apiUrl, null, request.toString());

                if (response.contains("candidates")) {
                    runOnMainThread(() -> pluginUI.buildDialog()
                            .setTitle(GeminiColorTokens.success(pluginUI, str("{prov_key_valid}")))
                            .setMessage("Your Gemini API key is working correctly!\n\nModel: " + model)
                            .setPositiveButton("{ok}", null)
                            .show());
                } else if (response.contains("error")) {
                    bin.mt.json.JSONObject error = response.getJSONObject("error");
                    String errorMsg = bin.mt.plugin.common.JSONCompat.optString(error, "message", str("{prov_unknown_error}"));

                    runOnMainThread(() -> pluginUI.buildDialog()
                            .setTitle(str("{prov_api_error}"))
                            .setMessage("Error: " + errorMsg)
                            .setPositiveButton("{ok}", null)
                            .show());
                } else {
                    runOnMainThread(() -> pluginUI.buildDialog()
                            .setTitle(str("{prov_bad_key}"))
                            .setMessage(
                                    "Your API key appears to be invalid.\n\nPlease check your key at:\naistudio.google.com/app/apikey")
                            .setPositiveButton("{ok}", null)
                            .show());
                }

            } catch (Throwable e) { // Throwable: an Error escaping this thread would crash MT Manager
                runOnMainThread(() -> pluginUI.buildDialog()
                        .setTitle(str("{prov_test_failed}"))
                        .setMessage("Error: " + e.getMessage())
                        .setPositiveButton("{ok}", null)
                        .show());
            }
        }).start();
    }

    /**
     * Show the merged (cached + curated) model catalog in a dialog.
     * Pure local read — never touches the network, so it opens instantly.
     */
    private void showModelCatalog(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        java.util.List<ModelCatalogManager.ModelInfo> models = ProviderCatalogRefresher.composeList(
                preferences,
                GeminiConstants.PREF_CACHE_GEMINI_MODELS,
                ModelCatalogManager.getDefaultSeedGemini());

        String effectiveModel = ProviderCatalogRefresher.resolveSelectedModel(
                preferences, GeminiConstants.PREF_MODEL_NAME, GeminiConstants.DEFAULT_MODEL);

        StringBuilder message = new StringBuilder();
        message.append(ModelCatalogManager.formatLastRefreshed(
                preferences, GeminiConstants.PREF_CACHE_GEMINI_MODELS)).append("\n\n");
        for (ModelCatalogManager.ModelInfo info : models) {
            if (info.id.equals(effectiveModel)) {
                message.append("▶ ");
            } else {
                message.append("• ");
            }
            message.append(info.displayName).append("\n   ").append(info.id);
            if (info.recommended) {
                message.append("  ⭐");
            }
            message.append('\n');
        }
        message.append("\n▶ = active model, ⭐ = recommended.\n")
               .append("Use 'Custom Model' below to switch; 'Refresh Model List' fetches the live catalog.");

        pluginUI.buildDialog()
                .setTitle("📚 Model Catalog (" + models.size() + ")")
                .setMessage(message.toString())
                .setPositiveButton("{ok}", null)
                .show();
    }

    private void refreshGeminiModels(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        String apiKey = preferences.getString(GeminiConstants.PREF_API_KEY, "");
        if (TextUtils.isEmpty(apiKey)) {
            pluginUI.buildDialog()
                    .setTitle(str("{prov_no_key_warn}"))
                    .setMessage("Add your Gemini API key first, then refresh.")
                    .setPositiveButton("{ok}", null)
                    .show();
            return;
        }
        // Progress indicator: without it a slow network reads as a hang.
        bin.mt.plugin.api.ui.dialog.LoadingDialog loadingDialog =
                new bin.mt.plugin.api.ui.dialog.LoadingDialog(pluginUI)
                        .setMessage("Fetching Gemini models…");
        loadingDialog.show();
        ModelCatalogManager.forceRefresh(
                preferences,
                GeminiConstants.PREF_CACHE_GEMINI_MODELS,
                apiKey,
                ModelCatalogManager.Provider.GEMINI,
                (models, error) -> runOnMainThread(() -> {
                    loadingDialog.dismiss();
                    if (error != null) {
                        pluginUI.buildDialog()
                                .setTitle(str("{prov_refresh_failed}"))
                                .setMessage("Could not fetch Gemini models:\n" + error.getMessage())
                                .setPositiveButton("{ok}", null)
                                .show();
                        return;
                    }
                    if (models == null || models.isEmpty()) {
                        pluginUI.buildDialog()
                                .setTitle(str("{prov_no_models}"))
                                .setMessage("Google API did not return any eligible Gemini models. " +
                                        "Check your API key permissions or try again later.")
                                .setPositiveButton("{ok}", null)
                                .show();
                        return;
                    }
                    int merged = ProviderCatalogRefresher.composeList(
                            preferences,
                            GeminiConstants.PREF_CACHE_GEMINI_MODELS,
                            ModelCatalogManager.getDefaultSeedGemini()).size();
                    context.showToast("✅ " + models.size() + " live models cached, " + merged
                            + " total available — reopen to apply");
                })
        );
    }

    private void runOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }
}
