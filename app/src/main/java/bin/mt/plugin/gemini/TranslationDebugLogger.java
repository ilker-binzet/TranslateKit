package bin.mt.plugin.gemini;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.UUID;

import bin.mt.plugin.api.PluginContext;

/**
 * Helper to emit structured debug log entries that make troubleshooting easier
 * when users share MT Manager logs. When disabled, it becomes a no-op.
 */
public class TranslationDebugLogger {

    private final PluginContext pluginContext;
    private final boolean enabled;
    private final String sessionId;

    public TranslationDebugLogger(@Nullable PluginContext pluginContext, boolean enabled) {
        this.pluginContext = pluginContext;
        this.enabled = enabled && pluginContext != null;
        this.sessionId = UUID.randomUUID().toString().substring(0, 8);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Span newSpan(String engine,
                        String model,
                        String sourceLanguage,
                        String targetLanguage,
                        int attempt,
                        int totalAttempts,
                        int inputChars,
                        String inputPreview) {
        if (!enabled) {
            return Span.disabled();
        }
        Span span = new Span(this, engine, model, sourceLanguage, targetLanguage,
                attempt, totalAttempts, inputChars, inputPreview);
        span.logStart();
        return span;
    }

    public void logLine(String emoji, String message) {
        if (!enabled || message == null || message.isEmpty()) {
            return;
        }
        emit((emoji != null ? emoji + " " : "") + "[AI Translation Hub] " + message);
    }

    private void emit(String line) {
        if (pluginContext != null) {
            pluginContext.log(line + " | session=" + sessionId);
        } else {
            System.out.println(line + " | session=" + sessionId);
        }
    }

    static String sanitizePreview(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String singleLine = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (singleLine.length() <= 60) {
            return singleLine;
        }
        return singleLine.substring(0, 57) + "...";
    }

    public static final class Span {
        private static final Span DISABLED = new Span();

        private final TranslationDebugLogger parent;
        private final String engine;
        private final String model;
        private final String sourceLanguage;
        private final String targetLanguage;
        private final int attempt;
        private final int totalAttempts;
        private final int inputChars;
        private final String inputPreview;
        private final long startedAt;

        private Span() {
            this.parent = null;
            this.engine = null;
            this.model = null;
            this.sourceLanguage = null;
            this.targetLanguage = null;
            this.attempt = 0;
            this.totalAttempts = 0;
            this.inputChars = 0;
            this.inputPreview = null;
            this.startedAt = 0L;
        }

        private Span(TranslationDebugLogger parent,
                     String engine,
                     String model,
                     String sourceLanguage,
                     String targetLanguage,
                     int attempt,
                     int totalAttempts,
                     int inputChars,
                     String inputPreview) {
            this.parent = parent;
            this.engine = engine;
            this.model = model;
            this.sourceLanguage = sourceLanguage;
            this.targetLanguage = targetLanguage;
            this.attempt = attempt;
            this.totalAttempts = totalAttempts;
            this.inputChars = inputChars;
            this.inputPreview = inputPreview;
            this.startedAt = System.currentTimeMillis();
        }

        public static Span disabled() {
            return DISABLED;
        }

        private boolean isEnabled() {
            return parent != null && parent.enabled;
        }

        private void logStart() {
            if (!isEnabled()) {
                return;
            }
            parent.emit(String.format(Locale.US,
                    "🔍 [AI Translation Hub] translate_start engine=%s model=%s src=%s tgt=%s attempt=%d/%d chars_in=%d preview=\"%s\"",
                    engine, model, sourceLanguage, targetLanguage,
                    attempt, totalAttempts, inputChars,
                    inputPreview == null ? "" : inputPreview));
        }

        public void markSuccess(int outputChars) {
            if (!isEnabled()) {
                return;
            }
            long latency = System.currentTimeMillis() - startedAt;
            parent.emit(String.format(Locale.US,
                    "✅ [AI Translation Hub] translate_success engine=%s model=%s latency=%dms chars_out=%d attempt=%d/%d",
                    engine, model, latency, outputChars, attempt, totalAttempts));
        }

        public void markFailure(String errorMessage, boolean willRetry) {
            if (!isEnabled()) {
                return;
            }
            long latency = System.currentTimeMillis() - startedAt;
            parent.emit(String.format(Locale.US,
                    "❌ [AI Translation Hub] translate_error engine=%s model=%s latency=%dms attempt=%d/%d retry=%s error=\"%s\"",
                    engine, model, latency, attempt, totalAttempts,
                    willRetry ? "yes" : "no",
                    errorMessage == null ? "" : errorMessage.replace('\n', ' ')));
        }
    }
}
