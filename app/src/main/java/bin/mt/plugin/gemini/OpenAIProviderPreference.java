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
 * OpenAI GPT Provider Settings
 * Dedicated settings page for OpenAI GPT configuration
 * 
 * @author Ilker Binzet
 * @version 0.7.0-MODERN
 */
public class OpenAIProviderPreference implements PluginPreference {

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
        builder.addHeader("🧠 Model Selection");

        var openAiModelList = builder.addList("GPT Model", GeminiConstants.PREF_OPENAI_MODEL)
            .defaultValue(GeminiConstants.DEFAULT_OPENAI_MODEL)
            .summary("Choose GPT model (GPT-4o recommended)");

        boolean disableCache = preferences.getBoolean(GeminiConstants.PREF_DEBUG_DISABLE_MODEL_CACHE, false);
        java.util.List<ModelCatalogManager.ModelInfo> cachedOpenAiModels = disableCache
            ? Collections.emptyList()
            : ModelCatalogManager.loadModelCache(preferences, GeminiConstants.PREF_CACHE_OPENAI_MODELS);
        if (cachedOpenAiModels == null || cachedOpenAiModels.isEmpty()) {
            openAiModelList.addItem("GPT-4o (Recommended)", "gpt-4o")
                    .addItem("GPT-4o Mini (Fast)", "gpt-4o-mini")
                    .addItem("o3-mini (Reasoning)", "o3-mini")
                    .addItem("o1-mini (Advanced)", "o1-mini");
        } else {
            for (ModelCatalogManager.ModelInfo info : cachedOpenAiModels) {
                openAiModelList.addItem(formatModelLabel(info), info.id);
            }
        }

        builder.addText("Refresh Model List")
            .summary("Fetch all available Chat Completions models")
            .onClick((pluginUI, item) -> refreshOpenAiModels(pluginUI));

        // ==================== Usage & Limits ====================
        builder.addHeader("📊 Usage & Limits");

        builder.addText("Pricing Information")
                .summary("GPT-4o: $2.50/1M input tokens | GPT-4o Mini: $0.15/1M tokens - Pay as you go");

        builder.addText("API Documentation")
                .summary("View OpenAI API documentation and pricing")
                .url("https://platform.openai.com/docs/overview");

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
                org.json.JSONArray messages = new org.json.JSONArray();
                org.json.JSONObject message = new org.json.JSONObject();
                message.put("role", "user");
                message.put("content", "Translate to Turkish: Hello");
                messages.put(message);
                request.put("messages", messages);
                request.put("max_tokens", 50);

                GeminiHttpUtils.Request httpRequest = GeminiHttpUtils.post("https://api.openai.com/v1/chat/completions");
                httpRequest.setTimeout(10000);
                httpRequest.header("Authorization", "Bearer " + apiKey);
                httpRequest.jsonBody(request);
                org.json.JSONObject response = httpRequest.executeToJson();

                runOnMainThread(loadingDialog::dismiss);

                if (response.has("choices")) {
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
                org.json.JSONObject request = new org.json.JSONObject();
                request.put("model", model);
                
                org.json.JSONArray messages = new org.json.JSONArray();
                org.json.JSONObject message = new org.json.JSONObject();
                message.put("role", "user");
                message.put("content", "Say 'test' in one word");
                messages.put(message);
                request.put("messages", messages);
                request.put("max_tokens", 5);
                request.put("temperature", 0);

                // Test API
                GeminiHttpUtils.Request httpRequest = GeminiHttpUtils.post("https://api.openai.com/v1/chat/completions");
                httpRequest.setTimeout(15000);
                httpRequest.header("Authorization", "Bearer " + apiKey);
                httpRequest.jsonBody(request);

                org.json.JSONObject response = httpRequest.executeToJson();

                runOnMainThread(loadingDialog::dismiss);

                if (response.has("choices")) {
                    org.json.JSONArray choices = response.getJSONArray("choices");
                    if (choices.length() > 0) {
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
                } else if (response.has("error")) {
                    org.json.JSONObject error = response.getJSONObject("error");
                    String errorMsg = error.optString("message", "Unknown error");
                    String errorType = error.optString("type", "");
                    
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

        new Thread(() -> {
            try {
                java.util.List<ModelCatalogManager.ModelInfo> models = ModelCatalogManager.fetchOpenAiModels(apiKey);
                ModelCatalogManager.saveModelCache(preferences, GeminiConstants.PREF_CACHE_OPENAI_MODELS, models);
                runOnMainThread(() -> {
                    loadingDialog.dismiss();
                    if (models == null || models.isEmpty()) {
                        pluginUI.buildDialog()
                                .setTitle("⚠️ No Models Found")
                                .setMessage("Your account did not return any chat-capable models.")
                                .setPositiveButton("{ok}", null)
                                .show();
                    } else {
                        showModelSelectionDialog(pluginUI, "Select OpenAI Model", models,
                                GeminiConstants.PREF_OPENAI_MODEL, GeminiConstants.DEFAULT_OPENAI_MODEL,
                                "OpenAI model switched to ");
                    }
                });
            } catch (IOException e) {
                runOnMainThread(() -> {
                    loadingDialog.dismiss();
                    pluginUI.buildDialog()
                            .setTitle("❌ Refresh Failed")
                            .setMessage("Could not fetch OpenAI models:\n" + e.getMessage())
                            .setPositiveButton("{ok}", null)
                            .show();
                });
            }
        }).start();
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

    private void runOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }
}
