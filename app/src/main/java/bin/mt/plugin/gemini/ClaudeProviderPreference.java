package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;

import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.preference.PluginPreference;

/**
 * Claude AI Provider Settings
 * Dedicated settings page for Anthropic Claude configuration
 *
 * @author Ilker Binzet
 * @version 0.4.0-beta - Auto-refreshing model catalog + custom model override
 */
public class ClaudeProviderPreference implements PluginPreference {

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

        builder.addInput("API Key", GeminiConstants.PREF_CLAUDE_API_KEY)
                .defaultValue(GeminiConstants.DEFAULT_API_KEY)
                .summary("Get your API key at console.anthropic.com/settings/keys")
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
                .summary("Open Anthropic Console to manage API keys")
                .url("https://console.anthropic.com/settings/keys");

        // ==================== Model Selection ====================
        builder.addText("🤖 Model Selection").summary("");

        // The model choice is an implementation detail. The default is the
        // recommended Claude model; the user can override via Custom Model.
        String customClaudeKey = ProviderCatalogRefresher.customPrefKeyFor(GeminiConstants.PREF_CLAUDE_MODEL);
        String effectiveClaudeModel = ProviderCatalogRefresher.resolveSelectedModel(
                preferences, GeminiConstants.PREF_CLAUDE_MODEL, GeminiConstants.DEFAULT_CLAUDE_MODEL);
        boolean isCustomClaude = !TextUtils.isEmpty(preferences.getString(customClaudeKey, ""));

        builder.addText("Provider")
            .summary("Claude");
        builder.addText("Model")
            .summary(isCustomClaude
                    ? effectiveClaudeModel + "  (custom override)"
                    : effectiveClaudeModel + "  (default)");

        builder.addText("Model Catalog")
            .summary(ModelCatalogManager.formatLastRefreshed(
                    preferences, GeminiConstants.PREF_CACHE_CLAUDE_MODELS))
            .onClick((pluginUI, item) -> showModelCatalog(pluginUI));

        builder.addText("Refresh Model List")
            .summary("Fetch the latest Claude models from Anthropic API")
            .onClick((pluginUI, item) -> refreshClaudeModels(pluginUI));

        builder.addInput("Custom Model (optional)", customClaudeKey)
                .defaultValue("")
                .summary("Overrides the default model above when non-empty. Use for any model name.")
                .hint("e.g. claude-opus-5-latest")
                .valueAsSummary()
                .inputType(InputType.TYPE_CLASS_TEXT);

        ProviderCatalogRefresher.scheduleAutoRefresh(
                preferences,
                GeminiConstants.PREF_CACHE_CLAUDE_MODELS,
                preferences.getString(GeminiConstants.PREF_CLAUDE_API_KEY, ""),
                ModelCatalogManager.Provider.CLAUDE);

        // ==================== Usage & Limits ====================
        builder.addText("📊 Usage & Limits")
                .summary("");

        builder.addText("Pricing Information")
                .summary("Sonnet: $3/1M input tokens | Haiku: $0.25/1M tokens - Pay as you go");

        builder.addText("API Documentation")
                .summary("View Claude API documentation and pricing")
                .url("https://docs.anthropic.com/");

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
            if (GeminiConstants.PREF_CLAUDE_API_KEY.equals(key)) {
                context.showToast("API key updated. Re-open settings to refresh status.");
            }
        });
    }

    private String getKeyStatus() {
        String apiKey = preferences.getString(GeminiConstants.PREF_CLAUDE_API_KEY, "");
        
        if (apiKey.isEmpty()) {
            return "⚪ Not Configured - Click 'Get API Key' above";
        } else if (!apiKey.startsWith("sk-ant-")) {
            return "🔴 Invalid Format - Claude keys start with 'sk-ant-'";
        } else {
            return "🟡 Ready - Click 'Test API Key' to verify connectivity";
        }
    }

    private void runQuickTranslationTest(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        SharedPreferences prefs = context.getPreferences();
        String apiKey = prefs.getString(GeminiConstants.PREF_CLAUDE_API_KEY, "");
        String model = prefs.getString(GeminiConstants.PREF_CLAUDE_MODEL, GeminiConstants.DEFAULT_CLAUDE_MODEL);

        if (apiKey.isEmpty()) {
            pluginUI.buildDialog()
                    .setTitle("❌ No API Key")
                    .setMessage("Please configure your Claude API key first.")
                    .setPositiveButton("{ok}", null)
                    .show();
            return;
        }

        // Show progress dialog
        bin.mt.plugin.api.ui.dialog.LoadingDialog loadingDialog = 
            new bin.mt.plugin.api.ui.dialog.LoadingDialog(pluginUI)
                .setMessage("Translating...")
                .setSecondaryMessage("Testing: 'Hello' → Turkish")
                .show();
        
        new Thread(() -> {
            try {
                bin.mt.json.JSONObject request = new bin.mt.json.JSONObject();
                request.put("model", model);
                request.put("max_tokens", 50);
                bin.mt.json.JSONArray messages = new bin.mt.json.JSONArray();
                bin.mt.json.JSONObject message = new bin.mt.json.JSONObject();
                message.put("role", "user");
                message.put("content", "Translate to Turkish: Hello");
                messages.add(message);
                request.put("messages", messages);

                java.util.Map<String, String> headers = new java.util.HashMap<>();
                headers.put("x-api-key", apiKey);
                headers.put("anthropic-version", "2023-06-01");
                bin.mt.json.JSONObject response = bin.mt.plugin.common.HttpUtils.postJson(
                        "https://api.anthropic.com/v1/messages", headers, request.toString());

                runOnMainThread(loadingDialog::dismiss);

                if (response.contains("content")) {
                    String result = response.getJSONArray("content")
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
            } catch (java.io.IOException e) {
                runOnMainThread(loadingDialog::dismiss);
                if (handleKnownClaudeErrors(e.getMessage(), pluginUI, model)) {
                    return;
                }
                runOnMainThread(() -> pluginUI.buildDialog()
                        .setTitle("❌ Test Failed")
                        .setMessage("Error: " + e.getMessage())
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

    private void testApiKey(bin.mt.plugin.api.ui.PluginUI pluginUI, Object item) {
        SharedPreferences prefs = context.getPreferences();
        String apiKey = prefs.getString(GeminiConstants.PREF_CLAUDE_API_KEY, "");
        String model = prefs.getString(GeminiConstants.PREF_CLAUDE_MODEL, GeminiConstants.DEFAULT_CLAUDE_MODEL);

        if (apiKey.isEmpty()) {
            pluginUI.buildDialog()
                    .setTitle("❌ No API Key")
                    .setMessage("Please configure your Claude API key first.")
                    .setPositiveButton("{ok}", null)
                    .show();
            return;
        }

        if (!apiKey.startsWith("sk-ant-")) {
            pluginUI.buildDialog()
                    .setTitle("❌ Invalid Key Format")
                    .setMessage("Claude API keys must start with 'sk-ant-'")
                    .setPositiveButton("{ok}", null)
                    .show();
            return;
        }

        // Show progress dialog with modern UI
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
                request.put("max_tokens", 5);

                bin.mt.json.JSONArray messages = new bin.mt.json.JSONArray();
                bin.mt.json.JSONObject message = new bin.mt.json.JSONObject();
                message.put("role", "user");
                message.put("content", "Say 'test' in one word");
                messages.add(message);
                request.put("messages", messages);

                // Test API
                java.util.Map<String, String> headers = new java.util.HashMap<>();
                headers.put("x-api-key", apiKey);
                headers.put("anthropic-version", "2023-06-01");
                bin.mt.json.JSONObject response = bin.mt.plugin.common.HttpUtils.postJson(
                        "https://api.anthropic.com/v1/messages", headers, request.toString());

                runOnMainThread(loadingDialog::dismiss);

                if (response.contains("content")) {
                    bin.mt.json.JSONArray content = response.getJSONArray("content");
                    if (bin.mt.plugin.common.JSONCompat.size(content) > 0) {
                    runOnMainThread(() -> pluginUI.buildDialog()
                                .setTitle("✅ API Key Valid")
                                .setMessage("Your Claude API key is working correctly!\n\nModel: " + model)
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
                    String errorType = bin.mt.plugin.common.JSONCompat.optString(error, "type", "");
                    String errorMsg = bin.mt.plugin.common.JSONCompat.optString(error, "message", "Unknown error");

                    String dialogTitle;
                    String dialogMessage;

                    if (errorType.contains("authentication_error")) {
                        dialogTitle = "❌ Authentication Failed";
                        dialogMessage = "Your API key is invalid.\n\nPlease check your API key at:\nconsole.anthropic.com/settings/keys";
                    } else if (errorType.contains("permission_error")) {
                        dialogTitle = "❌ Permission Denied";
                        dialogMessage = "Your API key lacks necessary permissions.\n\nError: " + errorMsg;
                    } else if (errorType.contains("rate_limit_error")) {
                        dialogTitle = "⚠️ Rate Limit";
                        dialogMessage = "Rate limit exceeded, but your key is valid!\n\n" + errorMsg;
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

                if (handleKnownClaudeErrors(msg, pluginUI, model)) {
                    return;
                }

                String dialogTitle;
                String dialogMessage;
                
                if (msg != null && msg.contains("401")) {
                    dialogTitle = "❌ Unauthorized";
                    dialogMessage = "Invalid API key (401 Unauthorized)\n\nPlease verify your API key.";
                } else if (msg != null && msg.contains("403")) {
                    dialogTitle = "❌ Forbidden";
                    dialogMessage = "Access forbidden - check API key permissions.";
                } else if (msg != null && msg.contains("429")) {
                    dialogTitle = "⚠️ Rate Limit";
                    dialogMessage = "Rate limit exceeded, but your key is valid!";
                } else {
                    dialogTitle = "❌ Connection Failed";
                    dialogMessage = "Failed to connect to Claude API.\n\n" + (msg != null ? msg : "Unknown error");
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

    private boolean handleKnownClaudeErrors(String rawMessage, bin.mt.plugin.api.ui.PluginUI pluginUI, String model) {
        if (rawMessage == null) {
            return false;
        }

        if (rawMessage.contains("404")) {
            bin.mt.json.JSONObject errorPayload = parseErrorPayload(rawMessage);
            if (errorPayload != null) {
                bin.mt.json.JSONObject errorObject = bin.mt.plugin.common.JSONCompat.optJSONObject(errorPayload, "error");
                String errorType = errorObject != null
                        ? bin.mt.plugin.common.JSONCompat.optString(errorObject, "type", "")
                        : bin.mt.plugin.common.JSONCompat.optString(errorPayload, "type", "");
                String apiMessage = errorObject != null
                        ? bin.mt.plugin.common.JSONCompat.optString(errorObject, "message", "")
                        : bin.mt.plugin.common.JSONCompat.optString(errorPayload, "message", "");

                if ("not_found_error".equalsIgnoreCase(errorType) || (apiMessage != null && apiMessage.contains("model"))) {
                    runOnMainThread(() -> showModelNotFoundDialog(pluginUI, model, apiMessage));
                    return true;
                }
            }
        }

        return false;
    }

    private bin.mt.json.JSONObject parseErrorPayload(String rawMessage) {
        int jsonStart = rawMessage.indexOf('{');
        if (jsonStart == -1) {
            return null;
        }
        try {
            return new bin.mt.json.JSONObject(rawMessage.substring(jsonStart));
        } catch (Exception ignored) {
            return null;
        }
    }

    private void showModelNotFoundDialog(bin.mt.plugin.api.ui.PluginUI pluginUI, String currentModel, String apiMessage) {
        final String fallbackModel = GeminiConstants.CLAUDE_MODEL_FALLBACK;
        StringBuilder message = new StringBuilder()
            .append("Anthropic can't find model '")
                .append(currentModel)
                .append("'.\n\nThis usually means your account doesn't have access yet.\n\n")
                .append("Tap 'Switch Model' to use the stable ")
                .append(fallbackModel)
                .append(" model instead, or pick another option under 'Model Selection'.");

        if (apiMessage != null && !apiMessage.isEmpty()) {
            message.append("\n\nAPI message: ").append(apiMessage);
        }

        pluginUI.buildDialog()
                .setTitle("❌ Model Not Available")
                .setMessage(message.toString())
                .setPositiveButton("Switch Model", (dialog, which) -> {
                    preferences.edit().putString(GeminiConstants.PREF_CLAUDE_MODEL, fallbackModel).apply();
                    pluginUI.buildDialog()
                            .setTitle("✅ Model Updated")
                            .setMessage("Claude model switched to " + fallbackModel + ". Please retry the test.")
                            .setPositiveButton("{ok}", null)
                            .show();
                })
                .setNegativeButton("{cancel}", null)
                .show();
    }

    private void runOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }

    /**
     * Show the merged (cached + curated) model catalog in a dialog.
     * Pure local read — never touches the network, so it opens instantly.
     */
    private void showModelCatalog(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        java.util.List<ModelCatalogManager.ModelInfo> models = ProviderCatalogRefresher.composeList(
                preferences,
                GeminiConstants.PREF_CACHE_CLAUDE_MODELS,
                ModelCatalogManager.getDefaultSeedClaude());

        String effectiveModel = ProviderCatalogRefresher.resolveSelectedModel(
                preferences, GeminiConstants.PREF_CLAUDE_MODEL, GeminiConstants.DEFAULT_CLAUDE_MODEL);

        StringBuilder message = new StringBuilder();
        message.append(ModelCatalogManager.formatLastRefreshed(
                preferences, GeminiConstants.PREF_CACHE_CLAUDE_MODELS)).append("\n\n");
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

    private void refreshClaudeModels(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        String apiKey = preferences.getString(GeminiConstants.PREF_CLAUDE_API_KEY, "");
        if (!ensureValidClaudeKey(pluginUI, apiKey)) {
            return;
        }
        bin.mt.plugin.api.ui.dialog.LoadingDialog loadingDialog =
                new bin.mt.plugin.api.ui.dialog.LoadingDialog(pluginUI)
                        .setMessage("Fetching Claude models...")
                        .setSecondaryMessage("Checking Anthropic availability")
                        .show();

        ModelCatalogManager.forceRefresh(
                preferences,
                GeminiConstants.PREF_CACHE_CLAUDE_MODELS,
                apiKey,
                ModelCatalogManager.Provider.CLAUDE,
                (models, error) -> runOnMainThread(() -> {
                    loadingDialog.dismiss();
                    if (error != null) {
                        pluginUI.buildDialog()
                                .setTitle("❌ Refresh Failed")
                                .setMessage("Could not fetch Claude models:\n" + error.getMessage())
                                .setPositiveButton("{ok}", null)
                                .show();
                        return;
                    }
                    if (models == null || models.isEmpty()) {
                        pluginUI.buildDialog()
                                .setTitle("⚠️ No Models Found")
                                .setMessage("Anthropic returned no Claude models for this key.")
                                .setPositiveButton("{ok}", null)
                                .show();
                        return;
                    }
                    int merged = ProviderCatalogRefresher.composeList(
                            preferences,
                            GeminiConstants.PREF_CACHE_CLAUDE_MODELS,
                            ModelCatalogManager.getDefaultSeedClaude()).size();
                    context.showToast("✅ " + models.size() + " live models, " + merged
                            + " total in list — reopen to see updates");
                })
        );
    }

    private boolean ensureValidClaudeKey(bin.mt.plugin.api.ui.PluginUI pluginUI, String apiKey) {
        if (TextUtils.isEmpty(apiKey)) {
            pluginUI.buildDialog()
                    .setTitle("❌ No API Key")
                    .setMessage("Please configure your Claude API key first.")
                    .setPositiveButton("{ok}", null)
                    .show();
            return false;
        }
        if (!apiKey.startsWith("sk-ant-")) {
            pluginUI.buildDialog()
                    .setTitle("❌ Invalid Key Format")
                    .setMessage("Claude API keys must start with 'sk-ant-'.")
                    .setPositiveButton("{ok}", null)
                    .show();
            return false;
        }
        return true;
    }
}
