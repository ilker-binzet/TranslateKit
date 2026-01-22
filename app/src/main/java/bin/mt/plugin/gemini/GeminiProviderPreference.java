package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;

import bin.mt.plugin.api.LocalString;
import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.preference.PluginPreference;

import java.io.IOException;
import java.util.Collections;

/**
 * Gemini AI Provider Settings
 * Dedicated settings page for Gemini configuration
 * 
 * @author Ilker Binzet
 * @version 0.7.0-MODERN
 */
public class GeminiProviderPreference implements PluginPreference {

    private LocalString localString;
    private PluginContext context;
    private SharedPreferences preferences;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onBuild(PluginContext context, Builder builder) {
        this.context = context;
        this.localString = context.getAssetLocalString("GeminiTranslate");
        if (this.localString == null) {
            this.localString = context.getLocalString();
        }
        this.preferences = context.getPreferences();

        builder.setLocalString(localString);

        // ==================== API Configuration ====================
        builder.addHeader("🔑 API Configuration");

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
        builder.addHeader("✨ Model Selection");

        var geminiModelList = builder.addList("Gemini Model", GeminiConstants.PREF_MODEL_NAME)
                .defaultValue(GeminiConstants.DEFAULT_MODEL)
                .summary("Choose AI model (Gemini 3 Flash recommended)");

        boolean disableCache = preferences.getBoolean(GeminiConstants.PREF_DEBUG_DISABLE_MODEL_CACHE, false);
        java.util.List<ModelCatalogManager.ModelInfo> cachedGeminiModels = disableCache
                ? Collections.emptyList()
                : ModelCatalogManager.loadModelCache(preferences, GeminiConstants.PREF_CACHE_GEMINI_MODELS);
        if (cachedGeminiModels == null || cachedGeminiModels.isEmpty()) {
            geminiModelList.addItem("Gemini 3 Flash (Recommended)", GeminiConstants.MODEL_GEMINI_3_FLASH)
                    .addItem("Gemini 3 Pro (Highest Quality)", GeminiConstants.MODEL_GEMINI_3_PRO)
                    .addItem("Gemini 2.5 Flash (Stable Fallback)", GeminiConstants.MODEL_GEMINI_25_FLASH)
                    .addItem("Gemini 2.5 Pro (Previous Gen)", GeminiConstants.MODEL_GEMINI_25_PRO)
                    .addItem("Gemini 2.0 Flash (Legacy)", GeminiConstants.MODEL_GEMINI_20_FLASH);
        } else {
            for (ModelCatalogManager.ModelInfo info : cachedGeminiModels) {
                geminiModelList.addItem(formatModelLabel(info), info.id);
            }
        }

        builder.addText("Refresh Model List")
                .summary("Fetch the latest Gemini models from Google API")
                .onClick((pluginUI, item) -> refreshGeminiModels(pluginUI));

        // ==================== Usage & Limits ====================
        builder.addHeader("📊 Usage & Limits");

        builder.addText("Free Tier Limits")
                .summary("2000 requests/day (Flash) | 100 requests/day (Pro) - Completely FREE!");

        builder.addText("API Documentation")
                .summary("View Gemini API documentation and pricing")
                .url(GeminiConstants.URL_API_DOCS);

        // ==================== Test & Debug ====================
        builder.addHeader("🔧 Test & Debug");

        builder.addText("Quick Test")
                .summary("Test translation with a simple phrase")
                .onClick((pluginUI, item) -> runQuickTranslationTest(pluginUI));

        builder.addText("View Logs")
                .summary("Open MT Manager log viewer")
                .onClick((pluginUI, item) -> context.openLogViewer());
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

                org.json.JSONObject request = new org.json.JSONObject();
                org.json.JSONArray contents = new org.json.JSONArray();
                org.json.JSONObject part = new org.json.JSONObject();
                part.put("text", prompt);
                org.json.JSONObject content = new org.json.JSONObject();
                content.put("parts", new org.json.JSONArray().put(part));
                contents.put(content);
                request.put("contents", contents);

                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model
                        + ":generateContent?key=" + apiKey;
                GeminiHttpUtils.Request httpRequest = GeminiHttpUtils.post(url);
                httpRequest.setTimeout(10000);
                httpRequest.jsonBody(request);
                org.json.JSONObject response = httpRequest.executeToJson();

                if (response.has("candidates")) {
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
            } catch (Exception e) {
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
                org.json.JSONObject request = new org.json.JSONObject();
                org.json.JSONArray contents = new org.json.JSONArray();
                org.json.JSONObject content = new org.json.JSONObject();
                org.json.JSONArray parts = new org.json.JSONArray();
                org.json.JSONObject part = new org.json.JSONObject();
                part.put("text", "Translate to Turkish: Hello");
                parts.put(part);
                content.put("parts", parts);
                contents.put(content);
                request.put("contents", contents);

                // Test API
                String apiUrl = String.format("%s/%s:generateContent?key=%s",
                        GeminiConstants.API_BASE_URL, model, apiKey);

                GeminiHttpUtils.Request httpRequest = GeminiHttpUtils.post(apiUrl);
                httpRequest.setTimeout(10000);
                httpRequest.jsonBody(request);

                org.json.JSONObject response = httpRequest.executeToJson();

                if (response.has("candidates")) {
                    runOnMainThread(() -> pluginUI.buildDialog()
                            .setTitle("✅ API Key Valid")
                            .setMessage("Your Gemini API key is working correctly!\n\nModel: " + model)
                            .setPositiveButton("{ok}", null)
                            .show());
                } else if (response.has("error")) {
                    org.json.JSONObject error = response.getJSONObject("error");
                    String errorMsg = error.optString("message", "Unknown error");

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

            } catch (Exception e) {
                runOnMainThread(() -> pluginUI.buildDialog()
                        .setTitle("❌ Test Failed")
                        .setMessage("Error: " + e.getMessage())
                        .setPositiveButton("{ok}", null)
                        .show());
            }
        }).start();
    }

    private void refreshGeminiModels(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        // Show toast instead of LoadingDialog to avoid SDK compatibility issues
        context.showToast("🔄 Refreshing Gemini models...");

        String apiKey = preferences.getString(GeminiConstants.PREF_API_KEY, "");
        new Thread(() -> {
            try {
                java.util.List<ModelCatalogManager.ModelInfo> models = ModelCatalogManager.fetchGeminiModels(apiKey);
                ModelCatalogManager.saveModelCache(preferences, GeminiConstants.PREF_CACHE_GEMINI_MODELS, models);
                runOnMainThread(() -> {
                    if (models == null || models.isEmpty()) {
                        pluginUI.buildDialog()
                                .setTitle("⚠️ No Models Found")
                                .setMessage(
                                        "Google API did not return any eligible Gemini models. Please try again later.")
                                .setPositiveButton("{ok}", null)
                                .show();
                    } else {
                        context.showToast("✅ Found " + models.size() + " models");
                        showModelSelectionDialog(pluginUI, "Select Gemini Model", models,
                                GeminiConstants.PREF_MODEL_NAME, GeminiConstants.DEFAULT_MODEL);
                    }
                });
            } catch (IOException e) {
                runOnMainThread(() -> {
                    pluginUI.buildDialog()
                            .setTitle("❌ Refresh Failed")
                            .setMessage("Could not fetch Gemini models:\n" + e.getMessage())
                            .setPositiveButton("{ok}", null)
                            .show();
                });
            }
        }).start();
    }

    private void showModelSelectionDialog(bin.mt.plugin.api.ui.PluginUI pluginUI,
            String title,
            java.util.List<ModelCatalogManager.ModelInfo> models,
            String prefKey,
            String defaultValue) {
        CharSequence[] labels = new CharSequence[models.size()];
        for (int i = 0; i < models.size(); i++) {
            labels[i] = formatModelLabel(models.get(i));
        }
        pluginUI.buildDialog()
                .setTitle(title)
                .setItems(labels, (dialog, which) -> {
                    ModelCatalogManager.ModelInfo selected = models.get(which);
                    preferences.edit().putString(prefKey, selected.id).apply();
                    if (context != null) {
                        context.showToast("Gemini model switched to " + selected.displayName);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("{cancel}", null)
                .show();
    }

    private String formatModelLabel(ModelCatalogManager.ModelInfo info) {
        StringBuilder label = new StringBuilder();
        String name = TextUtils.isEmpty(info.displayName) ? info.id : info.displayName;
        label.append(name);
        if (info.recommended) {
            label.append(" ⭐");
        }
        if (!TextUtils.isEmpty(info.detail)) {
            label.append("\n").append(info.detail);
        }
        return label.toString();
    }

    private void runOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }
}
