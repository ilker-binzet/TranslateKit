package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import android.text.InputType;
import android.text.TextUtils;

import java.util.regex.Pattern;

import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.preference.PluginPreference;

/**
 * Preference page dedicated to Anthropic Claude configuration.
 */
public class ClaudeTranslatePreference implements PluginPreference {

    private PluginContext context;

    @Override
    public void onBuild(PluginContext context, Builder builder) {
        this.context = context;
        SharedPreferences preferences = context.getPreferences();

        builder.title(context.getString("{pref_claude_title}"))
                .subtitle(context.getString("{pref_claude_subtitle}"));

        // Overview
        builder.addText("{pref_claude_header_overview}").summary("");
        builder.addText("{pref_claude_overview_title}")
                .summary("{pref_claude_overview_summary}");
        builder.addText("{pref_claude_limits}")
                .summary("{pref_claude_limits_summary}");

        // API key configuration
        builder.addHeader("{pref_claude_header_api}");
        builder.addInput("{pref_claude_api_key_title}", GeminiConstants.PREF_CLAUDE_API_KEY)
                .defaultValue(GeminiConstants.DEFAULT_API_KEY)
                .summary("{pref_claude_api_key_summary}")
                .valueAsSummary()
                .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        builder.addText("{pref_claude_status_title}")
                .summary(describeKeyStatus(
                        preferences.getString(GeminiConstants.PREF_CLAUDE_API_KEY, "")));

        builder.addText("{pref_claude_validate}")
                .summary("{pref_claude_validate_summary}")
                .onClick((pluginUI, item) -> validateApiKey());

        builder.addText("{pref_claude_get_key}")
                .summary("{pref_claude_get_key_summary}")
                .url(GeminiConstants.URL_CLAUDE_KEYS);

        // Model & endpoint selection
        builder.addHeader("{pref_claude_header_model}");

        // The model choice is an implementation detail. The default is the
        // recommended Claude model; the user can override via Custom Model.
        String customClaudeKey = ProviderCatalogRefresher.customPrefKeyFor(GeminiConstants.PREF_CLAUDE_MODEL);
        String effectiveClaudeModel = ProviderCatalogRefresher.resolveSelectedModel(
                preferences, GeminiConstants.PREF_CLAUDE_MODEL, GeminiConstants.DEFAULT_CLAUDE_MODEL);
        boolean isCustomClaude = !TextUtils.isEmpty(preferences.getString(customClaudeKey, ""));

        builder.addText("{pref_claude_model_title}")
                .summary(isCustomClaude
                        ? effectiveClaudeModel + "  (custom override)"
                        : effectiveClaudeModel + "  (default)");

        builder.addText("Model Catalog")
                .summary(ModelCatalogManager.formatLastRefreshed(
                        preferences, GeminiConstants.PREF_CACHE_CLAUDE_MODELS))
                .onClick((pluginUI, item) -> showModelCatalog(pluginUI));

        builder.addText("{pref_claude_model_fallback_note}")
                .summary("{pref_claude_model_fallback_note}");

        builder.addInput("Custom Model (optional)", customClaudeKey)
                .defaultValue("")
                .summary("Overrides the default model above when non-empty. Use for any model name.")
                .hint("e.g. claude-opus-5-latest")
                .valueAsSummary()
                .inputType(InputType.TYPE_CLASS_TEXT);

        builder.addInput("{pref_claude_endpoint_title}", GeminiConstants.PREF_CLAUDE_ENDPOINT)
                .defaultValue(GeminiConstants.DEFAULT_CLAUDE_ENDPOINT)
                .summary("{pref_claude_endpoint_summary}")
                .valueAsSummary();

        ProviderCatalogRefresher.scheduleAutoRefresh(
                preferences,
                GeminiConstants.PREF_CACHE_CLAUDE_MODELS,
                preferences.getString(GeminiConstants.PREF_CLAUDE_API_KEY, ""),
                ModelCatalogManager.Provider.CLAUDE);

        // Helpful resources
        builder.addHeader("{pref_claude_header_docs}");
        builder.addText("{pref_claude_docs}")
                .summary("{pref_claude_docs_summary}")
                .url(GeminiConstants.URL_CLAUDE_DOCS);

        builder.addText("{pref_claude_pricing}")
                .summary("{pref_claude_pricing_summary}")
                .url(GeminiConstants.URL_CLAUDE_PRICING);
    }

    /**
     * Show the merged (cached + curated) model catalog in a dialog.
     * Pure local read — never touches the network, so it opens instantly.
     */
    private void showModelCatalog(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        SharedPreferences preferences = context.getPreferences();
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
               .append("Use 'Custom Model' below to switch.");

        pluginUI.buildDialog()
                .setTitle("📚 Model Catalog (" + models.size() + ")")
                .setMessage(message.toString())
                .setPositiveButton("{ok}", null)
                .show();
    }

    private void validateApiKey() {
        SharedPreferences prefs = context.getPreferences();
        String apiKey = prefs.getString(GeminiConstants.PREF_CLAUDE_API_KEY, "");
        if (apiKey != null) apiKey = apiKey.trim();
        if (apiKey == null || apiKey.isEmpty()) {
            context.showToast(context.getString("{error_claude_no_api_key}"));
            return;
        }

        if (isValidApiKey(apiKey)) {
            context.showToast(context.getString("{msg_claude_key_valid_format}"));
        } else {
            context.showToast(context.getString("{error_claude_invalid_key_format}"));
        }
    }

    private boolean isValidApiKey(String apiKey) {
        return apiKey != null && Pattern.matches(GeminiConstants.CLAUDE_API_KEY_PATTERN, apiKey.trim());
    }

    private String describeKeyStatus(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return context.getString("{pref_status_missing}");
        }
        if (!isValidApiKey(apiKey)) {
            return context.getString("{pref_status_invalid_format}");
        }
        return context.getString("{pref_status_ready}");
    }
}
