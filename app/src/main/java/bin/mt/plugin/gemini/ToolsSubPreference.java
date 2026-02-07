package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import android.os.SystemClock;
import android.text.format.DateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import bin.mt.plugin.api.LocalString;
import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.preference.PluginPreference;

/**
 * Sub-preference screen for Tools & Diagnostics.
 * Contains: Provider Status dashboard, Interactive Provider Test,
 * View Logs, Debug Logging toggle, and hidden Debug Tools menu.
 */
public class ToolsSubPreference implements PluginPreference {

    private PluginContext context;
    private SharedPreferences preferences;
    private final Map<String, ProviderStatus> providerStatusCache = new HashMap<>();

    private static final Pattern PATTERN_GEMINI_API_KEY = Pattern.compile(GeminiConstants.API_KEY_PATTERN);
    private static final Pattern PATTERN_OPENAI_API_KEY = Pattern.compile(GeminiConstants.OPENAI_API_KEY_PATTERN);
    private static final Pattern PATTERN_CLAUDE_API_KEY = Pattern.compile(GeminiConstants.CLAUDE_API_KEY_PATTERN);

    private static final int DEBUG_TAP_THRESHOLD = 5;
    private static final long DEBUG_TAP_RESET_MS = 1500L;
    private int versionTapCount;
    private long lastVersionTapUptime;

    // ==================== Inner Classes ====================

    private static class ProviderStatus {
        final String providerKey;
        final String displayName;
        final String icon;
        final String title;
        final String detail;
        final String statusType;

        ProviderStatus(String providerKey, String displayName, String icon, String title,
                       String detail, String statusType) {
            this.providerKey = providerKey;
            this.displayName = displayName;
            this.icon = icon;
            this.title = title;
            this.detail = detail;
            this.statusType = statusType;
        }

        int getAccentColor(bin.mt.plugin.api.ui.PluginUI pluginUI) {
            return GeminiColorTokens.getProviderBrandColor(pluginUI, providerKey);
        }
    }

    @Override
    public void onBuild(PluginContext context, Builder builder) {
        this.context = context;
        this.preferences = context.getPreferences();
        LocalString localString = context.getAssetLocalString("GeminiTranslate");
        if (localString == null) {
            localString = context.getLocalString();
        }
        builder.setLocalString(localString);

        synchronized (providerStatusCache) {
            providerStatusCache.clear();
        }

        // ==================== Provider Status ====================
        builder.addText("Provider Status")
                .summary("View all providers health at a glance")
                .onClick((pluginUI, item) -> showDashboardCard(pluginUI));

        // ==================== Test Active Provider ====================
        builder.addText("Test Active Provider")
                .summary("Quick test: " + getActiveProviderName())
                .onClick((pluginUI, item) -> showInteractiveProviderTest(pluginUI));

        // ==================== View Logs ====================
        builder.addText("View Logs")
                .summary("Open MT Manager log viewer")
                .onClick((pluginUI, item) -> context.openLogViewer());

        // ==================== Debug Logging ====================
        builder.addSwitch("Debug Logging", GeminiConstants.PREF_ENABLE_DEBUG)
                .defaultValue(GeminiConstants.DEFAULT_ENABLE_DEBUG)
                .summary("Record detailed request info to MT Manager logs");

        // ==================== Hidden Debug Access ====================
        builder.addText("Plugin Version")
            .summary("v" + GeminiConstants.PLUGIN_VERSION_NAME)
            .onClick((pluginUI, item) -> handlePluginVersionTap(pluginUI));

        // Preference change callback
        builder.onPreferenceChange((pluginUI, preferenceItem, newValue) -> {
            if (preferenceItem.getKey().equals(GeminiConstants.PREF_ENABLE_DEBUG)) {
                boolean debugEnabled = (boolean) newValue;
                if (debugEnabled) {
                    context.showToast("Debug logging enabled - Check MT Manager logs for details");
                }
            }
        });
    }

    // ==================== Dashboard Dialog ====================

    private void showDashboardCard(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        ProviderStatus geminiStatus = getProviderStatus("gemini");
        ProviderStatus openaiStatus = getProviderStatus("openai");
        ProviderStatus claudeStatus = getProviderStatus("claude");
        ProviderStatus activeStatus = getActiveProviderStatus();
        String activeModel = getActiveModelName();

        int primaryTextColor = GeminiColorTokens.getPrimaryTextColor(pluginUI);
        int secondaryTextColor = GeminiColorTokens.getSecondaryTextColor(pluginUI);
        int activeCardBackground = GeminiColorTokens.getCardBackgroundEmphasizedColor(pluginUI);
        int cardBackground = GeminiColorTokens.getCardBackgroundColor(pluginUI);

        bin.mt.plugin.api.ui.PluginView view = pluginUI
            .buildVerticalLayout()
            .addTextView().text("AI Provider Overview").bold().textSize(18).paddingBottomDp(8).textColor(primaryTextColor)

            // Active provider card
            .addVerticalLayout().paddingDp(16).backgroundColor(activeCardBackground).children(subBuilder -> subBuilder
                .addTextView().text("Active Provider").bold().textColor(activeStatus.getAccentColor(pluginUI))
                .addTextView().text(activeStatus.icon + " " + activeStatus.displayName).paddingTopDp(6).textSize(18).textColor(primaryTextColor)
                .addTextView().text(activeStatus.title).paddingTopDp(4).textColor(primaryTextColor)
                .addTextView().text(activeStatus.detail).paddingTopDp(2).textColor(secondaryTextColor)
                .addTextView().text("Model: " + activeModel).paddingTopDp(10).textColor(secondaryTextColor)
            )
            .addTextView().height(1).widthMatchParent().backgroundColor(pluginUI.colorDivider()).marginVerticalDp(12)

            .addTextView().text("Provider Health").bold().textSize(16).textColor(primaryTextColor)
            .addVerticalLayout().paddingTopDp(8).children(column -> column
                .addHorizontalLayout().paddingDp(12).marginBottomDp(8)
                    .backgroundColor(cardBackground)
                    .children(row -> row
                        .addTextView().text(geminiStatus.icon).textSize(28).paddingRightDp(12)
                        .addVerticalLayout().children(col -> col
                            .addTextView().text(geminiStatus.displayName).bold().textColor(geminiStatus.getAccentColor(pluginUI))
                            .addTextView().text(geminiStatus.title).paddingTopDp(2).textColor(primaryTextColor)
                            .addTextView().text(geminiStatus.detail).paddingTopDp(2).textColor(secondaryTextColor)
                        )
                    )
                .addHorizontalLayout().paddingDp(12).marginBottomDp(8)
                    .backgroundColor(cardBackground)
                    .children(row -> row
                        .addTextView().text(openaiStatus.icon).textSize(28).paddingRightDp(12)
                        .addVerticalLayout().children(col -> col
                            .addTextView().text(openaiStatus.displayName).bold().textColor(openaiStatus.getAccentColor(pluginUI))
                            .addTextView().text(openaiStatus.title).paddingTopDp(2).textColor(primaryTextColor)
                            .addTextView().text(openaiStatus.detail).paddingTopDp(2).textColor(secondaryTextColor)
                        )
                    )
                .addHorizontalLayout().paddingDp(12)
                    .backgroundColor(cardBackground)
                    .children(row -> row
                        .addTextView().text(claudeStatus.icon).textSize(28).paddingRightDp(12)
                        .addVerticalLayout().children(col -> col
                            .addTextView().text(claudeStatus.displayName).bold().textColor(claudeStatus.getAccentColor(pluginUI))
                            .addTextView().text(claudeStatus.title).paddingTopDp(2).textColor(primaryTextColor)
                            .addTextView().text(claudeStatus.detail).paddingTopDp(2).textColor(secondaryTextColor)
                        )
                    )
            )
            .build();

        pluginUI.buildDialog()
            .setTitle("Dashboard")
            .setView(view)
            .setPositiveButton("{close}", null)
            .show();
    }

    // ==================== Interactive Provider Test ====================

    private void showInteractiveProviderTest(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        String engine = preferences.getString(GeminiConstants.PREF_DEFAULT_ENGINE, GeminiConstants.DEFAULT_ENGINE);
        String providerName = getActiveProviderName();

        String key = "";
        Pattern keyPattern = PATTERN_GEMINI_API_KEY;

        if (GeminiConstants.ENGINE_OPENAI.equals(engine)) {
            key = preferences.getString(GeminiConstants.PREF_OPENAI_API_KEY, "");
            keyPattern = PATTERN_OPENAI_API_KEY;
        } else if (GeminiConstants.ENGINE_CLAUDE.equals(engine)) {
            key = preferences.getString(GeminiConstants.PREF_CLAUDE_API_KEY, "");
            keyPattern = PATTERN_CLAUDE_API_KEY;
        } else {
            key = preferences.getString(GeminiConstants.PREF_API_KEY, "");
            keyPattern = PATTERN_GEMINI_API_KEY;
        }

        String statusIcon;
        String statusMsg;
        String resultMsg;

        if (key.isEmpty()) {
            statusIcon = "⚪";
            statusMsg = "API Key Missing";
            resultMsg = "Please configure your API key in provider settings.";
        } else if (!keyPattern.matcher(key).matches()) {
            statusIcon = "🔴";
            statusMsg = "Invalid Format";
            resultMsg = "API key format is invalid. Please check your key.";
        } else {
            statusIcon = "🟡";
            statusMsg = "Configuration Valid";
            resultMsg = "API key format is correct!\n\nTip: This validates format only. Use 'Test API Key' in provider settings to verify connectivity.";
        }

        bin.mt.plugin.api.ui.PluginView view = pluginUI
            .buildVerticalLayout()
            .addTextView().text("Testing: " + providerName).bold().textSize(16).paddingBottomDp(16)
            .addVerticalLayout().paddingDp(12).children(subBuilder -> subBuilder
                .addHorizontalLayout().children(h -> h
                    .addTextView().text(statusIcon).textSize(32).paddingRightDp(12)
                    .addVerticalLayout().children(v -> v
                        .addTextView().text(statusMsg).bold().textSize(16)
                        .addTextView().text(resultMsg).paddingTopDp(4).textSize(14)
                    )
                )
            )
            .build();

        pluginUI.buildDialog()
            .setTitle("Provider Test")
            .setView(view)
            .setPositiveButton("{close}", null)
            .show();
    }

    // ==================== Hidden Debug Tools ====================

    private void handlePluginVersionTap(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        if (context == null) return;

        long now = SystemClock.uptimeMillis();
        if (now - lastVersionTapUptime > DEBUG_TAP_RESET_MS) {
            versionTapCount = 0;
        }
        versionTapCount++;
        lastVersionTapUptime = now;

        if (versionTapCount < DEBUG_TAP_THRESHOLD) {
            int remaining = DEBUG_TAP_THRESHOLD - versionTapCount;
            String message = remaining == 1
                    ? "1 tap away from debug tools"
                    : remaining + " taps away from debug tools";
            context.showToast(message);
            return;
        }

        versionTapCount = 0;
        context.showToast("Debug tools unlocked");
        showDebugTools(pluginUI);
    }

    private void showDebugTools(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        if (pluginUI == null || preferences == null) return;

        boolean disableCache = preferences.getBoolean(GeminiConstants.PREF_DEBUG_DISABLE_MODEL_CACHE, false);
        ModelCatalogManager.CacheDiagnostics geminiDiagnostics =
                ModelCatalogManager.inspectCache(preferences, GeminiConstants.PREF_CACHE_GEMINI_MODELS);
        ModelCatalogManager.CacheDiagnostics openAiDiagnostics =
                ModelCatalogManager.inspectCache(preferences, GeminiConstants.PREF_CACHE_OPENAI_MODELS);
        ModelCatalogManager.CacheDiagnostics claudeDiagnostics =
                ModelCatalogManager.inspectCache(preferences, GeminiConstants.PREF_CACHE_CLAUDE_MODELS);

        int primaryTextColor = GeminiColorTokens.getPrimaryTextColor(pluginUI);
        int secondaryTextColor = GeminiColorTokens.getSecondaryTextColor(pluginUI);
        int cardColor = GeminiColorTokens.getCardBackgroundColor(pluginUI);
        String ttlSummary = "Entries expire after " + formatDuration(GeminiConstants.MODEL_CACHE_TTL_MS);

        bin.mt.plugin.api.ui.PluginView view = pluginUI
            .buildVerticalLayout()
            .addTextView().text("Hidden Debug Menu").bold().textSize(18).textColor(primaryTextColor)
            .addTextView().text("Inspect cached model catalogs, TTL status and cache-bypass controls.")
                .paddingTopDp(4).textColor(secondaryTextColor)
            .addTextView().text(ttlSummary).paddingTopDp(2).textColor(secondaryTextColor)
            .addTextView().height(1).widthMatchParent().backgroundColor(GeminiColorTokens.getDividerColor(pluginUI)).marginVerticalDp(12)
            .addTextView().text("Catalog Diagnostics").bold().textSize(16).textColor(primaryTextColor)
            .addVerticalLayout().paddingTopDp(8).children(column -> column
                .addVerticalLayout().paddingDp(12).marginBottomDp(10).backgroundColor(cardColor).children(section -> section
                    .addTextView().text("Gemini Catalog").bold().textColor(GeminiColorTokens.getProviderBrandColor(pluginUI, "gemini"))
                    .addTextView().text(formatCacheDiagnostics(geminiDiagnostics)).paddingTopDp(4).textColor(secondaryTextColor)
                )
                .addVerticalLayout().paddingDp(12).marginBottomDp(10).backgroundColor(cardColor).children(section -> section
                    .addTextView().text("OpenAI Catalog").bold().textColor(GeminiColorTokens.getProviderBrandColor(pluginUI, "openai"))
                    .addTextView().text(formatCacheDiagnostics(openAiDiagnostics)).paddingTopDp(4).textColor(secondaryTextColor)
                )
                .addVerticalLayout().paddingDp(12).marginBottomDp(10).backgroundColor(cardColor).children(section -> section
                    .addTextView().text("Claude Catalog").bold().textColor(GeminiColorTokens.getProviderBrandColor(pluginUI, "claude"))
                    .addTextView().text(formatCacheDiagnostics(claudeDiagnostics)).paddingTopDp(4).textColor(secondaryTextColor)
                )
            )
            .addTextView().text("Cache Controls").bold().textSize(16).paddingTopDp(8).textColor(primaryTextColor)
            .addVerticalLayout().paddingDp(12).backgroundColor(cardColor).children(section -> section
                .addTextView().text(disableCache ? "Cache bypass active" : "Cache enabled")
                    .bold().textColor(primaryTextColor)
                .addTextView().text(buildCacheControlHint(disableCache)).paddingTopDp(4).textColor(secondaryTextColor)
            )
            .build();

        pluginUI.buildDialog()
                .setTitle("Debug Tools")
                .setView(view)
                .setPositiveButton("{close}", null)
                .setNegativeButton(disableCache ? "Enable Cache" : "Disable Cache", (dialog, which) -> {
                    toggleModelCacheBypass();
                    if (context != null) {
                        context.showToast(disableCache ? "Model cache enabled" : "Model cache disabled");
                    }
                })
                .setNeutralButton("Clear Caches", (dialog, which) -> {
                    clearAllModelCaches();
                    if (context != null) {
                        context.showToast("All model caches cleared");
                    }
                })
                .show();
    }

    // ==================== Helper Methods ====================

    private String getActiveProviderName() {
        String engine = preferences.getString(GeminiConstants.PREF_DEFAULT_ENGINE, GeminiConstants.DEFAULT_ENGINE);
        switch (engine) {
            case GeminiConstants.ENGINE_OPENAI: return "OpenAI GPT-4o";
            case GeminiConstants.ENGINE_CLAUDE: return "Claude 3.5";
            default: return "Gemini AI";
        }
    }

    private String getActiveModelName() {
        return preferences.getString(GeminiConstants.PREF_MODEL_NAME, GeminiConstants.DEFAULT_MODEL);
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

    private ProviderStatus getActiveProviderStatus() {
        String engine = preferences.getString(GeminiConstants.PREF_DEFAULT_ENGINE, GeminiConstants.DEFAULT_ENGINE);
        switch (engine) {
            case GeminiConstants.ENGINE_OPENAI: return getProviderStatus("openai");
            case GeminiConstants.ENGINE_CLAUDE: return getProviderStatus("claude");
            default: return getProviderStatus("gemini");
        }
    }

    private ProviderStatus buildProviderStatus(String providerKey) {
        String prefKey = GeminiConstants.PREF_API_KEY;
        Pattern keyPattern = PATTERN_GEMINI_API_KEY;
        String displayName = "Gemini AI";
        String icon = "✨";

        switch (providerKey) {
            case "openai":
                prefKey = GeminiConstants.PREF_OPENAI_API_KEY;
                keyPattern = PATTERN_OPENAI_API_KEY;
                displayName = "OpenAI GPT-4o";
                icon = "🧠";
                break;
            case "claude":
                prefKey = GeminiConstants.PREF_CLAUDE_API_KEY;
                keyPattern = PATTERN_CLAUDE_API_KEY;
                displayName = "Claude 3.5";
                icon = "🎭";
                break;
            default:
                break;
        }

        String keyValue = preferences.getString(prefKey, "");
        if (keyValue == null) keyValue = "";

        if (keyValue.isEmpty()) {
            return new ProviderStatus(providerKey, displayName, icon,
                "Not configured",
                "Add your API key to activate " + displayName,
                "neutral");
        }

        if (!keyPattern.matcher(keyValue).matches()) {
            return new ProviderStatus(providerKey, displayName, icon,
                "Invalid API key",
                "The key format looks wrong. Re-copy it from the provider dashboard.",
                "error");
        }

        return new ProviderStatus(providerKey, displayName, icon,
            "Ready to use",
            "Key active (" + formatKeyHint(keyValue) + ")",
            "ready");
    }

    private String formatKeyHint(String key) {
        if (key == null || key.isEmpty()) return "••••";
        int visible = Math.min(4, key.length());
        return "••••" + key.substring(key.length() - visible);
    }

    private String buildCacheControlHint(boolean cacheDisabled) {
        StringBuilder sb = new StringBuilder();
        if (cacheDisabled) {
            sb.append("Always fetching live model catalogs. Useful for debugging inconsistent lists.");
        } else {
            sb.append("Using cached catalogs for faster provider loading.");
        }
        sb.append(" Use the buttons below to toggle cache usage or purge stored catalogs.");
        return sb.toString();
    }

    private String formatCacheDiagnostics(ModelCatalogManager.CacheDiagnostics diagnostics) {
        if (diagnostics == null) return "No diagnostics available";
        if (!diagnostics.hasData) {
            if (diagnostics.fetchedAt <= 0) return "Entries: 0\nStatus: Never fetched";
            StringBuilder emptyBuilder = new StringBuilder();
            emptyBuilder.append("Entries: 0");
            emptyBuilder.append("\nFetched: ").append(formatTimestamp(diagnostics.fetchedAt));
            emptyBuilder.append("\nStatus: ").append(diagnostics.expired ? "Expired" : "Empty result");
            return emptyBuilder.toString();
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Entries: ").append(diagnostics.modelCount);
        builder.append("\nFetched: ").append(formatTimestamp(diagnostics.fetchedAt));
        if (diagnostics.ageMs >= 0) {
            builder.append(" (").append(formatDuration(diagnostics.ageMs)).append(" ago)");
        }
        builder.append("\nStatus: ").append(diagnostics.expired ? "Expired" : "Fresh");
        return builder.toString();
    }

    private CharSequence formatTimestamp(long timestamp) {
        if (timestamp <= 0) return "never";
        return DateFormat.format("MMM d, HH:mm", timestamp);
    }

    private String formatDuration(long durationMs) {
        if (durationMs < 0) return "unknown";
        long seconds = durationMs / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remSeconds = seconds % 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + remSeconds + "s";
        return remSeconds + "s";
    }

    private void toggleModelCacheBypass() {
        if (preferences == null) return;
        boolean disabled = preferences.getBoolean(GeminiConstants.PREF_DEBUG_DISABLE_MODEL_CACHE, false);
        preferences.edit().putBoolean(GeminiConstants.PREF_DEBUG_DISABLE_MODEL_CACHE, !disabled).apply();
    }

    private void clearAllModelCaches() {
        if (preferences == null) return;
        ModelCatalogManager.clearModelCache(preferences, GeminiConstants.PREF_CACHE_GEMINI_MODELS);
        ModelCatalogManager.clearModelCache(preferences, GeminiConstants.PREF_CACHE_OPENAI_MODELS);
        ModelCatalogManager.clearModelCache(preferences, GeminiConstants.PREF_CACHE_CLAUDE_MODELS);
    }
}
