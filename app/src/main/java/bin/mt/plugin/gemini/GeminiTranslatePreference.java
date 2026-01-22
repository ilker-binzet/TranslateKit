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
 * Main preference screen for AI Translation Hub
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

    private static final int COLOR_STATUS_NEUTRAL = 0xFFECEFF1;
    private static final int COLOR_STATUS_WARNING = 0xFFFFF8E1;
    private static final int COLOR_STATUS_ERROR = 0xFFFFEBEE;
    private static final int COLOR_STATUS_READY = 0xFFE8F5E9;
    private static final int COLOR_ACTIVE_CARD = 0xFFE3F2FD;
    private static final int COLOR_BRAND_GEMINI = 0xFF1A73E8;
    private static final int COLOR_BRAND_OPENAI = 0xFF0B8F6A;
    private static final int COLOR_BRAND_CLAUDE = 0xFFB55F3B;
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
        final int backgroundColor;
        final int accentColor;

        ProviderStatus(String providerKey, String displayName, String icon, String title,
                       String detail, int backgroundColor, int accentColor) {
            this.providerKey = providerKey;
            this.displayName = displayName;
            this.icon = icon;
            this.title = title;
            this.detail = detail;
            this.backgroundColor = backgroundColor;
            this.accentColor = accentColor;
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

        // ==================== Quick Dashboard ====================
        builder.addHeader("📊 Dashboard");
        
        builder.addText("Quick Status")
                .summary("View all providers health and active configuration")
                .onClick((pluginUI, item) -> showDashboardCard(pluginUI));

        // ==================== Quick Actions ====================
        builder.addHeader("⚡ Quick Actions");

        builder.addText("Test Active Provider")
                .summary("Run a quick status check for " + getActiveProviderName())
                .onClick((pluginUI, item) -> showInteractiveProviderTest(pluginUI));

        builder.addText("View Plugin Logs")
                .summary("Open MT Manager log viewer")
                .onClick((pluginUI, item) -> context.openLogViewer());

        // ==================== AI Providers ====================
        builder.addHeader("🤖 AI Providers");

        builder.addText("✨ Gemini AI")
                .summary(getProviderStatusSummary("gemini"))
                .onClick((pluginUI, item) -> context.openPreference(GeminiProviderPreference.class));

        builder.addText("🧠 OpenAI GPT")
                .summary(getProviderStatusSummary("openai"))
                .onClick((pluginUI, item) -> context.openPreference(OpenAIProviderPreference.class));

        builder.addText("🎭 Claude AI")
                .summary(getProviderStatusSummary("claude"))
                .onClick((pluginUI, item) -> context.openPreference(ClaudeProviderPreference.class));

        // ==================== Translation Settings ====================
        builder.addHeader("⚙️ Translation Settings");

        builder.addList("Default AI Engine", GeminiConstants.PREF_DEFAULT_ENGINE)
                .defaultValue(GeminiConstants.DEFAULT_ENGINE)
                .summary("Choose which AI provider to use by default")
                .addItem("Gemini (Fast & Free)", GeminiConstants.ENGINE_GEMINI)
                .addItem("OpenAI GPT-4o", GeminiConstants.ENGINE_OPENAI)
                .addItem("Claude 3.5", GeminiConstants.ENGINE_CLAUDE);

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

        // ==================== Context & Tone ====================
        builder.addHeader("🎨 Context & Tone");

        builder.addText("Context Playbooks")
            .summary("Apply curated presets for apps, docs, support, marketing and more")
            .onClick((pluginUI, item) -> showContextPresetsDialog(pluginUI));

        builder.addText("Tone Presets")
            .summary("Pick a consistent voice (friendly, legal, marketing, support, technical)")
            .onClick((pluginUI, item) -> showTonePresetsDialog(pluginUI));

        builder.addInput("App Name", GeminiConstants.PREF_CONTEXT_APP_NAME)
                .summary("Your application or project name")
                .valueAsSummary();

        builder.addInput("App Type", GeminiConstants.PREF_CONTEXT_APP_TYPE)
                .summary("Domain or category (e.g., Shopping, Game)")
                .valueAsSummary();

        builder.addInput("Target Audience", GeminiConstants.PREF_CONTEXT_AUDIENCE)
                .summary("Who will use your app")
                .valueAsSummary();

        builder.addInput("Tone & Voice", GeminiConstants.PREF_CONTEXT_TONE)
            .summary("Writing style (clear, playful, formal). Use tone presets for inspiration.")
                .defaultValue(GeminiConstants.DEFAULT_CONTEXT_TONE)
                .valueAsSummary();

        builder.addInput("Custom Notes", GeminiConstants.PREF_CONTEXT_NOTES)
            .summary("Keywords, locale rules, formatting hints for translators")
            .valueAsSummary();

        // ==================== Debug & Advanced ====================
        builder.addHeader("🔧 Debug & Advanced");

        builder.addSwitch("Enable Debug Logging", GeminiConstants.PREF_ENABLE_DEBUG)
                .defaultValue(GeminiConstants.DEFAULT_ENABLE_DEBUG)
                .summary("Record detailed request info to MT Manager logs");

        // ==================== About ====================
        builder.addHeader("ℹ️ About");

        builder.addText("Plugin Version")
            .summary(GeminiConstants.PLUGIN_VERSION_NAME)
            .onClick((pluginUI, item) -> handlePluginVersionTap(pluginUI));

        builder.addText("API Documentation")
            .summary("MT Plugin V3 demo & docs (Gitee)")
            .url("https://gitee.com/L-JINBIN/mt-plugin-v3-demo");

        builder.addText("Developer")
                .summary("Ilker Binzet")
                .url(GeminiConstants.DEVELOPER_GITHUB);

        // ==================== SDK Beta2: Preference Callbacks ====================
        // onPreferenceChange: React to preference changes in real-time
        builder.onPreferenceChange((pluginUI, preferenceItem, newValue) -> {
            String key = preferenceItem.getKey();
            if (key == null) return;
            
            switch (key) {
                case GeminiConstants.PREF_DEFAULT_ENGINE -> {
                    // Invalidate provider status cache when engine changes
                    synchronized (providerStatusCache) {
                        providerStatusCache.clear();
                    }
                    String engineName = getEngineDisplayName((String) newValue);
                    pluginUI.showToast("Switched to " + engineName);
                }
                case GeminiConstants.PREF_ENABLE_DEBUG -> {
                    boolean enabled = (boolean) newValue;
                    pluginUI.showToast(enabled ? "Debug logging enabled" : "Debug logging disabled");
                }
            }
        });

        // onCreated: Initialize UI state when preference screen is created
        builder.onCreated((pluginUI, preferenceScreen) -> {
            // Preference screen initialized - ready for user interaction
        });
    }

    /**
     * Get display name for engine constant
     */
    private String getEngineDisplayName(String engine) {
        if (engine == null) return "Unknown";
        switch (engine) {
            case GeminiConstants.ENGINE_OPENAI: return "OpenAI GPT-4o";
            case GeminiConstants.ENGINE_CLAUDE: return "Claude 3.5";
            default: return "Gemini AI";
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

        boolean darkTheme = pluginUI.isDarkTheme();
        int primaryTextColor = darkTheme ? Color.WHITE : pluginUI.colorText();
        int secondaryTextColor = resolveDashboardSecondaryColor(pluginUI, primaryTextColor, darkTheme);
        int cardColor = adaptCardBackground(COLOR_STATUS_NEUTRAL, darkTheme);
        String ttlSummary = "Entries expire after " + formatDuration(GeminiConstants.MODEL_CACHE_TTL_MS);

        bin.mt.plugin.api.ui.PluginView view = pluginUI
            .buildVerticalLayout()
            .addTextView().text("Hidden Debug Menu").bold().textSize(18).textColor(primaryTextColor)
            .addTextView().text("Inspect cached model catalogs, TTL status and cache-bypass controls.")
                .paddingTopDp(4).textColor(secondaryTextColor)
            .addTextView().text(ttlSummary).paddingTopDp(2).textColor(secondaryTextColor)
            .addTextView().height(1).widthMatchParent().backgroundColor(pluginUI.colorDivider()).marginVerticalDp(12)
            .addTextView().text("Catalog Diagnostics").bold().textSize(16).textColor(primaryTextColor)
            .addVerticalLayout().paddingTopDp(8).children(column -> column
                .addVerticalLayout().paddingDp(12).marginBottomDp(10).backgroundColor(cardColor).children(section -> section
                    .addTextView().text("Gemini Catalog").bold().textColor(COLOR_BRAND_GEMINI)
                    .addTextView().text(formatCacheDiagnostics(geminiDiagnostics)).paddingTopDp(4).textColor(secondaryTextColor)
                )
                .addVerticalLayout().paddingDp(12).marginBottomDp(10).backgroundColor(cardColor).children(section -> section
                    .addTextView().text("OpenAI Catalog").bold().textColor(COLOR_BRAND_OPENAI)
                    .addTextView().text(formatCacheDiagnostics(openAiDiagnostics)).paddingTopDp(4).textColor(secondaryTextColor)
                )
                .addVerticalLayout().paddingDp(12).marginBottomDp(10).backgroundColor(cardColor).children(section -> section
                    .addTextView().text("Claude Catalog").bold().textColor(COLOR_BRAND_CLAUDE)
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

        boolean isDarkTheme = pluginUI.isDarkTheme();
        int primaryTextColor = isDarkTheme ? Color.WHITE : pluginUI.colorText();
        int secondaryTextColor = resolveDashboardSecondaryColor(pluginUI, primaryTextColor, isDarkTheme);
        int activeCardBackground = adaptCardBackground(COLOR_ACTIVE_CARD, isDarkTheme);
        int geminiCardBackground = adaptCardBackground(geminiStatus.backgroundColor, isDarkTheme);
        int openAiCardBackground = adaptCardBackground(openaiStatus.backgroundColor, isDarkTheme);
        int claudeCardBackground = adaptCardBackground(claudeStatus.backgroundColor, isDarkTheme);

        bin.mt.plugin.api.ui.PluginView view = pluginUI
            .buildVerticalLayout()
            .addTextView().text("AI Provider Overview").bold().textSize(18).paddingBottomDp(8).textColor(primaryTextColor)

            // Active provider card
            .addVerticalLayout().paddingDp(16).backgroundColor(activeCardBackground).children(subBuilder -> subBuilder
                .addTextView().text("Active Provider").bold().textColor(activeStatus.accentColor)
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
                            .addTextView().text(geminiStatus.displayName).bold().textColor(geminiStatus.accentColor)
                            .addTextView().text(geminiStatus.title).paddingTopDp(2).textColor(primaryTextColor)
                            .addTextView().text(geminiStatus.detail).paddingTopDp(2).textColor(secondaryTextColor)
                        )
                    )
                .addHorizontalLayout().paddingDp(12).marginBottomDp(8)
                    .backgroundColor(openAiCardBackground)
                    .children(row -> row
                        .addTextView().text(openaiStatus.icon).textSize(28).paddingRightDp(12)
                        .addVerticalLayout().children(col -> col
                            .addTextView().text(openaiStatus.displayName).bold().textColor(openaiStatus.accentColor)
                            .addTextView().text(openaiStatus.title).paddingTopDp(2).textColor(primaryTextColor)
                            .addTextView().text(openaiStatus.detail).paddingTopDp(2).textColor(secondaryTextColor)
                        )
                    )
                .addHorizontalLayout().paddingDp(12)
                    .backgroundColor(claudeCardBackground)
                    .children(row -> row
                        .addTextView().text(claudeStatus.icon).textSize(28).paddingRightDp(12)
                        .addVerticalLayout().children(col -> col
                            .addTextView().text(claudeStatus.displayName).bold().textColor(claudeStatus.accentColor)
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
        editor.putString(GeminiConstants.PREF_CONTEXT_APP_NAME, preset.appName);
        editor.putString(GeminiConstants.PREF_CONTEXT_APP_TYPE, preset.appType);
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
        int accentColor = COLOR_BRAND_GEMINI;

        switch (providerKey) {
            case "openai":
                prefKey = GeminiConstants.PREF_OPENAI_API_KEY;
                keyPattern = PATTERN_OPENAI_API_KEY;
                displayName = "OpenAI GPT-4o";
                icon = "🧠";
                accentColor = COLOR_BRAND_OPENAI;
                break;
            case "claude":
                prefKey = GeminiConstants.PREF_CLAUDE_API_KEY;
                keyPattern = PATTERN_CLAUDE_API_KEY;
                displayName = "Claude 3.5";
                icon = "🎭";
                accentColor = COLOR_BRAND_CLAUDE;
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
                COLOR_STATUS_NEUTRAL,
                accentColor
            );
        }

        if (!keyPattern.matcher(keyValue).matches()) {
            return new ProviderStatus(
                providerKey,
                displayName,
                icon,
                "Invalid API key",
                "The key format looks wrong. Re-copy it from the provider dashboard.",
                COLOR_STATUS_ERROR,
                accentColor
            );
        }

        return new ProviderStatus(
            providerKey,
            displayName,
            icon,
            "Ready to use",
            "Key active (" + formatKeyHint(keyValue) + ")",
            COLOR_STATUS_READY,
            accentColor
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

    private int resolveDashboardSecondaryColor(bin.mt.plugin.api.ui.PluginUI pluginUI,
                                               int primaryColor,
                                               boolean darkTheme) {
        int secondary = pluginUI.colorTextSecondary();
        if (!darkTheme) {
            return secondary;
        }
        float contrast = Math.abs(calculateLuminance(primaryColor) - calculateLuminance(secondary));
        float blendRatio = contrast < 0.4f ? 0.6f : 0.45f;
        return blendColors(secondary, primaryColor, blendRatio);
    }

    private int adaptCardBackground(int baseColor, boolean darkTheme) {
        if (!darkTheme) {
            return baseColor;
        }
        return blendColors(baseColor, Color.BLACK, 0.65f);
    }

    private static int blendColors(int startColor, int endColor, float ratio) {
        float inverseRatio = 1f - ratio;
        int a = Math.round(Color.alpha(startColor) * inverseRatio + Color.alpha(endColor) * ratio);
        int r = Math.round(Color.red(startColor) * inverseRatio + Color.red(endColor) * ratio);
        int g = Math.round(Color.green(startColor) * inverseRatio + Color.green(endColor) * ratio);
        int b = Math.round(Color.blue(startColor) * inverseRatio + Color.blue(endColor) * ratio);
        return Color.argb(a, r, g, b);
    }

    private static float calculateLuminance(int color) {
        double r = Color.red(color) / 255.0;
        double g = Color.green(color) / 255.0;
        double b = Color.blue(color) / 255.0;
        r = r <= 0.03928 ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        g = g <= 0.03928 ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        b = b <= 0.03928 ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);
        return (float)(0.2126 * r + 0.7152 * g + 0.0722 * b);
    }
}
