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

    private PluginContext context;
    private SharedPreferences preferences;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onBuild(PluginContext context, Builder builder) {
        this.context = context;
        this.preferences = context.getPreferences();

        // ==================== API Configuration ====================
        builder.addText("🔑 API Configuration")
                .summary("");

        builder.addInput("API Key", GeminiConstants.PREF_API_KEY)
                .defaultValue(GeminiConstants.DEFAULT_API_KEY)
                .summary("Get your FREE API key at aistudio.google.com/app/apikey")
                .valueAsSummary()
                .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        builder.addText("API Key Status")
                .summary(getKeyStatus());

        builder.addText("Test API Key")
                .summary("Verify your API key is working")
                .onClick((pluginUI, item) -> {
                    testApiKey(pluginUI, item);
                });

        builder.addText("Get FREE API Key")
                .summary("Open Google AI Studio to create a free API key")
                .url(GeminiConstants.URL_GET_API_KEY);

        // ==================== Model Selection ====================
        builder.addText("✨ Model Selection")
                .summary("");

        // The model choice is now an implementation detail. The default is the
        // recommended Gemini model; the user can override via the Custom Model
        // field below. The list is intentionally removed — the user is in the
        // Gemini provider preference, so the provider name is already implied.
        String customGeminiKey = ProviderCatalogRefresher.customPrefKeyFor(GeminiConstants.PREF_MODEL_NAME);
        String effectiveGeminiModel = ProviderCatalogRefresher.resolveSelectedModel(
                preferences, GeminiConstants.PREF_MODEL_NAME, GeminiConstants.DEFAULT_MODEL);
        boolean isCustomGemini = !TextUtils.isEmpty(preferences.getString(customGeminiKey, ""));

        builder.addText("Provider")
                .summary("Gemini");
        builder.addText("Model")
                .summary(isCustomGemini
                        ? effectiveGeminiModel + "  (custom override)"
                        : effectiveGeminiModel + "  (default)");

        // Catalog viewer + manual refresh
        builder.addText("Model Catalog")
                .summary(ModelCatalogManager.formatLastRefreshed(
                        preferences, GeminiConstants.PREF_CACHE_GEMINI_MODELS))
                .onClick((pluginUI, item) -> showModelCatalog(pluginUI));

        builder.addText("Refresh Model List")
                .summary("Fetch the latest Gemini models from Google API")
                .onClick((pluginUI, item) -> refreshGeminiModels(pluginUI));

        // Custom model name (forward-compat for any model name)
        builder.addInput("Custom Model (optional)", customGeminiKey)
                .defaultValue("")
                .summary("Overrides the default model above when non-empty. Use for any model name.")
                .hint("e.g. gemini-2.0-flash-exp")
                .valueAsSummary()
                .inputType(InputType.TYPE_CLASS_TEXT);

        // Trigger silent background refresh on first open
        ProviderCatalogRefresher.scheduleAutoRefresh(
                preferences,
                GeminiConstants.PREF_CACHE_GEMINI_MODELS,
                preferences.getString(GeminiConstants.PREF_API_KEY, ""),
                ModelCatalogManager.Provider.GEMINI);

        // ==================== Usage & Limits ====================
        builder.addText("📊 Usage & Limits")
                .summary("");

        builder.addText("Free Tier Limits")
                .summary("2000 requests/day (Flash) | 100 requests/day (Pro) - Completely FREE!");

        builder.addText("API Documentation")
                .summary("View Gemini API documentation and pricing")
                .url(GeminiConstants.URL_API_DOCS);

        // ==================== Test & Debug ====================
        builder.addText("🔧 Test & Debug")
                .summary("");

        builder.addText("Quick Test")
                .summary("Test translation with a simple phrase")
                .onClick((pluginUI, item) -> runQuickTranslationTest(pluginUI));

        builder.addText("View Logs")
                .summary("Open MT Manager log viewer")
                .onClick((pluginUI, item) -> context.openLogViewer());

        // SDK Beta2+ callbacks enabled (minMTVersion >= 26020300)
        builder.onPreferenceChange((pluginUI, preferenceItem, newValue) -> {
            String key = preferenceItem.getKey();
            if (GeminiConstants.PREF_API_KEY.equals(key)) {
                context.showToast("API key updated. Re-open settings to refresh status.");
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
            return "🟡 Ready - Click 'Test API Key' to verify connectivity";
        }
    }

    private void runQuickTranslationTest(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        SharedPreferences prefs = context.getPreferences();
        String apiKey = prefs.getString(GeminiConstants.PREF_API_KEY, "");
        String model = prefs.getString(GeminiConstants.PREF_MODEL_NAME, GeminiConstants.DEFAULT_MODEL);

        if (apiKey.isEmpty()) {
            pluginUI.buildDialog()
                    .setTitle("❌ No API Key")
                    .setMessage("Please configure your Gemini API key first.")
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
                            .setTitle("✅ Translation Successful")
                            .setMessage("Original: Hello\n\nTranslation (Turkish):\n" + result)
                            .setPositiveButton("{ok}", null)
                            .show());
                } else {
                    runOnMainThread(() -> pluginUI.buildDialog()
                            .setTitle("❌ Translation Failed")
                            .setMessage("Received unexpected response format.")
                            .setPositiveButton("{ok}", null)
                            .show());
                }
            } catch (Throwable e) { // Throwable: an Error escaping this thread would crash MT Manager
                runOnMainThread(() -> pluginUI.buildDialog()
                        .setTitle("❌ Test Failed")
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
                    .setTitle("❌ No API Key")
                    .setMessage("Please configure your Gemini API key first.")
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
                part.put("text", "Translate to Turkish: Hello");
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
                            .setTitle("✅ API Key Valid")
                            .setMessage("Your Gemini API key is working correctly!\n\nModel: " + model)
                            .setPositiveButton("{ok}", null)
                            .show());
                } else if (response.contains("error")) {
                    bin.mt.json.JSONObject error = response.getJSONObject("error");
                    String errorMsg = bin.mt.plugin.common.JSONCompat.optString(error, "message", "Unknown error");

                    runOnMainThread(() -> pluginUI.buildDialog()
                            .setTitle("❌ API Error")
                            .setMessage("Error: " + errorMsg)
                            .setPositiveButton("{ok}", null)
                            .show());
                } else {
                    runOnMainThread(() -> pluginUI.buildDialog()
                            .setTitle("❌ Invalid API Key")
                            .setMessage(
                                    "Your API key appears to be invalid.\n\nPlease check your key at:\naistudio.google.com/app/apikey")
                            .setPositiveButton("{ok}", null)
                            .show());
                }

            } catch (Throwable e) { // Throwable: an Error escaping this thread would crash MT Manager
                runOnMainThread(() -> pluginUI.buildDialog()
                        .setTitle("❌ Test Failed")
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
                    .setTitle("⚠️ No API Key")
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
                                .setTitle("❌ Refresh Failed")
                                .setMessage("Could not fetch Gemini models:\n" + error.getMessage())
                                .setPositiveButton("{ok}", null)
                                .show();
                        return;
                    }
                    if (models == null || models.isEmpty()) {
                        pluginUI.buildDialog()
                                .setTitle("⚠️ No Models Found")
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
