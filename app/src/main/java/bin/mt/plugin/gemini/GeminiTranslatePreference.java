package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.SystemClock;
import android.text.InputType;
import android.text.format.DateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import bin.mt.plugin.api.LocalString;
import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.drawable.MaterialIcons;
import bin.mt.plugin.api.preference.PluginPreference;

/**
 * Main preference screen for TranslateKit
 * Clean, modern design with minimal emojis and icon-based navigation
 * 
 * @author Ilker Binzet
 * @version 0.7.0-MODERN
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

    // Color constants removed - now using GeminiColorTokens for theme-aware colors
    private static final Pattern PATTERN_GEMINI_API_KEY = Pattern.compile(GeminiConstants.API_KEY_PATTERN);
    private static final Pattern PATTERN_OPENAI_API_KEY = Pattern.compile(GeminiConstants.OPENAI_API_KEY_PATTERN);
    private static final Pattern PATTERN_CLAUDE_API_KEY = Pattern.compile(GeminiConstants.CLAUDE_API_KEY_PATTERN);
    private static final int DEBUG_TAP_THRESHOLD = 5;
    private static final long DEBUG_TAP_RESET_MS = 1500L;
    private int versionTapCount;
    private long lastVersionTapUptime;

    private static class ProviderStatus {
        final String providerKey;
        final String displayName;
        final String icon;
        final String title;
        final String detail;
        final String statusType; // "not_configured", "error", "ready"

        ProviderStatus(String providerKey, String displayName, String icon, String title,
                       String detail, String statusType) {
            this.providerKey = providerKey;
            this.displayName = displayName;
            this.icon = icon;
            this.title = title;
            this.detail = detail;
            this.statusType = statusType;
        }

        // Helper methods for theme-aware colors (requires PluginUI)
        int getBackgroundColor(bin.mt.plugin.api.ui.PluginUI pluginUI) {
            return GeminiColorTokens.getStatusColor(pluginUI, statusType);
        }

        int getAccentColor(bin.mt.plugin.api.ui.PluginUI pluginUI) {
            return GeminiColorTokens.getProviderBrandColor(pluginUI, providerKey);
        }
    }

        private static class ContextPreset {
        final String title;
        final String subtitle;
        final String appName;
        final String appType;
        final String audience;
        final String tone;
        final String notes;

        ContextPreset(String title, String subtitle, String appName, String appType,
                  String audience, String tone, String notes) {
            this.title = title;
            this.subtitle = subtitle;
            this.appName = appName;
            this.appType = appType;
            this.audience = audience;
            this.tone = tone;
            this.notes = notes;
        }
        }

        private static class TonePreset {
        final String name;
        final String storedValue;
        final String description;

        TonePreset(String name, String storedValue, String description) {
            this.name = name;
            this.storedValue = storedValue;
            this.description = description;
        }
        }

        private static final ContextPreset[] CONTEXT_PRESETS = new ContextPreset[]{
            new ContextPreset(
                "Mobile App Launch",
                "Consumer onboarding flows",
                "Mobile Application",
                "Android/iOS Mobile Experience",
                "General smartphone users",
                "Friendly and clear",
                "Short sentences, plain language, actionable CTA verbs"
            ),
            new ContextPreset(
                "Gaming Experience",
                "Playful & energetic UI",
                "Gaming Application",
                "Mobile/PC Game Interface",
                "Gamers and casual players",
                "Energetic and playful",
                "Use game terminology, keep hype and momentum high"
            ),
            new ContextPreset(
                "Reading Companion",
                "E-book & article readers",
                "E-book Reader",
                "Digital Reading Platform",
                "Avid readers and book lovers",
                "Literary and sophisticated",
                "Flowing sentences, keep emphasis on readability and calm tone"
            ),
            new ContextPreset(
                "Business Dashboard",
                "Enterprise productivity tools",
                "Business Application",
                "Professional Analytics / Dashboard",
                "Business professionals and analysts",
                "Professional and concise",
                "Focus on clarity, mention KPIs, avoid slang"
            ),
            new ContextPreset(
                "Support Chatbot",
                "Customer care copy",
                "Support Assistant",
                "AI / Human Hybrid Support",
                "End-users needing troubleshooting",
                "Empathetic and helpful",
                "Reassure the user, acknowledge issues, provide next steps"
            ),
            new ContextPreset(
                "E-commerce Store",
                "Product & checkout flows",
                "Commerce Platform",
                "Online Shopping Experience",
                "Shoppers comparing products",
                "Conversion-focused and reassuring",
                "Highlight benefits, keep CTA strong, include trust cues"
            ),
            new ContextPreset(
                "Developer Docs",
                "APIs & technical notes",
                "Developer Portal",
                "Technical Documentation Suite",
                "Developers and integration engineers",
                "Precise and instructional",
                "Include parameters, avoid marketing tone, keep terminology exact"
            ),
            new ContextPreset(
                "Education Platform",
                "Lessons & assessments",
                "Learning Platform",
                "Education / LMS Experience",
                "Students and educators",
                "Encouraging and structured",
                "Explain learning goals, keep directions step-based and kind"
            )
        };

        private static final TonePreset[] TONE_PRESETS = new TonePreset[]{
            new TonePreset(
                "Friendly Clarity",
                "Friendly and clear (plain language, second-person guidance, concise sentences)",
                "Approachable help text for general audiences"
            ),
            new TonePreset(
                "Product Marketing",
                "Confident and inspiring marketing voice (benefit-driven, energetic, short CTA verbs)",
                "Highlight value propositions while staying concise"
            ),
            new TonePreset(
                "Legal / Policy",
                "Formal and compliant tone (objective, third-person, references policy numbers where needed)",
                "Use for privacy, security, or legal copy"
            ),
            new TonePreset(
                "Support Hero",
                "Empathetic and solution-focused (acknowledge frustration, reassure, offer clear steps)",
                "Great for help centers or chatbot replies"
            ),
            new TonePreset(
                "Technical Guide",
                "Precise and instructional (step-by-step, include field names, avoid marketing language)",
                "Best for developer or admin documentation"
            ),
            new TonePreset(
                "Playful Fun",
                "Playful and witty (light humor, emoji-friendly, upbeat pacing)",
                "Works for entertainment or Gen Z audiences"
            )
        };

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

        // ==================== AI Providers ====================
        builder.addText("AI Providers")
                .summary("Configure your translation engines");

        builder.addText("Gemini AI")
                .summary(getProviderStatusSummary("gemini"))
                .onClick((pluginUI, item) -> context.openPreference(GeminiProviderPreference.class));

        builder.addText("OpenAI GPT")
                .summary(getProviderStatusSummary("openai"))
                .onClick((pluginUI, item) -> context.openPreference(OpenAIProviderPreference.class));

        builder.addText("Claude AI")
                .summary(getProviderStatusSummary("claude"))
                .onClick((pluginUI, item) -> context.openPreference(ClaudeProviderPreference.class));

        // ==================== Translation Settings ====================
        builder.addText("Translation")
                .summary("Engine, timeout and retry settings");

        builder.addList("Default AI Engine", GeminiConstants.PREF_DEFAULT_ENGINE)
                .summary("Choose which AI provider to use by default")
                .addItem("Gemini (Fast & Free)", GeminiConstants.ENGINE_GEMINI)
                .addItem("OpenAI GPT (Powerful)", GeminiConstants.ENGINE_OPENAI)
                .addItem("Claude (Balanced)", GeminiConstants.ENGINE_CLAUDE);

        builder.addInput("Request Timeout (ms)", GeminiConstants.PREF_TIMEOUT)
                .defaultValue(String.valueOf(GeminiConstants.DEFAULT_TIMEOUT))
                .summary("Maximum wait time for API response")
                .valueAsSummary()
                .inputType(InputType.TYPE_CLASS_NUMBER);

        builder.addInput("Max Retry Attempts", GeminiConstants.PREF_MAX_RETRIES)
                .defaultValue(String.valueOf(GeminiConstants.DEFAULT_MAX_RETRIES))
                .summary("Number of retry attempts on failures")
                .valueAsSummary()
                .inputType(InputType.TYPE_CLASS_NUMBER);

        // ==================== Context & Tone (Simplified) ====================
        builder.addText("Context & Tone")
                .summary("Help AI understand your app for better translations");

        builder.addText("Quick Presets")
            .summary("Apply ready-made context + tone combinations")
            .onClick((pluginUI, item) -> showCombinedPresetsDialog(pluginUI));

        builder.addInput("Tone & Voice", GeminiConstants.PREF_CONTEXT_TONE)
            .summary("Writing style: friendly, formal, playful, technical...")
                .defaultValue(GeminiConstants.DEFAULT_CONTEXT_TONE)
                .valueAsSummary();

        builder.addInput("App Description", GeminiConstants.PREF_CONTEXT_APP_NAME)
                .summary("App name and type (e.g. 'MyApp - Shopping')")
                .valueAsSummary();

        builder.addInput("Target Audience", GeminiConstants.PREF_CONTEXT_AUDIENCE)
                .summary("Who uses your app (e.g. 'teenagers', 'developers')")
                .valueAsSummary();

        builder.addInput("Extra Notes", GeminiConstants.PREF_CONTEXT_NOTES)
            .summary("Special rules: locale format, forbidden words, etc.")
            .valueAsSummary();

        // ==================== Tools ====================
        builder.addText("Tools")
                .summary("Diagnostics and debugging");

        builder.addText("Provider Status")
                .summary("View all providers health at a glance")
                .onClick((pluginUI, item) -> showDashboardCard(pluginUI));

        builder.addText("Test Active Provider")
                .summary("Quick test: " + getActiveProviderName())
                .onClick((pluginUI, item) -> showInteractiveProviderTest(pluginUI));

        builder.addText("View Logs")
                .summary("Open MT Manager log viewer")
                .onClick((pluginUI, item) -> context.openLogViewer());

        builder.addSwitch("Debug Logging", GeminiConstants.PREF_ENABLE_DEBUG)
                .defaultValue(GeminiConstants.DEFAULT_ENABLE_DEBUG)
                .summary("Record detailed request info to MT Manager logs");

        // ==================== About ====================
        builder.addText("About")
                .summary("");

        builder.addText("Plugin Version")
            .summary("v" + GeminiConstants.PLUGIN_VERSION_NAME)
            .onClick((pluginUI, item) -> handlePluginVersionTap(pluginUI));

        builder.addText("API Documentation")
            .summary("MT Plugin V3 demo & docs (Gitee)")
            .url("https://gitee.com/L-JINBIN/mt-plugin-v3-demo");

        builder.addText("Developer")
                .summary("Ilker Binzet")
                .url(GeminiConstants.DEVELOPER_GITHUB);

        // ==================== Preference Callbacks (SDK 3 Beta2+) ====================
        // Restore callbacks for dynamic UI control
        builder.onPreferenceChange((pluginUI, preferenceItem, newValue) -> {
            if (preferenceItem.getKey().equals(GeminiConstants.PREF_ENABLE_DEBUG)) {
                boolean debugEnabled = (boolean) newValue;
                if (debugEnabled) {
                    context.showToast("Debug logging enabled - Check MT Manager logs for details");
                }
            }
        });

        builder.onCreated((pluginUI, preferenceScreen) -> {
            // Future: Can add dynamic visibility/enabled state management here
            // Example: Disable quick test if no providers configured
        });
    }

    /**
     * Get display name for engine constant
     */
    private String getEngineDisplayName(String engine) {
        if (engine == null) return "Unknown";
        switch (engine) {
            case GeminiConstants.ENGINE_OPENAI: return "OpenAI GPT";
            case GeminiConstants.ENGINE_CLAUDE: return "Claude";
            default: return "Gemini";
        }
    }

    private void handlePluginVersionTap(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        if (context == null) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastVersionTapUptime > DEBUG_TAP_RESET_MS) {
            versionTapCount = 0;
        }
        versionTapCount++;
        lastVersionTapUptime = now;

        if (versionTapCount < DEBUG_TAP_THRESHOLD) {
            int remaining = DEBUG_TAP_THRESHOLD - versionTapCount;
            if (remaining > 0) {
                String message = remaining == 1
                        ? "1 tap away from debug tools"
                        : remaining + " taps away from debug tools";
                context.showToast(message);
            }
            return;
        }

        versionTapCount = 0;
        context.showToast("Debug tools unlocked");
        showDebugTools(pluginUI);
    }

    private void showDebugTools(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        if (pluginUI == null || preferences == null) {
            return;
        }
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
        if (diagnostics == null) {
            return "No diagnostics available";
        }
        if (!diagnostics.hasData) {
            if (diagnostics.fetchedAt <= 0) {
                return "Entries: 0\nStatus: Never fetched";
            }
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
            builder.append(" (" ).append(formatDuration(diagnostics.ageMs)).append(" ago)");
        }
        builder.append("\nStatus: ").append(diagnostics.expired ? "Expired" : "Fresh");
        return builder.toString();
    }

    private CharSequence formatTimestamp(long timestamp) {
        if (timestamp <= 0) {
            return "never";
        }
        return DateFormat.format("MMM d, HH:mm", timestamp);
    }

    private String formatDuration(long durationMs) {
        if (durationMs < 0) {
            return "unknown";
        }
        long seconds = durationMs / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remSeconds = seconds % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + remSeconds + "s";
        }
        return remSeconds + "s";
    }

    private void toggleModelCacheBypass() {
        if (preferences == null) {
            return;
        }
        boolean disabled = preferences.getBoolean(GeminiConstants.PREF_DEBUG_DISABLE_MODEL_CACHE, false);
        preferences.edit().putBoolean(GeminiConstants.PREF_DEBUG_DISABLE_MODEL_CACHE, !disabled).apply();
    }

    private void clearAllModelCaches() {
        if (preferences == null) {
            return;
        }
        ModelCatalogManager.clearModelCache(preferences, GeminiConstants.PREF_CACHE_GEMINI_MODELS);
        ModelCatalogManager.clearModelCache(preferences, GeminiConstants.PREF_CACHE_OPENAI_MODELS);
        ModelCatalogManager.clearModelCache(preferences, GeminiConstants.PREF_CACHE_CLAUDE_MODELS);
    }

    private String getActiveProviderName() {
        String engine = preferences.getString(GeminiConstants.PREF_DEFAULT_ENGINE, GeminiConstants.DEFAULT_ENGINE);
        switch (engine) {
            case GeminiConstants.ENGINE_OPENAI: return "OpenAI GPT-4o";
            case GeminiConstants.ENGINE_CLAUDE: return "Claude 3.5";
            default: return "Gemini AI";
        }
    }

    private String getProviderStatusSummary(String provider) {
        ProviderStatus status = getProviderStatus(provider);
        return status.icon + " " + status.title + " • " + status.detail;
    }

    private void showDashboardCard(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        ProviderStatus geminiStatus = getProviderStatus("gemini");
        ProviderStatus openaiStatus = getProviderStatus("openai");
        ProviderStatus claudeStatus = getProviderStatus("claude");
        ProviderStatus activeStatus = getActiveProviderStatus();
        String activeModel = getActiveModelName();

        int primaryTextColor = GeminiColorTokens.getPrimaryTextColor(pluginUI);
        int secondaryTextColor = GeminiColorTokens.getSecondaryTextColor(pluginUI);
        int activeCardBackground = GeminiColorTokens.getCardBackgroundEmphasizedColor(pluginUI);
        int geminiCardBackground = GeminiColorTokens.getCardBackgroundColor(pluginUI);
        int openAiCardBackground = GeminiColorTokens.getCardBackgroundColor(pluginUI);
        int claudeCardBackground = GeminiColorTokens.getCardBackgroundColor(pluginUI);

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
                    .backgroundColor(geminiCardBackground)
                    .children(row -> row
                        .addTextView().text(geminiStatus.icon).textSize(28).paddingRightDp(12)
                        .addVerticalLayout().children(col -> col
                            .addTextView().text(geminiStatus.displayName).bold().textColor(geminiStatus.getAccentColor(pluginUI))
                            .addTextView().text(geminiStatus.title).paddingTopDp(2).textColor(primaryTextColor)
                            .addTextView().text(geminiStatus.detail).paddingTopDp(2).textColor(secondaryTextColor)
                        )
                    )
                .addHorizontalLayout().paddingDp(12).marginBottomDp(8)
                    .backgroundColor(openAiCardBackground)
                    .children(row -> row
                        .addTextView().text(openaiStatus.icon).textSize(28).paddingRightDp(12)
                        .addVerticalLayout().children(col -> col
                            .addTextView().text(openaiStatus.displayName).bold().textColor(openaiStatus.getAccentColor(pluginUI))
                            .addTextView().text(openaiStatus.title).paddingTopDp(2).textColor(primaryTextColor)
                            .addTextView().text(openaiStatus.detail).paddingTopDp(2).textColor(secondaryTextColor)
                        )
                    )
                .addHorizontalLayout().paddingDp(12)
                    .backgroundColor(claudeCardBackground)
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

    private void showCombinedPresetsDialog(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        // Build combined list: context presets + separator + tone presets
        int totalItems = CONTEXT_PRESETS.length + 1 + TONE_PRESETS.length;
        CharSequence[] labels = new CharSequence[totalItems];
        
        // Context presets section
        for (int i = 0; i < CONTEXT_PRESETS.length; i++) {
            ContextPreset p = CONTEXT_PRESETS[i];
            labels[i] = "\uD83D\uDCCB " + p.title + "\n" + p.subtitle;
        }
        // Separator
        labels[CONTEXT_PRESETS.length] = "── Tone Only ──";
        // Tone presets
        for (int i = 0; i < TONE_PRESETS.length; i++) {
            TonePreset t = TONE_PRESETS[i];
            labels[CONTEXT_PRESETS.length + 1 + i] = "\uD83C\uDFA8 " + t.name + "\n" + t.description;
        }

        pluginUI.buildDialog()
                .setTitle("Quick Presets")
                .setItems(labels, (dialog, which) -> {
                    if (which < CONTEXT_PRESETS.length) {
                        // Apply full context preset (sets all fields)
                        applyContextPreset(CONTEXT_PRESETS[which]);
                        context.showToast(CONTEXT_PRESETS[which].title + " applied");
                    } else if (which > CONTEXT_PRESETS.length) {
                        // Apply tone-only preset
                        TonePreset tone = TONE_PRESETS[which - CONTEXT_PRESETS.length - 1];
                        preferences.edit()
                                .putString(GeminiConstants.PREF_CONTEXT_TONE, tone.storedValue)
                                .apply();
                        context.showToast("Tone: " + tone.name);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("{cancel}", null)
                .show();
    }

    private void showContextPresetsDialog(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        CharSequence[] presetLabels = new CharSequence[CONTEXT_PRESETS.length];
        for (int i = 0; i < CONTEXT_PRESETS.length; i++) {
            ContextPreset preset = CONTEXT_PRESETS[i];
            presetLabels[i] = preset.title + "\n" + preset.subtitle;
        }

        pluginUI.buildDialog()
                .setTitle("Context Playbooks")
                .setItems(presetLabels, (dialog, which) -> {
                    ContextPreset preset = CONTEXT_PRESETS[which];
                    applyContextPreset(preset);
                    context.showToast(preset.title + " preset applied. Re-open settings to confirm.");
                    dialog.dismiss();
                })
                .setNegativeButton("{cancel}", null)
                .show();
    }

    private void applyContextPreset(ContextPreset preset) {
        SharedPreferences.Editor editor = preferences.edit();
        // Merge appName + appType into single "App Description" field
        String appDesc = preset.appName;
        if (preset.appType != null && !preset.appType.isEmpty()) {
            appDesc = appDesc + " - " + preset.appType;
        }
        editor.putString(GeminiConstants.PREF_CONTEXT_APP_NAME, appDesc);
        editor.putString(GeminiConstants.PREF_CONTEXT_AUDIENCE, preset.audience);
        editor.putString(GeminiConstants.PREF_CONTEXT_TONE, preset.tone);
        editor.putString(GeminiConstants.PREF_CONTEXT_NOTES, preset.notes);
        editor.apply();
    }

    private void showTonePresetsDialog(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        CharSequence[] toneLabels = new CharSequence[TONE_PRESETS.length];
        for (int i = 0; i < TONE_PRESETS.length; i++) {
            TonePreset preset = TONE_PRESETS[i];
            toneLabels[i] = preset.name + "\n" + preset.description;
        }

        pluginUI.buildDialog()
                .setTitle("Tone Presets")
                .setItems(toneLabels, (dialog, which) -> {
                    TonePreset preset = TONE_PRESETS[which];
                    preferences.edit()
                            .putString(GeminiConstants.PREF_CONTEXT_TONE, preset.storedValue)
                            .apply();
                    context.showToast("Tone set to " + preset.name);
                    dialog.dismiss();
                })
                .setNegativeButton("{cancel}", null)
                .show();
    }

    private ProviderStatus getProviderStatus(String providerKey) {
        synchronized (providerStatusCache) {
            ProviderStatus cached = providerStatusCache.get(providerKey);
            if (cached != null) {
                return cached;
            }
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
        if (keyValue == null) {
            keyValue = "";
        }

        if (keyValue.isEmpty()) {
            return new ProviderStatus(
                providerKey,
                displayName,
                icon,
                "Not configured",
                "Add your API key to activate " + displayName,
                "neutral"
            );
        }

        if (!keyPattern.matcher(keyValue).matches()) {
            return new ProviderStatus(
                providerKey,
                displayName,
                icon,
                "Invalid API key",
                "The key format looks wrong. Re-copy it from the provider dashboard.",
                "error"
            );
        }

        return new ProviderStatus(
            providerKey,
            displayName,
            icon,
            "Ready to use",
            "Key active (" + formatKeyHint(keyValue) + ")",
            "ready"
        );
    }

    private ProviderStatus getActiveProviderStatus() {
        String engine = preferences.getString(GeminiConstants.PREF_DEFAULT_ENGINE, GeminiConstants.DEFAULT_ENGINE);
        switch (engine) {
            case GeminiConstants.ENGINE_OPENAI:
                return getProviderStatus("openai");
            case GeminiConstants.ENGINE_CLAUDE:
                return getProviderStatus("claude");
            default:
                return getProviderStatus("gemini");
        }
    }

    private String getActiveModelName() {
        return preferences.getString(GeminiConstants.PREF_MODEL_NAME, GeminiConstants.DEFAULT_MODEL);
    }

    private void ensurePreferenceListenerRegistered() {
        if (preferences != null && !preferenceListenerRegistered) {
            preferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
            preferenceListenerRegistered = true;
        }
    }

    private String mapPreferenceToProviderKey(String prefKey) {
        if (GeminiConstants.PREF_API_KEY.equals(prefKey)) {
            return "gemini";
        }
        if (GeminiConstants.PREF_OPENAI_API_KEY.equals(prefKey)) {
            return "openai";
        }
        if (GeminiConstants.PREF_CLAUDE_API_KEY.equals(prefKey)) {
            return "claude";
        }
        return null;
    }

    private String formatKeyHint(String key) {
        if (key == null || key.isEmpty()) {
            return "••••";
        }
        int visible = Math.min(4, key.length());
        return "••••" + key.substring(key.length() - visible);
    }

    // Color helper methods removed - now using GeminiColorTokens
    // calculateLuminance, blendColors, resolveDashboardSecondaryColor moved to GeminiColorTokens
}
