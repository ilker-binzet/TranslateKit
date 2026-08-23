package bin.mt.plugin.gemini;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The status colour is derived from the indicator emoji that already prefixes
 * each status line, so the two cannot drift apart. That mapping is the thing
 * worth pinning down — a wrong answer paints a failure green.
 */
public class StatusTypeTest {

    @Test
    public void greenIndicatorMeansReady() {
        assertEquals("ready", GeminiColorTokens.statusTypeOf("🟢 Ready - Click 'Test API Key'"));
        assertEquals("ready", GeminiColorTokens.statusTypeOf("🟢 Configured"));
        assertEquals("ready", GeminiColorTokens.statusTypeOf("🟢"));
    }

    @Test
    public void redIndicatorMeansInvalid() {
        assertEquals("invalid",
                GeminiColorTokens.statusTypeOf("🔴 Invalid Format - keys start with 'sk-'"));
    }

    @Test
    public void yellowIndicatorMeansWarning() {
        assertEquals("warning", GeminiColorTokens.statusTypeOf("🟡 Rate limited"));
    }

    @Test
    public void anythingElseIsNeutral() {
        assertEquals("neutral", GeminiColorTokens.statusTypeOf("⚪ Not Configured"));
        assertEquals("neutral", GeminiColorTokens.statusTypeOf("Ready"));
        assertEquals("neutral", GeminiColorTokens.statusTypeOf(""));
        assertEquals("neutral", GeminiColorTokens.statusTypeOf(null));
    }

    @Test
    public void aFailureIsNeverReportedAsReady() {
        // Guards the case that matters: red must not fall through to the
        // default and get painted with the success colour.
        for (String failure : new String[]{
                "🔴 Invalid Format", "⚪ Not Configured", "🟡 Pending"}) {
            assertEquals("no failure state may map to ready",
                    false, "ready".equals(GeminiColorTokens.statusTypeOf(failure)));
        }
    }
}
