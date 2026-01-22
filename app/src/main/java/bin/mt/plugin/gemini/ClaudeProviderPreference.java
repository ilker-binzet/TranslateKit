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
 * Claude AI Provider Settings
 * Dedicated settings page for Anthropic Claude configuration
 * 
 * @author Ilker Binzet
 * @version 0.7.0-MODERN
 */
public class ClaudeProviderPreference implements PluginPreference {

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
        builder.addHeader("🤖 Model Selection");

        var claudeModelList = builder.addList("Claude Model", GeminiConstants.PREF_CLAUDE_MODEL)
            .defaultValue(GeminiConstants.DEFAULT_CLAUDE_MODEL)
            .summary("Choose Claude model (3.5 Sonnet recommended)");

        boolean disableCache = preferences.getBoolean(GeminiConstants.PREF_DEBUG_DISABLE_MODEL_CACHE, false);
        java.util.List<ModelCatalogManager.ModelInfo> cachedClaudeModels = disableCache
            ? Collections.emptyList()
            : ModelCatalogManager.loadModelCache(preferences, GeminiConstants.PREF_CACHE_CLAUDE_MODELS);
        if (cachedClaudeModels == null || cachedClaudeModels.isEmpty()) {
            claudeModelList.addItem("Claude Sonnet 4.5 (Latest, Recommended)", "claude-sonnet-4-5-20250514")
                    .addItem("Claude 3.5 Sonnet", "claude-3-5-sonnet-20241022")
                    .addItem("Claude 3.5 Haiku (Faster)", "claude-3-5-haiku-20241022")
                    .addItem("Claude 3 Opus (Highest Quality)", "claude-3-opus-20240229");
        } else {
            for (ModelCatalogManager.ModelInfo info : cachedClaudeModels) {
                claudeModelList.addItem(formatModelLabel(info), info.id);
            }
        }

        builder.addText("Refresh Model List")
            .summary("Pull current Claude 3.x/3.5 availability")
            .onClick((pluginUI, item) -> refreshClaudeModels(pluginUI));

        // ==================== Usage & Limits ====================
        builder.addHeader("📊 Usage & Limits");

        builder.addText("Pricing Information")
                .summary("Sonnet: $3/1M input tokens | Haiku: $0.25/1M tokens - Pay as you go");

        builder.addText("API Documentation")
                .summary("View Claude API documentation and pricing")
                .url("https://docs.anthropic.com/");

        // ==================== Test & Debug ====================
        builder.addHeader("🔧 Test & Debug");

        builder.addText("Quick Test")
                .summary("Test translation with a simple phrase")
                .onClick((pluginUI, item) -> runQuickTranslationTest(pluginUI));

        builder.addText("View Logs")
                .summary("Open MT Manager log viewer")
                .onClick((pluginUI, item) -> context.openLogViewer());

        // ==================== SDK Beta2: Preference Callbacks ====================
        // onPreferenceChange: React to model changes in real-time
        builder.onPreferenceChange((pluginUI, preferenceItem, newValue) -> {
            String key = preferenceItem.getKey();
            if (GeminiConstants.PREF_CLAUDE_MODEL.equals(key)) {
                String modelName = (String) newValue;
                pluginUI.showToast("Claude model: " + modelName);
            }
        });

        // onCreated: Log screen initialization for debugging
        builder.onCreated((pluginUI, preferenceScreen) -> {
            // Provider preference screen initialized
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
                org.json.JSONObject request = new org.json.JSONObject();
                request.put("model", model);
                request.put("max_tokens", 50);
                org.json.JSONArray messages = new org.json.JSONArray();
                org.json.JSONObject message = new org.json.JSONObject();
                message.put("role", "user");
                message.put("content", "Translate to Turkish: Hello");
                messages.put(message);
                request.put("messages", messages);

                GeminiHttpUtils.Request httpRequest = GeminiHttpUtils.post("https://api.anthropic.com/v1/messages");
                httpRequest.setTimeout(10000);
                httpRequest.header("x-api-key", apiKey);
                httpRequest.header("anthropic-version", "2023-06-01");
                httpRequest.jsonBody(request);
                org.json.JSONObject response = httpRequest.executeToJson();

                runOnMainThread(loadingDialog::dismiss);

                if (response.has("content")) {
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
            } catch (Exception e) {
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
                org.json.JSONObject request = new org.json.JSONObject();
                request.put("model", model);
                request.put("max_tokens", 5);
                
                org.json.JSONArray messages = new org.json.JSONArray();
                org.json.JSONObject message = new org.json.JSONObject();
                message.put("role", "user");
                message.put("content", "Say 'test' in one word");
                messages.put(message);
                request.put("messages", messages);

                // Test API
                GeminiHttpUtils.Request httpRequest = GeminiHttpUtils.post("https://api.anthropic.com/v1/messages");
                httpRequest.setTimeout(15000);
                httpRequest.header("x-api-key", apiKey);
                httpRequest.header("anthropic-version", "2023-06-01");
                httpRequest.jsonBody(request);

                org.json.JSONObject response = httpRequest.executeToJson();

                runOnMainThread(loadingDialog::dismiss);

                if (response.has("content")) {
                    org.json.JSONArray content = response.getJSONArray("content");
                    if (content.length() > 0) {
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
                } else if (response.has("error")) {
                    org.json.JSONObject error = response.getJSONObject("error");
                    String errorType = error.optString("type", "");
                    String errorMsg = error.optString("message", "Unknown error");
                    
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
                        
            } catch (Exception e) {
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
            org.json.JSONObject errorPayload = parseErrorPayload(rawMessage);
            if (errorPayload != null) {
                org.json.JSONObject errorObject = errorPayload.optJSONObject("error");
                String errorType = errorObject != null ? errorObject.optString("type") : errorPayload.optString("type");
                String apiMessage = errorObject != null ? errorObject.optString("message") : errorPayload.optString("message");

                if ("not_found_error".equalsIgnoreCase(errorType) || (apiMessage != null && apiMessage.contains("model"))) {
                    runOnMainThread(() -> showModelNotFoundDialog(pluginUI, model, apiMessage));
                    return true;
                }
            }
        }

        return false;
    }

    private org.json.JSONObject parseErrorPayload(String rawMessage) {
        int jsonStart = rawMessage.indexOf('{');
        if (jsonStart == -1) {
            return null;
        }
        try {
            return new org.json.JSONObject(rawMessage.substring(jsonStart));
        } catch (org.json.JSONException ignored) {
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

        new Thread(() -> {
            try {
                java.util.List<ModelCatalogManager.ModelInfo> models = ModelCatalogManager.fetchClaudeModels(apiKey);
                ModelCatalogManager.saveModelCache(preferences, GeminiConstants.PREF_CACHE_CLAUDE_MODELS, models);
                runOnMainThread(() -> {
                    loadingDialog.dismiss();
                    if (models == null || models.isEmpty()) {
                        pluginUI.buildDialog()
                                .setTitle("⚠️ No Models Found")
                                .setMessage("Anthropic returned no Claude 3.x models for this key.")
                                .setPositiveButton("{ok}", null)
                                .show();
                    } else {
                        showModelSelectionDialog(pluginUI, "Select Claude Model", models,
                                GeminiConstants.PREF_CLAUDE_MODEL, GeminiConstants.DEFAULT_CLAUDE_MODEL,
                                "Claude model switched to ");
                    }
                });
            } catch (IOException e) {
                runOnMainThread(() -> {
                    loadingDialog.dismiss();
                    pluginUI.buildDialog()
                            .setTitle("❌ Refresh Failed")
                            .setMessage("Could not fetch Claude models:\n" + e.getMessage())
                            .setPositiveButton("{ok}", null)
                            .show();
                });
            }
        }).start();
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

    private void showModelSelectionDialog(bin.mt.plugin.api.ui.PluginUI pluginUI,
                                          String title,
                                          java.util.List<ModelCatalogManager.ModelInfo> models,
                                          String prefKey,
                                          String defaultValue,
                                          String toastPrefix) {
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
                        context.showToast(toastPrefix + selected.displayName);
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
}
