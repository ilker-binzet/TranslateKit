package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import bin.mt.plugin.api.LocalString;
import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.preference.PluginPreference;

/**
 * Main preference screen for TranslateKit.
 * Clean 5-category navigation with sub-screens for each section.
 *
 * @author Ilker Binzet
 * @version 0.8.0
 */
public class GeminiTranslatePreference implements PluginPreference {

    private LocalString localString;
    private PluginContext context;
    private SharedPreferences preferences;
    private final Map<String, ProviderStatus> providerStatusCache = new HashMap<>();
    private boolean preferenceListenerRegistered;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener = (prefs, key) -> {
        String providerKey = mapPreferenceToProviderKey(key);
        if (providerKey != null) {
            synchronized (providerStatusCache) {
                providerStatusCache.remove(providerKey);
            }
        }
    };

    private static final Pattern PATTERN_GEMINI_API_KEY = Pattern.compile(GeminiConstants.API_KEY_PATTERN);
    private static final Pattern PATTERN_OPENAI_API_KEY = Pattern.compile(GeminiConstants.OPENAI_API_KEY_PATTERN);
    private static final Pattern PATTERN_CLAUDE_API_KEY = Pattern.compile(GeminiConstants.CLAUDE_API_KEY_PATTERN);

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
        this.localString = context.getAssetLocalString("GeminiTranslate");
        if (this.localString == null) {
            this.localString = context.getLocalString();
        }
        this.preferences = context.getPreferences();
        synchronized (providerStatusCache) {
            providerStatusCache.clear();
        }
        ensurePreferenceListenerRegistered();

        builder.setLocalString(localString);

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
        int configured = 0;
        if (isKeyConfigured(GeminiConstants.PREF_API_KEY, PATTERN_GEMINI_API_KEY)) configured++;
        if (isKeyConfigured(GeminiConstants.PREF_OPENAI_API_KEY, PATTERN_OPENAI_API_KEY)) configured++;
        if (isKeyConfigured(GeminiConstants.PREF_CLAUDE_API_KEY, PATTERN_CLAUDE_API_KEY)) configured++;

        String activeEngine = getActiveEngineName();
        if (configured == 0) {
            return "No providers configured \u2022 Tap to set up";
        }
        return configured + "/3 configured \u2022 Active: " + activeEngine;
    }

    private String buildTranslationSummary() {
        String engine = getActiveEngineName();
        String timeout = preferences.getString(GeminiConstants.PREF_TIMEOUT, String.valueOf(GeminiConstants.DEFAULT_TIMEOUT));
        return "Engine: " + engine + " \u2022 Timeout: " + timeout + "ms";
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
        ProviderStatus gemini = getProviderStatus("gemini");
        ProviderStatus openai = getProviderStatus("openai");
        ProviderStatus claude = getProviderStatus("claude");

        CharSequence[] labels = new CharSequence[]{
            gemini.icon + " " + gemini.displayName + "\n" + gemini.title + " \u2022 " + gemini.detail,
            openai.icon + " " + openai.displayName + "\n" + openai.title + " \u2022 " + openai.detail,
            claude.icon + " " + claude.displayName + "\n" + claude.title + " \u2022 " + claude.detail
        };

        pluginUI.buildDialog()
                .setTitle("AI Providers")
                .setItems(labels, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            context.openPreference(GeminiProviderPreference.class);
                            break;
                        case 1:
                            context.openPreference(OpenAIProviderPreference.class);
                            break;
                        case 2:
                            context.openPreference(ClaudeProviderPreference.class);
                            break;
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("{cancel}", null)
                .show();
    }

    // ==================== Provider Status Helpers ====================

    private boolean isKeyConfigured(String prefKey, Pattern pattern) {
        String key = preferences.getString(prefKey, "");
        return key != null && !key.isEmpty() && pattern.matcher(key).matches();
    }

    private String getActiveEngineName() {
        String engine = preferences.getString(GeminiConstants.PREF_DEFAULT_ENGINE, GeminiConstants.DEFAULT_ENGINE);
        switch (engine) {
            case GeminiConstants.ENGINE_OPENAI: return "OpenAI";
            case GeminiConstants.ENGINE_CLAUDE: return "Claude";
            default: return "Gemini";
        }
    }

    private ProviderStatus getProviderStatus(String providerKey) {
        synchronized (providerStatusCache) {
            ProviderStatus cached = providerStatusCache.get(providerKey);
            if (cached != null) return cached;
        }
        ProviderStatus computed = buildProviderStatus(providerKey);
        synchronized (providerStatusCache) {
            providerStatusCache.put(providerKey, computed);
        }
        return computed;
    }

    private ProviderStatus buildProviderStatus(String providerKey) {
        String prefKey = GeminiConstants.PREF_API_KEY;
        Pattern keyPattern = PATTERN_GEMINI_API_KEY;
        String displayName = "Gemini AI";
        String icon = "\u2728";

        switch (providerKey) {
            case "openai":
                prefKey = GeminiConstants.PREF_OPENAI_API_KEY;
                keyPattern = PATTERN_OPENAI_API_KEY;
                displayName = "OpenAI GPT-4o";
                icon = "\uD83E\uDDE0";
                break;
            case "claude":
                prefKey = GeminiConstants.PREF_CLAUDE_API_KEY;
                keyPattern = PATTERN_CLAUDE_API_KEY;
                displayName = "Claude 3.5";
                icon = "\uD83C\uDFAD";
                break;
            default:
                break;
        }

        String keyValue = preferences.getString(prefKey, "");
        if (keyValue == null) keyValue = "";

        if (keyValue.isEmpty()) {
            return new ProviderStatus(displayName, icon, "Not configured", "Tap to set up");
        }
        if (!keyPattern.matcher(keyValue).matches()) {
            return new ProviderStatus(displayName, icon, "Invalid key", "Check format");
        }
        return new ProviderStatus(displayName, icon, "Ready", formatKeyHint(keyValue));
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
        if (GeminiConstants.PREF_API_KEY.equals(prefKey)) return "gemini";
        if (GeminiConstants.PREF_OPENAI_API_KEY.equals(prefKey)) return "openai";
        if (GeminiConstants.PREF_CLAUDE_API_KEY.equals(prefKey)) return "claude";
        return null;
    }
}
