package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;

import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.preference.PluginPreference;

/**
 * OpenAI GPT Provider Settings
 * Dedicated settings page for OpenAI GPT configuration
 *
 * @author Ilker Binzet
 * @version 0.4.0-beta - Auto-refreshing model catalog + custom model override
 */
public class OpenAIProviderPreference implements PluginPreference {

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

        builder.addInput("API Key", GeminiConstants.PREF_OPENAI_API_KEY)
                .defaultValue(GeminiConstants.DEFAULT_API_KEY)
                .summary("Get your API key at platform.openai.com/api-keys")
                .valueAsSummary()
                .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        builder.addText("API Key Status")
                .summary(getKeyStatus());

        builder.addText("Test API Key")
                .summary("Verify your API key is working")
                .onClick((pluginUI, item) -> {
                    testApiKey(pluginUI, item);
                });

        builder.addText("Get API Key")
                .summary("Open OpenAI Platform to manage API keys")
                .url("https://platform.openai.com/api-keys");

        // ==================== Model Selection ====================
        builder.addText("🧠 Model Selection").summary("");

        // The model choice is an implementation detail. The default is the
        // recommended OpenAI model; the user can override via Custom Model.
        String customOpenAiKey = ProviderCatalogRefresher.customPrefKeyFor(GeminiConstants.PREF_OPENAI_MODEL);
        String effectiveOpenAiModel = ProviderCatalogRefresher.resolveSelectedModel(
                preferences, GeminiConstants.PREF_OPENAI_MODEL, GeminiConstants.DEFAULT_OPENAI_MODEL);
        boolean isCustomOpenAi = !TextUtils.isEmpty(preferences.getString(customOpenAiKey, ""));

        builder.addText("Provider")
            .summary("OpenAI");
        builder.addText("Model")
            .summary(isCustomOpenAi
                    ? effectiveOpenAiModel + "  (custom override)"
                    : effectiveOpenAiModel + "  (default)");

        builder.addText("Model Catalog")
            .summary(ModelCatalogManager.formatLastRefreshed(
                    preferences, GeminiConstants.PREF_CACHE_OPENAI_MODELS))
            .onClick((pluginUI, item) -> showModelCatalog(pluginUI));

        builder.addText("Refresh Model List")
            .summary("Fetch all available Chat Completions models")
            .onClick((pluginUI, item) -> refreshOpenAiModels(pluginUI));

        builder.addInput("Custom Model (optional)", customOpenAiKey)
                .defaultValue("")
                .summary("Overrides the default model above when non-empty. Use for any model name.")
                .hint("e.g. gpt-5.3-nano")
                .valueAsSummary()
                .inputType(InputType.TYPE_CLASS_TEXT);

        ProviderCatalogRefresher.scheduleAutoRefresh(
                preferences,
                GeminiConstants.PREF_CACHE_OPENAI_MODELS,
                preferences.getString(GeminiConstants.PREF_OPENAI_API_KEY, ""),
                ModelCatalogManager.Provider.OPENAI);

        // ==================== Usage & Limits ====================
        builder.addText("📊 Usage & Limits")
                .summary("");

        builder.addText("Pricing Information")
                .summary("GPT-4o: $2.50/1M input tokens | GPT-4o Mini: $0.15/1M tokens - Pay as you go");

        builder.addText("API Documentation")
                .summary("View OpenAI API documentation and pricing")
                .url("https://platform.openai.com/docs/overview");

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
            if (GeminiConstants.PREF_OPENAI_API_KEY.equals(key)) {
                context.showToast("API key updated. Re-open settings to refresh status.");
            }
        });
    }

    private String getKeyStatus() {
        String apiKey = preferences.getString(GeminiConstants.PREF_OPENAI_API_KEY, "");
        
        if (apiKey.isEmpty()) {
            return "⚪ Not Configured - Click 'Get API Key' above";
        } else if (!apiKey.startsWith("sk-")) {
            return "🔴 Invalid Format - OpenAI keys start with 'sk-'";
        } else {
            return "🟡 Ready - Click 'Test API Key' to verify connectivity";
        }
    }

    private void runQuickTranslationTest(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        SharedPreferences prefs = context.getPreferences();
        String apiKey = prefs.getString(GeminiConstants.PREF_OPENAI_API_KEY, "");
        String model = prefs.getString(GeminiConstants.PREF_OPENAI_MODEL, GeminiConstants.DEFAULT_OPENAI_MODEL);

        if (apiKey.isEmpty()) {
            pluginUI.buildDialog()
                    .setTitle("❌ No API Key")
                    .setMessage("Please configure your OpenAI API key first.")
                    .setPositiveButton("{ok}", null)
                    .show();
            return;
        }

        // Show toast instead of LoadingDialog for backward compatibility
        context.showToast("🔄 Translating...");
        
        new Thread(() -> {
            try {
                bin.mt.json.JSONObject request = new bin.mt.json.JSONObject();
                request.put("model", model);
                bin.mt.json.JSONArray messages = new bin.mt.json.JSONArray();
                bin.mt.json.JSONObject message = new bin.mt.json.JSONObject();
                message.put("role", "user");
                message.put("content", "Translate to Turkish: Hello");
                messages.add(message);
                request.put("messages", messages);
                request.put("max_tokens", 50);

                java.util.Map<String, String> headers = new java.util.HashMap<>();
                headers.put("Authorization", "Bearer " + apiKey);
                bin.mt.json.JSONObject response = bin.mt.plugin.common.HttpUtils.postJson(
                        "https://api.openai.com/v1/chat/completions", headers, request.toString());

                if (response.contains("choices")) {
                    String result = response.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content").trim();

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
        String apiKey = prefs.getString(GeminiConstants.PREF_OPENAI_API_KEY, "");
        String model = prefs.getString(GeminiConstants.PREF_OPENAI_MODEL, GeminiConstants.DEFAULT_OPENAI_MODEL);

        if (apiKey.isEmpty()) {
            pluginUI.buildDialog()
                    .setTitle("❌ No API Key")
                    .setMessage("Please configure your OpenAI API key first.")
                    .setPositiveButton("{ok}", null)
                    .show();
            return;
        }

        if (!apiKey.startsWith("sk-")) {
            pluginUI.buildDialog()
                    .setTitle("❌ Invalid Key Format")
                    .setMessage("OpenAI API keys must start with 'sk-'")
                    .setPositiveButton("{ok}", null)
                    .show();
            return;
        }

        // Show progress dialog
        bin.mt.plugin.api.ui.dialog.LoadingDialog loadingDialog = 
            new bin.mt.plugin.api.ui.dialog.LoadingDialog(pluginUI)
                .setMessage("Testing API Connection...")
                .setSecondaryMessage("Please wait while we verify your API key")
                .show();

        new Thread(() -> {
            try {
                // Build minimal test request
                bin.mt.json.JSONObject request = new bin.mt.json.JSONObject();
                request.put("model", model);

                bin.mt.json.JSONArray messages = new bin.mt.json.JSONArray();
                bin.mt.json.JSONObject message = new bin.mt.json.JSONObject();
                message.put("role", "user");
                message.put("content", "Say 'test' in one word");
                messages.add(message);
                request.put("messages", messages);
                request.put("max_tokens", 5);
                request.put("temperature", 0);

                // Test API
                java.util.Map<String, String> headers = new java.util.HashMap<>();
                headers.put("Authorization", "Bearer " + apiKey);
                bin.mt.json.JSONObject response = bin.mt.plugin.common.HttpUtils.postJson(
                        "https://api.openai.com/v1/chat/completions", headers, request.toString());

                runOnMainThread(loadingDialog::dismiss);

                if (response.contains("choices")) {
                    bin.mt.json.JSONArray choices = response.getJSONArray("choices");
                    if (bin.mt.plugin.common.JSONCompat.size(choices) > 0) {
                    runOnMainThread(() -> pluginUI.buildDialog()
                                .setTitle("✅ API Key Valid")
                                .setMessage("Your OpenAI API key is working correctly!\n\nModel: " + model)
                                .setPositiveButton("{ok}", null)
                        .show());
                    } else {
                    runOnMainThread(() -> pluginUI.buildDialog()
                                .setTitle("⚠️ Warning")
                                .setMessage("API key is valid but received empty response.")
                                .setPositiveButton("{ok}", null)
                        .show());
                    }
                } else if (response.contains("error")) {
                    bin.mt.json.JSONObject error = response.getJSONObject("error");
                    String errorMsg = bin.mt.plugin.common.JSONCompat.optString(error, "message", "Unknown error");
                    String errorType = bin.mt.plugin.common.JSONCompat.optString(error, "type", "");

                    String dialogTitle;
                    String dialogMessage;
                    
                    if (errorType.contains("invalid_api_key")) {
                        dialogTitle = "❌ Authentication Failed";
                        dialogMessage = "Your API key is invalid.\n\nPlease check your API key at:\nplatform.openai.com/api-keys";
                    } else if (errorType.contains("insufficient_quota")) {
                        dialogTitle = "⚠️ Quota Exceeded";
                        dialogMessage = "Your API key is valid but you have exceeded your quota.\n\n" + errorMsg;
                    } else {
                        dialogTitle = "❌ API Error";
                        dialogMessage = "Error Type: " + errorType + "\n\n" + errorMsg;
                    }
                    
                        runOnMainThread(() -> pluginUI.buildDialog()
                            .setTitle(dialogTitle)
                            .setMessage(dialogMessage)
                            .setPositiveButton("{ok}", null)
                            .show());
                } else {
                        runOnMainThread(() -> pluginUI.buildDialog()
                            .setTitle("❌ Unexpected Response")
                            .setMessage("Received unexpected response format from API.")
                            .setPositiveButton("{ok}", null)
                            .show());
                }

            } catch (java.io.IOException e) {
                    runOnMainThread(loadingDialog::dismiss);
                String msg = e.getMessage();
                String dialogTitle;
                String dialogMessage;
                
                if (msg != null && msg.contains("401")) {
                    dialogTitle = "❌ Unauthorized";
                    dialogMessage = "Invalid API key (401 Unauthorized)\n\nPlease verify your API key.";
                } else if (msg != null && msg.contains("429")) {
                    dialogTitle = "⚠️ Rate Limit";
                    dialogMessage = "Rate limit exceeded, but your key is valid!";
                } else {
                    dialogTitle = "❌ Connection Failed";
                    dialogMessage = "Failed to connect to OpenAI API.\n\n" + (msg != null ? msg : "Unknown error");
                }
                
                runOnMainThread(() -> pluginUI.buildDialog()
                        .setTitle(dialogTitle)
                        .setMessage(dialogMessage)
                        .setPositiveButton("{ok}", null)
                        .show());
                        
            } catch (Throwable e) { // Throwable: an Error escaping this thread would crash MT Manager
                runOnMainThread(loadingDialog::dismiss);
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
                GeminiConstants.PREF_CACHE_OPENAI_MODELS,
                ModelCatalogManager.getDefaultSeedOpenAi());

        String effectiveModel = ProviderCatalogRefresher.resolveSelectedModel(
                preferences, GeminiConstants.PREF_OPENAI_MODEL, GeminiConstants.DEFAULT_OPENAI_MODEL);

        StringBuilder message = new StringBuilder();
        message.append(ModelCatalogManager.formatLastRefreshed(
                preferences, GeminiConstants.PREF_CACHE_OPENAI_MODELS)).append("\n\n");
        for (ModelCatalogManager.ModelInfo info : models) {
            message.append(info.id.equals(effectiveModel) ? "▶ " : "• ");
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

    private void refreshOpenAiModels(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        String apiKey = preferences.getString(GeminiConstants.PREF_OPENAI_API_KEY, "");
        if (!ensureValidOpenAiKey(pluginUI, apiKey)) {
            return;
        }
        bin.mt.plugin.api.ui.dialog.LoadingDialog loadingDialog =
                new bin.mt.plugin.api.ui.dialog.LoadingDialog(pluginUI)
                        .setMessage("Fetching OpenAI models...")
                        .setSecondaryMessage("Listing chat.completions capabilities")
                        .show();

        ModelCatalogManager.forceRefresh(
                preferences,
                GeminiConstants.PREF_CACHE_OPENAI_MODELS,
                apiKey,
                ModelCatalogManager.Provider.OPENAI,
                (models, error) -> runOnMainThread(() -> {
                    loadingDialog.dismiss();
                    if (error != null) {
                        pluginUI.buildDialog()
                                .setTitle("❌ Refresh Failed")
                                .setMessage("Could not fetch OpenAI models:\n" + error.getMessage())
                                .setPositiveButton("{ok}", null)
                                .show();
                        return;
                    }
                    if (models == null || models.isEmpty()) {
                        pluginUI.buildDialog()
                                .setTitle("⚠️ No Models Found")
                                .setMessage("Your account did not return any chat-capable models.")
                                .setPositiveButton("{ok}", null)
                                .show();
                        return;
                    }
                    int merged = ProviderCatalogRefresher.composeList(
                            preferences,
                            GeminiConstants.PREF_CACHE_OPENAI_MODELS,
                            ModelCatalogManager.getDefaultSeedOpenAi()).size();
                    context.showToast("✅ " + models.size() + " live models, " + merged
                            + " total in list — reopen to see updates");
                })
        );
    }

    private boolean ensureValidOpenAiKey(bin.mt.plugin.api.ui.PluginUI pluginUI, String apiKey) {
        if (TextUtils.isEmpty(apiKey)) {
            pluginUI.buildDialog()
                    .setTitle("❌ No API Key")
                    .setMessage("Please configure your OpenAI API key first.")
                    .setPositiveButton("{ok}", null)
                    .show();
            return false;
        }
        if (!apiKey.startsWith("sk-")) {
            pluginUI.buildDialog()
                    .setTitle("❌ Invalid Key Format")
                    .setMessage("OpenAI API keys must start with 'sk-'.")
                    .setPositiveButton("{ok}", null)
                    .show();
            return false;
        }
        return true;
    }

    private void runOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }
}
