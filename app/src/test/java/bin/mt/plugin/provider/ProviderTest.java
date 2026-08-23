package bin.mt.plugin.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * Covers the addressing rules: which URL a provider is reached at, which
 * headers authenticate it, and how a user-supplied base URL is normalised.
 * A wrong answer here surfaces as a confusing API error rather than an
 * obvious failure, which is why it is pinned down.
 */
public class ProviderTest {

    private static Provider gemini() {
        return new Provider("gemini", "Google Gemini", Provider.WIRE_GEMINI,
                "https://generativelanguage.googleapis.com/v1beta/models",
                "AIzaSyKEY", "gemini-2.5-flash", null, null, null);
    }

    private static Provider openai() {
        return new Provider("openai", "OpenAI", Provider.WIRE_OPENAI,
                "https://api.openai.com/v1/chat/completions",
                "sk-secret", "gpt-4.1-mini", null, null, null);
    }

    private static Provider claude() {
        return new Provider("claude", "Anthropic Claude", Provider.WIRE_ANTHROPIC,
                "https://api.anthropic.com/v1/messages",
                "sk-ant-secret", "claude-sonnet-4-5-latest", null, null, null);
    }

    private static Provider openrouter() {
        Map<String, String> extra = new HashMap<>();
        extra.put("X-Title", "TranslateKit");
        return new Provider("openrouter", "OpenRouter", Provider.WIRE_OPENAI,
                "https://openrouter.ai/api/v1/chat/completions",
                "sk-or-v1-secret", "google/gemini-2.5-flash", null, null, extra);
    }

    private static Provider localOllama() {
        return new Provider("custom:ollama", "Ollama", Provider.WIRE_OPENAI,
                "http://127.0.0.1:11434/v1/chat/completions",
                "", "llama3", null, null, null);
    }

    private static Provider withKey(String keyPattern, String apiKey) {
        return new Provider("t", "Test", Provider.WIRE_OPENAI,
                "https://x/v1/chat/completions", apiKey, "m", keyPattern, null, null);
    }

    // ---- key validation ----

    @Test
    public void nullKeyPatternAcceptsAnything() {
        // Self-hosted endpoints (Ollama, LM Studio) take no key at all.
        assertTrue(withKey(null, "").hasValidKeyFormat());
        assertTrue(withKey(null, "whatever").hasValidKeyFormat());
        assertFalse(withKey(null, "").requiresKey());
    }

    @Test
    public void keyPatternIsEnforcedWhenPresent() {
        String openRouter = "^sk-or-v1-[A-Za-z0-9]{16,}$";
        assertTrue(withKey(openRouter, "sk-or-v1-abcdef0123456789").hasValidKeyFormat());
        assertFalse("a plain OpenAI key must not pass as OpenRouter",
                withKey(openRouter, "sk-abcdef0123456789").hasValidKeyFormat());
        assertFalse(withKey(openRouter, "").hasValidKeyFormat());
        assertTrue(withKey(openRouter, "").requiresKey());
    }

    @Test
    public void nullApiKeyBecomesEmptyString() {
        assertEquals("", withKey(null, null).apiKey);
    }

    @Test
    public void nullExtraHeadersBecomeEmptyMap() {
        assertEquals(Collections.emptyMap(), withKey(null, "k").extraHeaders);
    }

    // ---- URLs ----

    @Test
    public void geminiUrlEmbedsModelAndKey() {
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models"
                        + "/gemini-2.5-flash:generateContent?key=AIzaSyKEY",
                gemini().url());
    }

    @Test
    public void otherWiresPostToTheEndpointUnchanged() {
        assertEquals("https://api.openai.com/v1/chat/completions", openai().url());
        assertEquals("https://api.anthropic.com/v1/messages", claude().url());
        assertEquals("https://openrouter.ai/api/v1/chat/completions", openrouter().url());
    }

    // ---- headers ----

    @Test
    public void openAiWireUsesBearerAuth() {
        Map<String, String> h = openai().headers();
        assertEquals("Bearer sk-secret", h.get("Authorization"));
        assertNull(h.get("x-api-key"));
    }

    @Test
    public void anthropicWireUsesApiKeyAndVersionHeaders() {
        Map<String, String> h = claude().headers();
        assertEquals("sk-ant-secret", h.get("x-api-key"));
        assertEquals(Provider.ANTHROPIC_VERSION, h.get("anthropic-version"));
        assertNull(h.get("Authorization"));
    }

    @Test
    public void geminiWireSendsNoAuthHeaders() {
        // The key rides in the query string; sending it twice is a leak risk.
        assertTrue(gemini().headers().isEmpty());
    }

    @Test
    public void extraHeadersAreMerged() {
        Map<String, String> h = openrouter().headers();
        assertEquals("Bearer sk-or-v1-secret", h.get("Authorization"));
        assertEquals("TranslateKit", h.get("X-Title"));
    }

    @Test
    public void keylessOpenAiCompatibleEndpointSendsNoAuthorizationHeader() {
        // A local Ollama rejects "Authorization: Bearer " with an empty token.
        assertFalse(localOllama().headers().containsKey("Authorization"));
    }

    // ---- base URL normalisation ----

    @Test
    public void baseUrlIsNormalisedToChatCompletions() {
        assertEquals("https://api.groq.com/openai/v1/chat/completions",
                Provider.chatCompletionsUrl("https://api.groq.com/openai/v1"));
        assertEquals("https://api.groq.com/openai/v1/chat/completions",
                Provider.chatCompletionsUrl("https://api.groq.com/openai/v1/"));
        assertEquals("https://api.groq.com/openai/v1/chat/completions",
                Provider.chatCompletionsUrl("https://api.groq.com/openai/v1/chat/completions"));
    }

    @Test
    public void modelsUrlIsDerivedFromEitherForm() {
        assertEquals("http://127.0.0.1:11434/v1/models",
                Provider.modelsUrl("http://127.0.0.1:11434/v1"));
        assertEquals("http://127.0.0.1:11434/v1/models",
                Provider.modelsUrl("http://127.0.0.1:11434/v1/chat/completions"));
    }

    @Test
    public void slugStripsPunctuationAndEdgeDashes() {
        assertEquals("lm-studio", Provider.slug("LM Studio"));
        assertEquals("my-server-2", Provider.slug("My Server #2"));
        assertEquals("groq", Provider.slug("  Groq!  "));
    }

    // ---- configuration verdict ----

    /**
     * Both the provider list and the diagnostics dashboard colour themselves
     * from this one answer, so a wrong verdict paints a broken provider green
     * in two places at once.
     */
    @Test
    public void keylessEndpointIsReadyOnceItNamesAModel() {
        assertEquals("ready", localOllama().statusType());
        assertEquals("neutral", localOllama().withModel("").statusType());
        assertEquals("neutral", localOllama().withModel(null).statusType());
    }

    @Test
    public void aMissingKeyReadsAsUnconfiguredRatherThanBroken() {
        assertEquals("neutral", withKey("^sk-.+$", "").statusType());
    }

    @Test
    public void aMalformedKeyIsInvalid() {
        assertEquals("invalid", withKey("^sk-.+$", "AIzaSyWrongProvider").statusType());
    }

    @Test
    public void aWellFormedKeyIsReady() {
        assertEquals("ready", withKey("^sk-.+$", "sk-secret").statusType());
    }

    @Test
    public void noFailingStateIsEverReportedAsReady() {
        for (Provider broken : new Provider[]{
                withKey("^sk-.+$", ""),
                withKey("^sk-.+$", "nonsense"),
                localOllama().withModel("")}) {
            assertFalse(broken.toString(), "ready".equals(broken.statusType()));
        }
    }

    // ---- fallback copy ----

    @Test
    public void withModelKeepsEverythingElse() {
        Provider p = claude().withModel("claude-haiku-4-5-latest");
        assertEquals("claude-haiku-4-5-latest", p.model);
        assertEquals(claude().id, p.id);
        assertEquals(claude().apiKey, p.apiKey);
        assertEquals(claude().endpoint, p.endpoint);
        assertEquals(claude().wire, p.wire);
    }
}
