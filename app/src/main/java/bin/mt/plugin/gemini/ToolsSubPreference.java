package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import android.os.Environment;
import android.os.SystemClock;
import android.text.format.DateFormat;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import bin.mt.json.PrettyPrint;
import bin.mt.json.JSONObject;

import bin.mt.plugin.api.ui.PluginEditText;
import bin.mt.plugin.api.ui.PluginView;

import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.preference.PluginPreference;
import bin.mt.plugin.common.JSONCompat;
import bin.mt.plugin.provider.Provider;
import bin.mt.plugin.provider.Providers;

/**
 * Sub-preference screen for Tools & Diagnostics.
 * Contains: Provider Status dashboard, Interactive Provider Test,
 * View Logs, Debug Logging toggle, and hidden Debug Tools menu.
 */
public class ToolsSubPreference implements PluginPreference {

    private PluginContext context;
    private SharedPreferences preferences;
    private final Map<String, ProviderStatus> providerStatusCache = new HashMap<>();

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

        /** Green when ready, red when broken — the outcome, not the brand. */
        int getStatusTextColor(bin.mt.plugin.api.ui.PluginUI pluginUI) {
            return GeminiColorTokens.getStatusColor(pluginUI, statusType);
        }
    }

    @Override
    public void onBuild(PluginContext context, Builder builder) {
        this.context = context;
        this.preferences = context.getPreferences();

        synchronized (providerStatusCache) {
            providerStatusCache.clear();
        }

        // ==================== Provider Status ====================
        builder.addText(str("{tools_provider_status}"))
                .summary(str("{tools_provider_status_summary}"))
                .onClick((pluginUI, item) -> showDashboardCard(pluginUI));

        // ==================== Test Active Provider ====================
        builder.addText(str("{tools_test_provider}"))
                .summary(str("{tools_quick_test}") + ": " + Providers.selected(preferences).displayName)
                .onClick((pluginUI, item) -> showInteractiveProviderTest(pluginUI));

        // ==================== View Logs ====================
        builder.addText(str("{tools_view_logs}"))
                .summary(str("{tools_view_logs_summary}"))
                .onClick((pluginUI, item) -> context.openLogViewer());

        // ==================== Debug Logging ====================
        builder.addSwitch(str("{tools_debug_logging}"), GeminiConstants.PREF_ENABLE_DEBUG)
                .defaultValue(GeminiConstants.DEFAULT_ENABLE_DEBUG)
                .summary(str("{tools_debug_logging_summary}"));

        // ==================== Export / Copy Debug Log ====================
        builder.addText(str("{tools_export_log}"))
                .summary(str("{tools_export_log_summary}"))
                .onClick((pluginUI, item) -> exportDebugLog());

        builder.addText(str("{tools_copy_log}"))
                .summary(str("{tools_copy_log_summary}"))
                .onClick((pluginUI, item) -> copyDebugLog());

        // ==================== Export Settings ====================
        builder.addText(str("{tools_export}"))
                .summary(str("{tools_export_summary}"))
                .onClick((pluginUI, item) -> showExportDialog(pluginUI));

        // ==================== Import Settings ====================
        builder.addText(str("{tools_import}"))
                .summary(str("{tools_import_summary}"))
                .onClick((pluginUI, item) -> showImportDialog(pluginUI));

        // ==================== Hidden Debug Access ====================
        builder.addText(str("{tools_plugin_version}"))
            .summary("v" + GeminiConstants.PLUGIN_VERSION_NAME)
            .onClick((pluginUI, item) -> handlePluginVersionTap(pluginUI));

        // Preference change callback
        builder.onPreferenceChange((pluginUI, preferenceItem, newValue) -> {
            if (preferenceItem.getKey().equals(GeminiConstants.PREF_ENABLE_DEBUG)) {
                boolean debugEnabled = (boolean) newValue;
                if (debugEnabled) {
                    context.showToast(str("{msg_debug_enabled}"));
                }
            }
        });
    }

    /** Localized text for {@code key}; the language packs live in assets. */
    private String str(String key) {
        return context.getString(key);
    }

    // ==================== Debug Log Export ====================

    /**
     * Copies the mirrored debug log into the public Downloads folder. The
     * plugin runs inside MT Manager, a file manager with all-files access, so
     * the plain File API reaches it; when it does not, the clipboard is the
     * fallback rather than an error.
     */
    private void exportDebugLog() {
        String log = TranslationDebugLogger.readLog(context);
        if (log.isEmpty()) {
            context.showToast(str("{msg_log_empty}"));
            return;
        }
        try {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            String stamp = DateFormat.format("yyyyMMdd-HHmm", System.currentTimeMillis()).toString();
            File out = new File(dir, "TranslateKit-" + stamp + ".log");
            Files.write(out.toPath(), log.getBytes(StandardCharsets.UTF_8));
            context.showToastL(str("{msg_log_exported}") + " " + out.getAbsolutePath());
        } catch (Exception e) {
            context.log("[TranslateKit] Log export failed: " + e.getMessage());
            if (context.setClipboardText(log)) {
                context.showToast(str("{msg_log_export_failed_copied}"));
            } else {
                context.showToast(str("{msg_clipboard_failed}"));
            }
        }
    }

    private void copyDebugLog() {
        String log = TranslationDebugLogger.readLog(context);
        if (log.isEmpty()) {
            context.showToast(str("{msg_log_empty}"));
        } else if (context.setClipboardText(log)) {
            context.showToast(str("{msg_log_copied}"));
        } else {
            context.showToast(str("{msg_clipboard_failed}"));
        }
    }

    // ==================== Exportable Preference Keys ====================

    /** Non-sensitive preference keys eligible for export. API keys are always excluded. */
    private static final Set<String> EXPORTABLE_KEYS = new HashSet<>(Arrays.asList(
            GeminiConstants.PREF_DEFAULT_ENGINE,
            GeminiConstants.PREF_MODEL_NAME,
            GeminiConstants.PREF_TIMEOUT,
            GeminiConstants.PREF_MAX_RETRIES,
            GeminiConstants.PREF_TEMPERATURE,
            GeminiConstants.PREF_ENABLE_CACHE,
            GeminiConstants.PREF_BATCH_ENABLED,
            GeminiConstants.PREF_BATCH_SIZE,
            GeminiConstants.PREF_BATCH_MAX_CHARS,
            GeminiConstants.PREF_CONTEXT_APP_NAME,
            GeminiConstants.PREF_CONTEXT_APP_TYPE,
            GeminiConstants.PREF_CONTEXT_AUDIENCE,
            GeminiConstants.PREF_CONTEXT_TONE,
            GeminiConstants.PREF_CONTEXT_NOTES,
            GeminiConstants.PREF_DEFAULT_TARGET_LANG,
            Languages.PREF_ENABLED_LANGUAGES,
            GeminiConstants.PREF_ENABLE_DEBUG,
            GeminiConstants.PREF_OPENAI_MODEL,
            GeminiConstants.PREF_OPENAI_ENDPOINT,
            GeminiConstants.PREF_CLAUDE_MODEL,
            GeminiConstants.PREF_CLAUDE_ENDPOINT,
            GeminiConstants.PREF_OPENROUTER_MODEL,
            GeminiConstants.PREF_OPENROUTER_ENDPOINT,
            Providers.PREF_CUSTOM_PROVIDERS
    ));

    /** Keys stored as boolean (all others are treated as String). */
    private static final Set<String> BOOLEAN_KEYS = new HashSet<>(Arrays.asList(
            GeminiConstants.PREF_ENABLE_CACHE,
            GeminiConstants.PREF_BATCH_ENABLED,
            GeminiConstants.PREF_ENABLE_DEBUG
    ));

    // ==================== Export Dialog ====================

    private void showExportDialog(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        JSONObject json = new JSONObject();
        try {
            json.put("preset_version", 1);
            json.put("plugin_version", GeminiConstants.PLUGIN_VERSION_NAME);

            JSONObject settings = new JSONObject();
            int count = 0;
            for (String key : EXPORTABLE_KEYS) {
                if (BOOLEAN_KEYS.contains(key)) {
                    if (preferences.contains(key)) {
                        settings.put(key, preferences.getBoolean(key, false));
                        count++;
                    }
                } else {
                    String value = preferences.getString(key, null);
                    if (Providers.PREF_CUSTOM_PROVIDERS.equals(key) && value != null) {
                        // A user-defined endpoint keeps its key in the same blob
                        // as its name and URL. Presets are meant to be shared,
                        // so the name, URL and model travel and the key does not.
                        value = Providers.withoutKeys(value);
                    }
                    if (value != null && !value.isEmpty()) {
                        settings.put(key, value);
                        count++;
                    }
                }
            }
            json.put("settings", settings);

            String jsonText = json.toString(PrettyPrint.indentWithSpaces(2));

            // Also copy to clipboard automatically — the user can paste anywhere
            // (file, email, etc.) without first opening the dialog.
            // setClipboardText(text, null) suppresses the default success toast;
            // we show our own so the user knows the export succeeded.
            try {
                context.setClipboardText(jsonText, null);
                context.showToast("✅ " + count + " " + str("{msg_settings_copied}"));
            } catch (Exception clipErr) {
                // Fallback: just open the dialog so the user can copy manually
                context.showToast("⚠️ " + str("{msg_clipboard_failed}"));
            }

            pluginUI.buildDialog()
                    .setTitle(str("{tools_export}") + " (" + count + ")")
                    .setView(pluginUI.buildVerticalLayout()
                            .addTextView().text(str("{tools_export_hint}"))
                            .textColor(GeminiColorTokens.getSecondaryTextColor(pluginUI))
                            .textSize(13)
                            .addEditBox("exportJson").text(jsonText)
                            .minLines(6).maxLines(12).textSize(12)
                            .softWrap(PluginEditText.SOFT_WRAP_KEEP_WORD)
                            .build())
                    .setPositiveButton(str("{btn_done}"), null)
                    .show();
        } catch (Exception e) {
            context.showToast(str("{msg_export_failed}") + ": " + e.getMessage());
        }
    }

    // ==================== Import Dialog ====================

    private void showImportDialog(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        PluginView dialogView = pluginUI.buildVerticalLayout()
                .addTextView().text(str("{tools_import_hint}"))
                .textColor(GeminiColorTokens.getSecondaryTextColor(pluginUI))
                .textSize(13)
                .addEditBox("importJson").hint("{ \"preset_version\": 1, ... }")
                .minLines(6).maxLines(12).textSize(12)
                .build();

        pluginUI.buildDialog()
                .setTitle(str("{tools_import}"))
                .setView(dialogView)
                .setPositiveButton(str("{btn_import}"), (dialog, which) -> {
                    PluginEditText editText = dialogView.requireViewById("importJson");
                    String input = editText.getText().toString().trim();
                    if (input.isEmpty()) {
                        context.showToast(str("{msg_no_json}"));
                        return;
                    }
                    applyPresetJson(input);
                })
                .setNegativeButton("{cancel}", null)
                .show();
    }

    private void applyPresetJson(String jsonText) {
        try {
            JSONObject json = new JSONObject(jsonText);

            if (!json.contains("preset_version") || !json.contains("settings")) {
                context.showToast(str("{msg_invalid_preset}"));
                return;
            }

            JSONObject settings = json.getJSONObject("settings");
            SharedPreferences.Editor editor = preferences.edit();
            int applied = 0;

            java.util.Iterator<String> keys = settings.names().iterator();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!EXPORTABLE_KEYS.contains(key)) {
                    continue; // Skip unknown or sensitive keys
                }
                if (BOOLEAN_KEYS.contains(key)) {
                    editor.putBoolean(key, settings.getBoolean(key));
                } else if (Providers.PREF_CUSTOM_PROVIDERS.equals(key)) {
                    mergeCustomProviders(settings.getString(key));
                } else {
                    editor.putString(key, settings.getString(key));
                }
                applied++;
            }
            editor.apply();
            context.showToast(applied + " " + str("{msg_settings_restored}"));
        } catch (Exception e) {
            context.showToast(str("{msg_import_failed}") + ": " + e.getMessage());
        }
    }

    /**
     * Applies the custom endpoints from a preset, keeping any key already
     * stored under the same endpoint name.
     *
     * <p>Export strips keys, so importing a preset back onto the device it came
     * from would otherwise erase them. Writes on its own rather than through the
     * caller's editor — the entries are re-serialised, not copied verbatim.
     */
    private void mergeCustomProviders(String blob) {
        Map<String, String> existingKeys = new HashMap<>();
        for (JSONObject entry : Providers.customEntries(preferences)) {
            existingKeys.put(JSONCompat.optString(entry, "name", ""),
                    JSONCompat.optString(entry, "apiKey", ""));
        }

        java.util.List<JSONObject> merged = new ArrayList<>();
        for (JSONObject entry : Providers.parseEntries(blob)) {
            String name = JSONCompat.optString(entry, "name", "");
            String apiKey = JSONCompat.optString(entry, "apiKey", "");
            if (apiKey.isEmpty()) {
                String kept = existingKeys.get(name);
                if (kept != null) {
                    apiKey = kept;
                }
            }
            merged.add(Providers.newCustomEntry(name,
                    JSONCompat.optString(entry, "baseUrl", ""), apiKey,
                    JSONCompat.optString(entry, "model", "")));
        }
        Providers.saveCustomEntries(preferences, merged);
    }

    // ==================== Dashboard Dialog ====================

    private void showDashboardCard(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        // Every configured provider, including OpenRouter and any user-defined
        // endpoint — the panel used to name three of them literally.
        java.util.List<ProviderStatus> health = new ArrayList<>();
        for (Provider p : Providers.all(preferences)) {
            health.add(getProviderStatus(p));
        }
        Provider active = Providers.selected(preferences);
        ProviderStatus activeStatus = getProviderStatus(active);
        // A freshly added custom endpoint has no model until one is picked.
        String activeModel = active.model == null || active.model.isEmpty()
                ? str("{dash_not_set}") : active.model;

        int primaryTextColor = GeminiColorTokens.getPrimaryTextColor(pluginUI);
        int secondaryTextColor = GeminiColorTokens.getSecondaryTextColor(pluginUI);
        int activeCardBackground = GeminiColorTokens.getCardBackgroundEmphasizedColor(pluginUI);
        int cardBackground = GeminiColorTokens.getCardBackgroundColor(pluginUI);

        bin.mt.plugin.api.ui.PluginView view = pluginUI
            .buildVerticalLayout()
            .addTextView().text(str("{dash_title}")).bold().textSize(18).paddingBottomDp(8).textColor(primaryTextColor)

            // Active provider card
            .addVerticalLayout().paddingDp(16).backgroundColor(activeCardBackground).children(subBuilder -> subBuilder
                .addTextView().text(str("{dash_active}")).bold().textColor(activeStatus.getAccentColor(pluginUI))
                .addTextView().text(activeStatus.icon + " " + activeStatus.displayName).paddingTopDp(6).textSize(18).textColor(primaryTextColor)
                .addTextView().text(activeStatus.title).paddingTopDp(4).textColor(activeStatus.getStatusTextColor(pluginUI))
                .addTextView().text(activeStatus.detail).paddingTopDp(2).textColor(secondaryTextColor)
                .addTextView().text(str("{dash_model}") + ": " + activeModel).paddingTopDp(10).textColor(secondaryTextColor)
            )
            .addTextView().height(1).widthMatchParent().backgroundColor(pluginUI.colorDivider()).marginVerticalDp(12)

            .addTextView().text(str("{dash_health}")).bold().textSize(16).textColor(primaryTextColor)
            .addVerticalLayout().paddingTopDp(8).children(column -> {
                for (ProviderStatus s : health) {
                    column.addHorizontalLayout().paddingDp(12).marginBottomDp(8)
                        .backgroundColor(cardBackground)
                        .children(row -> row
                            .addTextView().text(s.icon).textSize(28).paddingRightDp(12)
                            .addVerticalLayout().children(col -> col
                                .addTextView().text(s.displayName).bold().textColor(s.getAccentColor(pluginUI))
                                .addTextView().text(s.title).paddingTopDp(2).textColor(s.getStatusTextColor(pluginUI))
                                .addTextView().text(s.detail).paddingTopDp(2).textColor(secondaryTextColor)
                            )
                        );
                }
            })
            .build();

        pluginUI.buildDialog()
            .setTitle(str("{dash_dialog}"))
            .setView(view)
            .setPositiveButton("{close}", null)
            .show();
    }

    // ==================== Interactive Provider Test ====================

    private void showInteractiveProviderTest(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        // The provider actually in use. This used to read the Gemini key
        // whenever the selection was neither OpenAI nor Claude, so picking
        // OpenRouter or a custom endpoint tested the wrong key entirely.
        Provider provider = Providers.selected(preferences);
        String providerName = provider.displayName;
        String formatHint = keyFormatHint(provider);
        boolean hasModel = provider.model != null && !provider.model.isEmpty();

        String statusIcon;
        String statusMsg;
        String resultMsg;

        if (!provider.requiresKey()) {
            // Self-hosted endpoints take no key, so there is nothing to validate.
            statusIcon = hasModel ? "🟢" : "⚪";
            statusMsg = hasModel ? str("{status_ready}") : str("{status_no_model}");
            resultMsg = hasModel
                    ? str("{test_no_key_needed}") + "\n\n" + str("{test_endpoint}") + ": " + provider.endpoint
                            + "\n" + str("{dash_model}") + ": " + provider.model
                    : str("{test_set_model}");
        } else if (provider.apiKey.isEmpty()) {
            statusIcon = "⚪";
            statusMsg = str("{test_key_missing}");
            resultMsg = str("{test_key_missing_detail}") + "\n\n" + formatHint;
        } else if (!provider.hasValidKeyFormat()) {
            statusIcon = "🔴";
            statusMsg = str("{test_invalid_format}");
            resultMsg = str("{test_invalid_detail}") + "\n\n" + formatHint;
        } else {
            statusIcon = "🟢";
            statusMsg = str("{test_format_valid}");
            resultMsg = str("{test_format_valid_detail}") + "\n\n" + str("{dash_model}") + ": " + provider.model
                    + "\n\n" + str("{test_format_tip}");
        }

        // The indicator emoji already encodes the outcome; reuse it so the
        // headline colour cannot disagree with the icon beside it.
        int statusColor = GeminiColorTokens.getStatusColor(
                pluginUI, GeminiColorTokens.statusTypeOf(statusIcon));

        bin.mt.plugin.api.ui.PluginView view = pluginUI
            .buildVerticalLayout()
            .addTextView().text(str("{test_testing}") + ": " + providerName).bold().textSize(16).paddingBottomDp(16)
            .addVerticalLayout().paddingDp(12).children(subBuilder -> subBuilder
                .addHorizontalLayout().children(h -> h
                    .addTextView().text(statusIcon).textSize(32).paddingRightDp(12)
                    .addVerticalLayout().children(v -> v
                        .addTextView().text(statusMsg).bold().textSize(16).textColor(statusColor)
                        .addTextView().text(resultMsg).paddingTopDp(4).textSize(14)
                    )
                )
            )
            .build();

        pluginUI.buildDialog()
            .setTitle(str("{test_dialog}"))
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

    // Deliberately not translated: this menu is a developer aid reached by
    // tapping the version five times, and every entry in it would otherwise
    // be a line asked of every volunteer translator.
    private void showDebugTools(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        if (pluginUI == null || preferences == null) return;

        boolean disableCache = preferences.getBoolean(GeminiConstants.PREF_DEBUG_DISABLE_MODEL_CACHE, false);
        java.util.List<Provider> cachedCatalogs = providersWithCatalogCache();

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
            .addVerticalLayout().paddingTopDp(8).children(column -> {
                for (Provider p : cachedCatalogs) {
                    String diagnostics = formatCacheDiagnostics(
                            ModelCatalogManager.inspectCache(preferences, cacheKeyFor(p.id)));
                    column.addVerticalLayout().paddingDp(12).marginBottomDp(10).backgroundColor(cardColor)
                        .children(section -> section
                            .addTextView().text(p.displayName + " Catalog").bold()
                                .textColor(GeminiColorTokens.getProviderBrandColor(pluginUI, p.id))
                            .addTextView().text(diagnostics).paddingTopDp(4).textColor(secondaryTextColor)
                        );
                }
            })
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

    /** Purely decorative; an unknown provider falls through to the generic mark. */
    private static String iconFor(String providerId) {
        switch (providerId) {
            case Providers.ID_GEMINI:     return "✨";
            case Providers.ID_OPENAI:     return "🧠";
            case Providers.ID_CLAUDE:     return "🎭";
            case Providers.ID_OPENROUTER: return "🔀";
            default:                      return "🔌";
        }
    }

    /** What a well-formed key looks like for the provider in hand. */
    private String keyFormatHint(Provider provider) {
        switch (provider.id) {
            case Providers.ID_GEMINI:     return "Expected format: AIzaSy...";
            case Providers.ID_OPENAI:     return "Expected format: sk-...";
            case Providers.ID_CLAUDE:     return "Expected format: sk-ant-...";
            case Providers.ID_OPENROUTER: return "Expected format: sk-or-v1-...";
            default:                      return str("{test_hint_any}");
        }
    }

    /**
     * Model catalogue cache key for a provider, or null when it caches nothing.
     * User-defined endpoints have no catalogue of their own.
     */
    private static String cacheKeyFor(String providerId) {
        switch (providerId) {
            case Providers.ID_GEMINI:     return GeminiConstants.PREF_CACHE_GEMINI_MODELS;
            case Providers.ID_OPENAI:     return GeminiConstants.PREF_CACHE_OPENAI_MODELS;
            case Providers.ID_CLAUDE:     return GeminiConstants.PREF_CACHE_CLAUDE_MODELS;
            case Providers.ID_OPENROUTER: return GeminiConstants.PREF_CACHE_OPENROUTER_MODELS;
            default:                      return null;
        }
    }

    private java.util.List<Provider> providersWithCatalogCache() {
        java.util.List<Provider> list = new ArrayList<>();
        for (Provider p : Providers.all(preferences)) {
            if (cacheKeyFor(p.id) != null) {
                list.add(p);
            }
        }
        return list;
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

    private ProviderStatus buildProviderStatus(Provider provider) {
        String icon = iconFor(provider.id);
        String name = provider.displayName;
        // Shared with the provider list screen so the two cannot disagree.
        String type = provider.statusType();

        if ("invalid".equals(type)) {
            return new ProviderStatus(provider.id, name, icon,
                "Invalid API key",
                "The key format looks wrong. Re-copy it from the provider dashboard.",
                type);
        }
        if ("neutral".equals(type)) {
            return provider.requiresKey()
                ? new ProviderStatus(provider.id, name, icon,
                    "Not configured", "Add your API key to activate " + name, type)
                : new ProviderStatus(provider.id, name, icon,
                    "No model set", "Set a model id for " + name, type);
        }
        return new ProviderStatus(provider.id, name, icon,
            "Ready to use",
            provider.requiresKey()
                ? "Key active (" + formatKeyHint(provider.apiKey) + ")"
                : "Model: " + provider.model,
            type);
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
        // Driven by the registry so a new provider's cache is never left behind
        // — the OpenRouter catalogue survived "Clear Caches" until this changed.
        for (Provider p : providersWithCatalogCache()) {
            ModelCatalogManager.clearModelCache(preferences, cacheKeyFor(p.id));
        }
    }
}
