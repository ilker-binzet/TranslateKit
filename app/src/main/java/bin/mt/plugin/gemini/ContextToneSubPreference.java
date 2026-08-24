package bin.mt.plugin.gemini;

import android.content.SharedPreferences;

import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.preference.PluginPreference;

/**
 * Sub-preference screen for Context & Tone settings.
 * Contains: Quick Presets, Tone & Voice, App Description, Target Audience, Extra Notes.
 */
public class ContextToneSubPreference implements PluginPreference {

    private PluginContext context;
    private SharedPreferences preferences;

    // ==================== Preset Data ====================

    /**
     * A ready-made context. {@code title} and {@code subtitle} are language
     * pack keys; the five fields after them are prompt text sent to the model,
     * which stays in English because that is what the models handle best.
     */
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

    /** {@code name} and {@code description} are pack keys; {@code storedValue} is prompt text. */
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
            "{ctx_preset_mobile}",
            "{ctx_preset_mobile_sub}",
            "Mobile Application",
            "Android/iOS Mobile Experience",
            "General smartphone users",
            "Friendly and clear",
            "Short sentences, plain language, actionable CTA verbs"
        ),
        new ContextPreset(
            "{ctx_preset_gaming}",
            "{ctx_preset_gaming_sub}",
            "Gaming Application",
            "Mobile/PC Game Interface",
            "Gamers and casual players",
            "Energetic and playful",
            "Use game terminology, keep hype and momentum high"
        ),
        new ContextPreset(
            "{ctx_preset_reading}",
            "{ctx_preset_reading_sub}",
            "E-book Reader",
            "Digital Reading Platform",
            "Avid readers and book lovers",
            "Literary and sophisticated",
            "Flowing sentences, keep emphasis on readability and calm tone"
        ),
        new ContextPreset(
            "{ctx_preset_business}",
            "{ctx_preset_business_sub}",
            "Business Application",
            "Professional Analytics / Dashboard",
            "Business professionals and analysts",
            "Professional and concise",
            "Focus on clarity, mention KPIs, avoid slang"
        ),
        new ContextPreset(
            "{ctx_preset_support}",
            "{ctx_preset_support_sub}",
            "Support Assistant",
            "AI / Human Hybrid Support",
            "End-users needing troubleshooting",
            "Empathetic and helpful",
            "Reassure the user, acknowledge issues, provide next steps"
        ),
        new ContextPreset(
            "{ctx_preset_commerce}",
            "{ctx_preset_commerce_sub}",
            "Commerce Platform",
            "Online Shopping Experience",
            "Shoppers comparing products",
            "Conversion-focused and reassuring",
            "Highlight benefits, keep CTA strong, include trust cues"
        ),
        new ContextPreset(
            "{ctx_preset_devdocs}",
            "{ctx_preset_devdocs_sub}",
            "Developer Portal",
            "Technical Documentation Suite",
            "Developers and integration engineers",
            "Precise and instructional",
            "Include parameters, avoid marketing tone, keep terminology exact"
        ),
        new ContextPreset(
            "{ctx_preset_education}",
            "{ctx_preset_education_sub}",
            "Learning Platform",
            "Education / LMS Experience",
            "Students and educators",
            "Encouraging and structured",
            "Explain learning goals, keep directions step-based and kind"
        )
    };

    private static final TonePreset[] TONE_PRESETS = new TonePreset[]{
        new TonePreset(
            "{tone_friendly}",
            "Friendly and clear (plain language, second-person guidance, concise sentences)",
            "{tone_friendly_desc}"
        ),
        new TonePreset(
            "{tone_marketing}",
            "Confident and inspiring marketing voice (benefit-driven, energetic, short CTA verbs)",
            "{tone_marketing_desc}"
        ),
        new TonePreset(
            "{tone_legal}",
            "Formal and compliant tone (objective, third-person, references policy numbers where needed)",
            "{tone_legal_desc}"
        ),
        new TonePreset(
            "{tone_support}",
            "Empathetic and solution-focused (acknowledge frustration, reassure, offer clear steps)",
            "{tone_support_desc}"
        ),
        new TonePreset(
            "{tone_technical}",
            "Precise and instructional (step-by-step, include field names, avoid marketing language)",
            "{tone_technical_desc}"
        ),
        new TonePreset(
            "{tone_playful}",
            "Playful and witty (light humor, emoji-friendly, upbeat pacing)",
            "{tone_playful_desc}"
        )
    };

    @Override
    public void onBuild(PluginContext context, Builder builder) {
        this.context = context;
        this.preferences = context.getPreferences();

        // ==================== Quick Presets ====================
        builder.addText(str("{ctx_quick_presets}"))
            .summary(str("{ctx_quick_presets_summary}"))
            .onClick((pluginUI, item) -> showCombinedPresetsDialog(pluginUI));

        // ==================== Tone & Voice ====================
        builder.addInput(str("{ctx_tone}"), GeminiConstants.PREF_CONTEXT_TONE)
            .summary(str("{ctx_tone_summary}"))
            .defaultValue(GeminiConstants.DEFAULT_CONTEXT_TONE)
            .valueAsSummary();

        // ==================== App Description ====================
        builder.addInput(str("{ctx_app}"), GeminiConstants.PREF_CONTEXT_APP_NAME)
            .summary(str("{ctx_app_summary}"))
            .valueAsSummary();

        // ==================== Target Audience ====================
        builder.addInput(str("{ctx_audience}"), GeminiConstants.PREF_CONTEXT_AUDIENCE)
            .summary(str("{ctx_audience_summary}"))
            .valueAsSummary();

        // ==================== Extra Notes ====================
        builder.addInput(str("{ctx_notes}"), GeminiConstants.PREF_CONTEXT_NOTES)
            .summary(str("{ctx_notes_summary}"))
            .valueAsSummary();
    }

    /** Localized text for {@code key}; the language packs live in assets. */
    private String str(String key) {
        return context.getString(key);
    }

    // ==================== Dialog Methods ====================

    private void showCombinedPresetsDialog(bin.mt.plugin.api.ui.PluginUI pluginUI) {
        int totalItems = CONTEXT_PRESETS.length + 1 + TONE_PRESETS.length;
        CharSequence[] labels = new CharSequence[totalItems];

        // Context presets section
        for (int i = 0; i < CONTEXT_PRESETS.length; i++) {
            ContextPreset p = CONTEXT_PRESETS[i];
            labels[i] = "\uD83D\uDCCB " + str(p.title) + "\n" + str(p.subtitle);
        }
        // Separator
        labels[CONTEXT_PRESETS.length] = "── " + str("{ctx_tone_only}") + " ──";
        // Tone presets
        for (int i = 0; i < TONE_PRESETS.length; i++) {
            TonePreset t = TONE_PRESETS[i];
            labels[CONTEXT_PRESETS.length + 1 + i] = "\uD83C\uDFA8 " + str(t.name) + "\n" + str(t.description);
        }

        pluginUI.buildDialog()
                .setTitle(str("{ctx_quick_presets}"))
                .setItems(labels, (dialog, which) -> {
                    if (which < CONTEXT_PRESETS.length) {
                        applyContextPreset(CONTEXT_PRESETS[which]);
                        context.showToast(str(CONTEXT_PRESETS[which].title) + " " + str("{msg_preset_applied}"));
                    } else if (which > CONTEXT_PRESETS.length) {
                        TonePreset tone = TONE_PRESETS[which - CONTEXT_PRESETS.length - 1];
                        preferences.edit()
                                .putString(GeminiConstants.PREF_CONTEXT_TONE, tone.storedValue)
                                .apply();
                        context.showToast(str("{ctx_tone}") + ": " + str(tone.name));
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("{cancel}", null)
                .show();
    }

    private void applyContextPreset(ContextPreset preset) {
        SharedPreferences.Editor editor = preferences.edit();
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
}
