package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;

import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.preference.PluginPreference;
import bin.mt.plugin.provider.Provider;
import bin.mt.plugin.provider.Providers;

/**
 * Main preference screen for TranslateKit.
 * Clean 5-category navigation with sub-screens for each section.
 *
 * @author Ilker Binzet
 * @version 0.8.0
 */
public class GeminiTranslatePreference implements PluginPreference {

    private PluginContext context;
    private SharedPreferences preferences;
    private final Map<String, ProviderStatus> providerStatusCache = new HashMap<>();
    private boolean preferenceListenerRegistered;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener = (prefs, key) -> {
        // Editing the custom list can add, rename or remove several entries at
        // once, so there is no single cache row to invalidate.
        if (Providers.PREF_CUSTOM_PROVIDERS.equals(key)) {
            synchronized (providerStatusCache) {
                providerStatusCache.clear();
            }
            return;
        }
        String providerKey = mapPreferenceToProviderKey(key);
        if (providerKey != null) {
            synchronized (providerStatusCache) {
                providerStatusCache.remove(providerKey);
            }
        }
    };

    private static class ProviderStatus {
        final String displayName;
        final String icon;
        final String title;
        final String detail;

        ProviderStatus(String displayName, String icon, String title, String detail) {
            this.displayName = displayName;
            this.icon = icon;
            this.title = title;
            this.detail = detail;
        }
    }

    @Override
    public void onBuild(PluginContext context, Builder builder) {
        this.context = context;
        this.preferences = context.getPreferences();
        synchronized (providerStatusCache) {
            providerStatusCache.clear();
        }
        ensurePreferenceListenerRegistered();

        // ==================== 1. AI Providers ====================
        builder.addText("AI Providers")
                .summary(buildProvidersSummary())
                .onClick((pluginUI, item) -> showProvidersMenu(pluginUI));

        // ==================== 2. Translation Settings ====================
        builder.addText("Translation Settings")
                .summary(buildTranslationSummary())
                .onClick((pluginUI, item) -> context.openPreference(TranslationSubPreference.class));

        // ==================== 3. Context & Tone ====================
        builder.addText("Context & Tone")
                .summary(buildContextSummary())
                .onClick((pluginUI, item) -> context.openPreference(ContextToneSubPreference.class));

        // ==================== 4. Tools & Diagnostics ====================
        builder.addText("Tools & Diagnostics")
                .summary("Provider status, tests, logs")
                .onClick((pluginUI, item) -> context.openPreference(ToolsSubPreference.class));

        // ==================== 5. About ====================
        builder.addText("About")
                .summary("TranslateKit v" + GeminiConstants.PLUGIN_VERSION_NAME + " • by Ilker Binzet")
                .url(GeminiConstants.DEVELOPER_GITHUB);
    }

    // ==================== Summary Builders ====================

    private String buildProvidersSummary() {
        java.util.List<Provider> all = Providers.all(preferences);
        int configured = 0;
        for (Provider p : all) {
            // A keyless custom endpoint counts as configured once it has a URL.
            if (!p.requiresKey() || (!p.apiKey.isEmpty() && p.hasValidKeyFormat())) {
                configured++;
            }
        }

        String activeEngine = getActiveEngineName();
        if (configured == 0) {
            return "No providers configured \u2022 Tap to set up";
        }
        return configured + "/" + all.size() + " configured \u2022 Active: " + activeEngine;
    }

    private String buildTranslationSummary() {
        String provider = getActiveEngineName();
        String timeout = preferences.getString(GeminiConstants.PREF_TIMEOUT, String.valueOf(GeminiConstants.DEFAULT_TIMEOUT));
        return "Provider: " + provider + " • Timeout: " + timeout + "ms";
    }

    private String buildContextSummary() {
        String tone = preferences.getString(GeminiConstants.PREF_CONTEXT_TONE, "");
        if (tone == null || tone.isEmpty()) {
            return "No context configured \u2022 Set up for better translations";
        }
        if (tone.length() > 40) {
            tone = tone.substring(0, 37) + "...";
        }
        return tone;
    }

    // ==================== Providers Menu ====================

    private void showProvidersMenu(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        java.util.List<Provider> all = Providers.all(preferences);

        // One extra row at the end for managing user-defined endpoints.
        CharSequence[] labels = new CharSequence[all.size() + 1];
        for (int i = 0; i < all.size(); i++) {
            ProviderStatus s = getProviderStatus(all.get(i));
            labels[i] = s.icon + " " + s.displayName + "\n" + s.title + " \u2022 " + s.detail;
        }
        labels[all.size()] = "\u2795 Custom endpoints\nAdd any OpenAI-compatible API";

        pluginUI.buildDialog()
                .setTitle("AI Providers")
                .setItems(labels, (dialog, which) -> {
                    if (which >= all.size()) {
                        context.openPreference(CustomProviderPreference.class);
                    } else {
                        openSettingsFor(all.get(which).id);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("{cancel}", null)
                .show();
    }

    /** Custom entries share one editor; built-ins each have their own screen. */
    private void openSettingsFor(String providerId) {
        switch (providerId) {
            case Providers.ID_OPENAI:
                context.openPreference(OpenAIProviderPreference.class);
                break;
            case Providers.ID_CLAUDE:
                context.openPreference(ClaudeProviderPreference.class);
                break;
            case Providers.ID_OPENROUTER:
                context.openPreference(OpenRouterProviderPreference.class);
                break;
            case Providers.ID_GEMINI:
                context.openPreference(GeminiProviderPreference.class);
                break;
            default:
                context.openPreference(CustomProviderPreference.class);
                break;
        }
    }

    // ==================== Provider Status Helpers ====================

    private String getActiveEngineName() {
        return Providers.selected(preferences).displayName;
    }

    private ProviderStatus getProviderStatus(Provider provider) {
        synchronized (providerStatusCache) {
            ProviderStatus cached = providerStatusCache.get(provider.id);
            if (cached != null) return cached;
        }
        ProviderStatus computed = buildProviderStatus(provider);
        synchronized (providerStatusCache) {
            providerStatusCache.put(provider.id, computed);
        }
        return computed;
    }

    /** Purely decorative; an unknown provider falls through to the generic mark. */
    private static String iconFor(String providerId) {
        switch (providerId) {
            case Providers.ID_GEMINI:     return "\u2728";
            case Providers.ID_OPENAI:     return "\uD83E\uDDE0";
            case Providers.ID_CLAUDE:     return "\uD83C\uDFAD";
            case Providers.ID_OPENROUTER: return "\uD83D\uDD00";
            default:                      return "\uD83D\uDD0C";
        }
    }

    private ProviderStatus buildProviderStatus(Provider provider) {
        String icon = iconFor(provider.id);
        String displayName = provider.displayName;

        // Self-hosted endpoints take no key, so "no key" is a valid state.
        if (!provider.requiresKey()) {
            return provider.model == null || provider.model.isEmpty()
                    ? new ProviderStatus(displayName, icon, "No model set", "Tap to configure")
                    : new ProviderStatus(displayName, icon, "Ready", provider.model);
        }
        if (provider.apiKey.isEmpty()) {
            return new ProviderStatus(displayName, icon, "Not configured", "Tap to set up");
        }
        if (!provider.hasValidKeyFormat()) {
            return new ProviderStatus(displayName, icon, "Invalid key", "Check format");
        }
        return new ProviderStatus(displayName, icon, "Ready", formatKeyHint(provider.apiKey));
    }

    private String formatKeyHint(String key) {
        if (key == null || key.isEmpty()) return "\u2022\u2022\u2022\u2022";
        int visible = Math.min(4, key.length());
        return "\u2022\u2022\u2022\u2022" + key.substring(key.length() - visible);
    }

    private void ensurePreferenceListenerRegistered() {
        if (preferences != null && !preferenceListenerRegistered) {
            preferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
            preferenceListenerRegistered = true;
        }
    }

    private String mapPreferenceToProviderKey(String prefKey) {
        if (GeminiConstants.PREF_API_KEY.equals(prefKey)) return Providers.ID_GEMINI;
        if (GeminiConstants.PREF_OPENAI_API_KEY.equals(prefKey)) return Providers.ID_OPENAI;
        if (GeminiConstants.PREF_CLAUDE_API_KEY.equals(prefKey)) return Providers.ID_CLAUDE;
        if (GeminiConstants.PREF_OPENROUTER_API_KEY.equals(prefKey)) return Providers.ID_OPENROUTER;
        return null;
    }
}
