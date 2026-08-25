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

        builder.addInput(str("{prov_api_key}"), GeminiConstants.PREF_OPENAI_API_KEY)
                .defaultValue(GeminiConstants.DEFAULT_API_KEY)
                .summary(str("{openai_key_summary}"))
                .valueAsSummary()
                .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        builder.addText(str("{prov_api_key_status}"), KEY_STATUS_ROW)
                .summary(getKeyStatus());

        builder.addText(str("{prov_test_key}"))
                .summary(str("{prov_test_key_summary}"))
                .onClick((pluginUI, item) -> {
                    testApiKey(pluginUI, item);
                });

        builder.addText(str("{prov_get_key}"))
                .summary(str("{openai_get_key_summary}"))
                .url("https://platform.openai.com/api-keys");

        // ==================== Model Selection ====================
        builder.addText(str("{prov_head_model}")).summary("");

        // The model choice is an implementation detail. The default is the
        // recommended OpenAI model; the user can override via Custom Model.
        String customOpenAiKey = ProviderCatalogRefresher.customPrefKeyFor(GeminiConstants.PREF_OPENAI_MODEL);
        String effectiveOpenAiModel = ProviderCatalogRefresher.resolveSelectedModel(
                preferences, GeminiConstants.PREF_OPENAI_MODEL, GeminiConstants.DEFAULT_OPENAI_MODEL);
        boolean isCustomOpenAi = !TextUtils.isEmpty(preferences.getString(customOpenAiKey, ""));

        builder.addText(str("{prov_provider}"))
            .summary("OpenAI");
        builder.addText(str("{prov_model}"))
            .summary(isCustomOpenAi
                    ? effectiveOpenAiModel + str("{prov_custom_override}")
                    : effectiveOpenAiModel + str("{prov_default}"));

        builder.addText(str("{prov_model_catalog}"))
            .summary(ModelCatalogManager.formatLastRefreshed(
                    preferences, GeminiConstants.PREF_CACHE_OPENAI_MODELS))
            .onClick((pluginUI, item) -> showModelCatalog(pluginUI));

        builder.addText(str("{prov_refresh_models}"))
            .summary(str("{openai_refresh_summary}"))
            .onClick((pluginUI, item) -> refreshOpenAiModels(pluginUI));

        builder.addInput(str("{prov_custom_model}"), customOpenAiKey)
                .defaultValue("")
                .summary(str("{prov_custom_model_summary}"))
                .hint(str("{openai_model_hint}"))
                .valueAsSummary()
                .inputType(InputType.TYPE_CLASS_TEXT);

        ProviderCatalogRefresher.scheduleAutoRefresh(
                preferences,
                GeminiConstants.PREF_CACHE_OPENAI_MODELS,
                preferences.getString(GeminiConstants.PREF_OPENAI_API_KEY, ""),
                ModelCatalogManager.Provider.OPENAI);

        // ==================== Usage & Limits ====================
        builder.addText(str("{prov_head_usage}"))
                .summary("");

        builder.addText(str("{prov_pricing}"))
                .summary(str("{openai_pricing}"));

        builder.addText(str("{prov_api_docs}"))
                .summary(str("{openai_docs_summary}"))
                .url("https://platform.openai.com/docs/overview");

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
            if (GeminiConstants.PREF_OPENAI_API_KEY.equals(key)) {
                context.showToast(str("{prov_key_updated}"));
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
            return "🟢 Ready - Click 'Test API Key' to verify connectivity";
        }
    }

    private void runQuickTranslationTest(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        SharedPreferences prefs = context.getPreferences();
        String apiKey = prefs.getString(GeminiConstants.PREF_OPENAI_API_KEY, "");
        String model = prefs.getString(GeminiConstants.PREF_OPENAI_MODEL, GeminiConstants.DEFAULT_OPENAI_MODEL);

        if (apiKey.isEmpty()) {
            pluginUI.buildDialog()
                    .setTitle(str("{prov_no_key}"))
                    .setMessage(str("{openai_need_key}"))
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
                message.put("content", str("{prov_test_prompt}"));
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
        String apiKey = prefs.getString(GeminiConstants.PREF_OPENAI_API_KEY, "");
        String model = prefs.getString(GeminiConstants.PREF_OPENAI_MODEL, GeminiConstants.DEFAULT_OPENAI_MODEL);

        if (apiKey.isEmpty()) {
            pluginUI.buildDialog()
                    .setTitle(str("{prov_no_key}"))
                    .setMessage(str("{openai_need_key}"))
                    .setPositiveButton("{ok}", null)
                    .show();
            return;
        }

        if (!apiKey.startsWith("sk-")) {
            pluginUI.buildDialog()
                    .setTitle(str("{prov_bad_key_format}"))
                    .setMessage("OpenAI API keys must start with 'sk-'")
                    .setPositiveButton("{ok}", null)
                    .show();
            return;
        }

        // Show progress dialog
        bin.mt.plugin.api.ui.dialog.LoadingDialog loadingDialog = 
            new bin.mt.plugin.api.ui.dialog.LoadingDialog(pluginUI)
                .setMessage(str("{prov_testing_connection}"))
                .setSecondaryMessage(str("{prov_verifying}"))
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
                                .setTitle(GeminiColorTokens.success(pluginUI, str("{prov_key_valid}")))
                                .setMessage("Your OpenAI API key is working correctly!\n\nModel: " + model)
                                .setPositiveButton("{ok}", null)
                        .show());
                    } else {
                    runOnMainThread(() -> pluginUI.buildDialog()
                                .setTitle(str("{prov_warning}"))
                                .setMessage(str("{prov_empty_response}"))
                                .setPositiveButton("{ok}", null)
                        .show());
                    }
                } else if (response.contains("error")) {
                    bin.mt.json.JSONObject error = response.getJSONObject("error");
                    String errorMsg = bin.mt.plugin.common.JSONCompat.optString(error, "message", str("{prov_unknown_error}"));
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
                        dialogTitle = str("{prov_api_error}");
                        dialogMessage = "Error Type: " + errorType + "\n\n" + errorMsg;
                    }
                    
                        runOnMainThread(() -> pluginUI.buildDialog()
                            .setTitle(dialogTitle)
                            .setMessage(dialogMessage)
                            .setPositiveButton("{ok}", null)
                            .show());
                } else {
                        runOnMainThread(() -> pluginUI.buildDialog()
                            .setTitle(str("{prov_unexpected}"))
                            .setMessage(str("{prov_bad_response}"))
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
                    dialogMessage = str("{prov_rate_limited}");
                } else {
                    dialogTitle = "❌ Connection Failed";
                    dialogMessage = "Failed to connect to OpenAI API.\n\n" + (msg != null ? msg : str("{prov_unknown_error}"));
                }
                
                runOnMainThread(() -> pluginUI.buildDialog()
                        .setTitle(dialogTitle)
                        .setMessage(dialogMessage)
                        .setPositiveButton("{ok}", null)
                        .show());
                        
            } catch (Throwable e) { // Throwable: an Error escaping this thread would crash MT Manager
                runOnMainThread(loadingDialog::dismiss);
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
                                .setTitle(str("{prov_refresh_failed}"))
                                .setMessage("Could not fetch OpenAI models:\n" + error.getMessage())
                                .setPositiveButton("{ok}", null)
                                .show();
                        return;
                    }
                    if (models == null || models.isEmpty()) {
                        pluginUI.buildDialog()
                                .setTitle(str("{prov_no_models}"))
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
                    .setTitle(str("{prov_no_key}"))
                    .setMessage(str("{openai_need_key}"))
                    .setPositiveButton("{ok}", null)
                    .show();
            return false;
        }
        if (!apiKey.startsWith("sk-")) {
            pluginUI.buildDialog()
                    .setTitle(str("{prov_bad_key_format}"))
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
