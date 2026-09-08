package bin.mt.plugin.gemini;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class PlaceholderTest {

    @Test
    public void batchTokensCarryLineBreaksThroughTheOneItemPerLinePrompt() {
        // The batch prompt is "[N] text" per line: a raw newline inside an item
        // used to be flattened to a space and never restored.
        String source = "## 功能建议\n\n## 使用场景\r\n%1$s";
        var r = GeminiTranslationEngine.tokenizePlaceholders(
                source, GeminiTranslationEngine.BATCH_PLACEHOLDER_PATTERN);

        assertFalse("no raw line break may reach the prompt", r.tokenizedText.contains("\n"));
        assertEquals("## 功能建议__PH0____PH1__## 使用场景__PH2____PH3__", r.tokenizedText);

        String translated = "## Özellik Önerisi__PH0____PH1__## Kullanım Senaryosu__PH2____PH3__";
        assertEquals("## Özellik Önerisi\n\n## Kullanım Senaryosu\r\n%1$s",
                GeminiTranslationEngine.restorePlaceholders(translated, r.placeholders));
    }
}
