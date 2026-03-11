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

    public BatchSpan newBatchSpan(String engine,
                                  String model,
                                  String sourceLanguage,
                                  String targetLanguage,
                                  int totalTexts,
                                  int translatableCount,
                                  int totalChars) {
        if (!enabled) {
            return BatchSpan.disabled();
        }
        return new BatchSpan(this, engine, model, sourceLanguage, targetLanguage,
                totalTexts, translatableCount, totalChars);
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
        emit((emoji != null ? emoji + " " : "") + "[TranslateKit] " + message);
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
                    "🔍 [TranslateKit] translate_start engine=%s model=%s src=%s tgt=%s attempt=%d/%d chars_in=%d preview=\"%s\"",
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
                    "✅ [TranslateKit] translate_success engine=%s model=%s latency=%dms chars_out=%d attempt=%d/%d",
                    engine, model, latency, outputChars, attempt, totalAttempts));
        }

        public void markFailure(String errorMessage, boolean willRetry) {
            if (!isEnabled()) {
                return;
            }
            long latency = System.currentTimeMillis() - startedAt;
            parent.emit(String.format(Locale.US,
                    "❌ [TranslateKit] translate_error engine=%s model=%s latency=%dms attempt=%d/%d retry=%s error=\"%s\"",
                    engine, model, latency, attempt, totalAttempts,
                    willRetry ? "yes" : "no",
                    errorMessage == null ? "" : errorMessage.replace('\n', ' ')));
        }
    }

    /**
     * Structured batch translation span — tracks the full lifecycle of a batch translate call.
     */
    public static class BatchSpan {
        private static final BatchSpan DISABLED = new BatchSpan(null, null, null, null, null, 0, 0, 0);

        private final TranslationDebugLogger parent;
        private final String engine;
        private final String model;
        private final String sourceLanguage;
        private final String targetLanguage;
        private final int totalTexts;
        private final int translatableCount;
        private final int totalChars;
        private final long startedAt;

        BatchSpan(TranslationDebugLogger parent,
                  String engine, String model,
                  String sourceLanguage, String targetLanguage,
                  int totalTexts, int translatableCount, int totalChars) {
            this.parent = parent;
            this.engine = engine;
            this.model = model;
            this.sourceLanguage = sourceLanguage;
            this.targetLanguage = targetLanguage;
            this.totalTexts = totalTexts;
            this.translatableCount = translatableCount;
            this.totalChars = totalChars;
            this.startedAt = System.currentTimeMillis();
            logStart();
        }

        public static BatchSpan disabled() {
            return DISABLED;
        }

        private boolean isEnabled() {
            return parent != null && parent.enabled;
        }

        private void logStart() {
            if (!isEnabled()) return;
            parent.emit(String.format(Locale.US,
                    "📦 [TranslateKit] batch_start engine=%s model=%s src=%s tgt=%s total=%d translatable=%d chars=%d",
                    engine, model, sourceLanguage, targetLanguage,
                    totalTexts, translatableCount, totalChars));
        }

        public void logPreprocess(int skippedCount) {
            if (!isEnabled()) return;
            parent.emit(String.format(Locale.US,
                    "🔧 [TranslateKit] batch_preprocess translatable=%d skipped=%d total=%d",
                    translatableCount, skippedCount, totalTexts));
        }

        public void logApiCall(int promptChars) {
            if (!isEnabled()) return;
            parent.emit(String.format(Locale.US,
                    "🌐 [TranslateKit] batch_api_call engine=%s prompt_chars=%d items=%d",
                    engine, promptChars, translatableCount));
        }

        public void logParseResult(String formatUsed, int matchedCount, int expectedCount) {
            if (!isEnabled()) return;
            parent.emit(String.format(Locale.US,
                    "📋 [TranslateKit] batch_parse format=%s matched=%d expected=%d %s",
                    formatUsed, matchedCount, expectedCount,
                    matchedCount == expectedCount ? "OK" : "MISMATCH"));
        }

        public void logParseWarning(int missingCount, String details) {
            if (!isEnabled()) return;
            parent.emit(String.format(Locale.US,
                    "⚠️ [TranslateKit] batch_parse_warning missing=%d details=\"%s\"",
                    missingCount, details == null ? "" : details.replace('\n', ' ')));
        }

        public void logPlaceholderRestore(int itemIndex, boolean success, String details) {
            if (!isEnabled()) return;
            parent.emit(String.format(Locale.US,
                    "🔗 [TranslateKit] batch_placeholder_restore item=%d success=%s details=\"%s\"",
                    itemIndex, success ? "yes" : "no",
                    details == null ? "" : details.replace('\n', ' ')));
        }

        public void markSuccess(int translatedCount) {
            if (!isEnabled()) return;
            long latency = System.currentTimeMillis() - startedAt;
            parent.emit(String.format(Locale.US,
                    "✅ [TranslateKit] batch_success engine=%s model=%s latency=%dms translated=%d total=%d",
                    engine, model, latency, translatedCount, totalTexts));
        }

        public void logFallbackToIndividual(String reason) {
            if (!isEnabled()) return;
            parent.emit(String.format(Locale.US,
                    "🔄 [TranslateKit] batch_fallback_individual engine=%s items=%d reason=\"%s\"",
                    engine, totalTexts,
                    reason == null ? "" : reason.replace('\n', ' ')));
        }

        public void markFailure(String errorMessage) {
            if (!isEnabled()) return;
            long latency = System.currentTimeMillis() - startedAt;
            parent.emit(String.format(Locale.US,
                    "❌ [TranslateKit] batch_error engine=%s model=%s latency=%dms items=%d error=\"%s\"",
                    engine, model, latency, totalTexts,
                    errorMessage == null ? "" : errorMessage.replace('\n', ' ')));
        }
    }
}
