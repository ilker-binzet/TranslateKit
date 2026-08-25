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

    /** Rows whose summary names the active provider. */
    private static final String KEY_PROVIDERS_ROW = "providers_row";
    private static final String KEY_TRANSLATION_ROW = "translation_row";

    private PluginContext context;
    private SharedPreferences preferences;
    /** Set once the screen exists, so those two summaries can be updated in place. */
    private PreferenceScreen screen;
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
        /** Drives the title colour — see GeminiColorTokens.getStatusColor. */
        final String statusType;

        ProviderStatus(String displayName, String icon, String title, String detail,
                       String statusType) {
            this.displayName = displayName;
            this.icon = icon;
            this.title = title;
            this.detail = detail;
            this.statusType = statusType;
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
        builder.addText(str("{nav_providers}"), KEY_PROVIDERS_ROW)
                .summary(buildProvidersSummary())
                .onClick((pluginUI, item) -> showProvidersMenu(pluginUI));

        // ==================== 2. Translation Settings ====================
        builder.addText(str("{nav_translation}"), KEY_TRANSLATION_ROW)
                .summary(buildTranslationSummary())
                .onClick((pluginUI, item) -> context.openPreference(TranslationSubPreference.class));

        // ==================== 3. Context & Tone ====================
        builder.addText(str("{nav_context}"))
                .summary(buildContextSummary())
                .onClick((pluginUI, item) -> context.openPreference(ContextToneSubPreference.class));

        // ==================== 4. Tools & Diagnostics ====================
        builder.addText(str("{nav_tools}"))
                .summary(str("{nav_tools_summary}"))
                .onClick((pluginUI, item) -> context.openPreference(ToolsSubPreference.class));

        // ==================== 5. Translate This Plugin ====================
        // The plugin ships English, Turkish and Simplified Chinese. Anyone who
        // wants their own language has to be able to find out how from inside
        // the plugin, or they never will.
        builder.addText(str("{nav_translate_plugin}"))
                .summary(str("{nav_translate_plugin_summary}"))
                .url(GeminiConstants.URL_TRANSLATE_GUIDE);

        // ==================== 6. About ====================
        builder.addText(str("{nav_about}"))
                .summary("TranslateKit v" + GeminiConstants.PLUGIN_VERSION_NAME + " • by Ilker Binzet")
                .url(GeminiConstants.DEVELOPER_GITHUB);

        builder.onCreated((pluginUI, createdScreen) -> this.screen = createdScreen);
    }

    // ==================== Summary Builders ====================

    /** Localized text for {@code key}; the language packs live in assets. */
    private String str(String key) {
        return context.getString(key);
    }

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
            return str("{providers_none}");
        }
        return configured + "/" + all.size() + " " + str("{providers_configured}")
                + " \u2022 " + str("{providers_active}") + ": " + activeEngine;
    }

    private String buildTranslationSummary() {
        String provider = getActiveEngineName();
        String timeout = preferences.getString(GeminiConstants.PREF_TIMEOUT, String.valueOf(GeminiConstants.DEFAULT_TIMEOUT));
        return str("{providers_provider}") + ": " + provider
                + " • " + str("{trans_timeout}") + ": " + timeout + "ms";
    }

    private String buildContextSummary() {
        String tone = preferences.getString(GeminiConstants.PREF_CONTEXT_TONE, "");
        if (tone == null || tone.isEmpty()) {
            return str("{ctx_none}");
        }
        if (tone.length() > 40) {
            tone = tone.substring(0, 37) + "...";
        }
        return tone;
    }

    // ==================== Providers Menu ====================

    private void showProvidersMenu(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        java.util.List<Provider> all = Providers.all(preferences);
        String activeId = Providers.selected(preferences).id;

        // One extra row at the end for managing user-defined endpoints.
        CharSequence[] labels = new CharSequence[all.size() + 1];
        for (int i = 0; i < all.size(); i++) {
            ProviderStatus s = getProviderStatus(all.get(i));
            // Only the status word is tinted; the name and key hint stay in the
            // normal text colour so the green actually means something.
            android.text.SpannableStringBuilder label = new android.text.SpannableStringBuilder();
            label.append(all.get(i).id.equals(activeId) ? "\u2713 " : "\u2007\u2007")
                    .append(s.icon).append(" ").append(s.displayName).append("\n");
            GeminiColorTokens.appendStatus(label, pluginUI, s.title, s.statusType);
            label.append(" \u2022 ").append(s.detail);
            labels[i] = label;
        }
        labels[all.size()] = "\u2795 " + str("{providers_custom}")
                + "\n" + str("{providers_custom_summary}");

        pluginUI.buildDialog()
                .setTitle(str("{nav_providers}"))
                .setItems(labels, (dialog, which) -> {
                    if (which >= all.size()) {
                        context.openPreference(CustomProviderPreference.class);
                    } else {
                        showProviderActions(pluginUI, all.get(which));
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("{cancel}", null)
                .show();
    }

    /**
     * Activate or configure one provider.
     *
     * <p>Choosing the active provider lives here rather than in Translation
     * Settings: this is the only list that shows every provider \u2014 OpenRouter
     * and user-defined endpoints included \u2014 together with whether it is
     * actually configured.
     */
    private void showProviderActions(bin.mt.plugin.api.ui.PluginUI pluginUI, Provider provider) {
        boolean active = provider.id.equals(Providers.selected(preferences).id);
        // The activate row only appears when it would do something; a dead row
        // in a two-item list reads worse than a one-item list.
        CharSequence[] actions = active
                ? new CharSequence[]{"\u2699 " + str("{providers_settings}")}
                : new CharSequence[]{"\u2b50 " + str("{providers_use_default}"),
                                   "\u2699 " + str("{providers_settings}")};

        pluginUI.buildDialog()
                .setTitle(iconFor(provider.id) + " " + provider.displayName)
                .setItems(actions, (dialog, which) -> {
                    if (!active && which == 0) {
                        setDefaultProvider(provider);
                    } else {
                        openSettingsFor(provider.id);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("{cancel}", null)
                .show();
    }

    private void setDefaultProvider(Provider provider) {
        preferences.edit()
                .putString(GeminiConstants.PREF_DEFAULT_ENGINE, provider.id)
                .apply();
        refreshActiveSummaries();
        // Say so up front rather than letting the first translation silently
        // fall back to Gemini because the key was never entered.
        context.showToast("ready".equals(provider.statusType())
                ? str("{msg_default_provider}") + ": " + provider.displayName
                : str("{msg_default_provider}") + ": " + provider.displayName
                        + " \u2014 " + str("{msg_not_configured_yet}"));
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

    /**
     * Rewrites the two summaries that name the active provider.
     *
     * <p>Picking a provider happens in a dialog, and closing a dialog does not
     * rebuild the screen behind it — the rows kept naming the old provider
     * until the user backed out of settings and came in again.
     */
    private void refreshActiveSummaries() {
        if (screen == null) return;
        setSummary(KEY_PROVIDERS_ROW, buildProvidersSummary());
        setSummary(KEY_TRANSLATION_ROW, buildTranslationSummary());
    }

    private void setSummary(String key, CharSequence summary) {
        PreferenceItem item = screen.findPreference(key);
        if (item != null) {
            item.setSummary(summary);
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
        // Self-hosted endpoints take no key, so "no key" is a valid state there.
        String type = provider.statusType();

        if ("invalid".equals(type)) {
            return new ProviderStatus(displayName, icon, str("{status_invalid_key}"), str("{status_check_format}"), type);
        }
        if ("neutral".equals(type)) {
            return provider.requiresKey()
                    ? new ProviderStatus(displayName, icon, str("{status_not_configured}"), str("{status_tap_setup}"), type)
                    : new ProviderStatus(displayName, icon, str("{status_no_model}"), str("{status_tap_configure}"), type);
        }
        return new ProviderStatus(displayName, icon, str("{status_ready}"),
                provider.requiresKey() ? formatKeyHint(provider.apiKey) : provider.model, type);
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
