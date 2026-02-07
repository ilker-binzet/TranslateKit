package bin.mt.plugin.gemini;

import android.content.SharedPreferences;

import bin.mt.plugin.api.LocalString;
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
        this.preferences = context.getPreferences();
        LocalString localString = context.getAssetLocalString("GeminiTranslate");
        if (localString == null) {
            localString = context.getLocalString();
        }
        builder.setLocalString(localString);

        // ==================== Quick Presets ====================
        builder.addText("Quick Presets")
            .summary("Apply ready-made context + tone combinations")
            .onClick((pluginUI, item) -> showCombinedPresetsDialog(pluginUI));

        // ==================== Tone & Voice ====================
        builder.addInput("Tone & Voice", GeminiConstants.PREF_CONTEXT_TONE)
            .summary("Writing style: friendly, formal, playful, technical...")
            .defaultValue(GeminiConstants.DEFAULT_CONTEXT_TONE)
            .valueAsSummary();

        // ==================== App Description ====================
        builder.addInput("App Description", GeminiConstants.PREF_CONTEXT_APP_NAME)
            .summary("App name and type (e.g. 'MyApp - Shopping')")
            .valueAsSummary();

        // ==================== Target Audience ====================
        builder.addInput("Target Audience", GeminiConstants.PREF_CONTEXT_AUDIENCE)
            .summary("Who uses your app (e.g. 'teenagers', 'developers')")
            .valueAsSummary();

        // ==================== Extra Notes ====================
        builder.addInput("Extra Notes", GeminiConstants.PREF_CONTEXT_NOTES)
            .summary("Special rules: locale format, forbidden words, etc.")
            .valueAsSummary();
    }

    // ==================== Dialog Methods ====================

    private void showCombinedPresetsDialog(bin.mt.plugin.api.ui.PluginUI pluginUI) {
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
                        applyContextPreset(CONTEXT_PRESETS[which]);
                        context.showToast(CONTEXT_PRESETS[which].title + " applied");
                    } else if (which > CONTEXT_PRESETS.length) {
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
