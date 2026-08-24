package bin.mt.plugin.gemini;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * The language selection feeds MT's Source and Target dropdowns directly, so a
 * wrong answer here is a picker the user cannot translate from.
 */
public class LanguagesTest {

    @Test
    public void everyCodeHasAName() {
        // A missing name is what put a bare "he" in the dropdown to begin with.
        for (String code : Languages.allCodes()) {
            assertNotNull("no name for " + code, Languages.nameOf(code));
            assertFalse("empty name for " + code, Languages.nameOf(code).isEmpty());
        }
    }

    @Test
    public void hebrewIsNamed() {
        assertEquals("Hebrew", Languages.nameOf("he"));
    }

    @Test
    public void noSelectionMeansEveryLanguage() {
        assertEquals(Languages.allCodes(), Languages.parseEnabled(""));
        assertEquals(Languages.allCodes(), Languages.parseEnabled(null));
        assertEquals(Languages.allCodes(), Languages.parseEnabled("   "));
    }

    @Test
    public void aSelectionIsKeptInCatalogueOrder() {
        // Ticked in any order, shown in one order.
        assertEquals(Arrays.asList("en", "tr", "de"), Languages.parseEnabled("de,tr,en"));
    }

    @Test
    public void unknownCodesAreDropped() {
        assertEquals(Arrays.asList("en", "tr"), Languages.parseEnabled("en,klingon,tr"));
    }

    @Test
    public void surroundingSpaceIsTolerated() {
        assertEquals(Arrays.asList("en", "tr"), Languages.parseEnabled(" en , tr "));
    }

    @Test
    public void anEmptySelectionNeverReachesTheDropdown() {
        // MT would be handed a picker with nothing in it.
        assertEquals(Languages.allCodes(), Languages.parseEnabled("klingon,elvish"));
        assertEquals(Languages.allCodes(), Languages.parseEnabled(","));
    }

    @Test
    public void aSelectionSurvivesASaveAndLoad() {
        List<String> picked = Arrays.asList("tr", "en", "ja");
        List<String> restored = Languages.parseEnabled(Languages.joinEnabled(picked));
        assertTrue(restored.containsAll(picked));
        assertEquals(picked.size(), restored.size());
    }
}
