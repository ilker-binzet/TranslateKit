package bin.mt.plugin.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;

import org.junit.Test;

import bin.mt.json.JSONObject;
import bin.mt.plugin.common.JSONCompat;

/**
 * Request bodies and response parsing, per wire format.
 *
 * <p>These run against the real SDK JSON types, so a change that would send a
 * malformed body to a provider fails here rather than as an opaque 400 on the
 * user's device. No API key and no network are involved.
 */
public class ProviderClientTest {

    private static final String PROMPT = "Translate:\n[1] Hello\n[2] Bye";
    private static final String SYSTEM = "You are a translator.";

    private static Provider provider(String wire) {
        return new Provider("x", "X", wire, "https://example.invalid/v1",
                "KEY", "the-model", null, null, null);
    }

    // ── OpenAI wire ───────────────────────────────────────────────────────────

    @Test
    public void openAiBodyMatchesTheChatCompletionsSchema() throws IOException {
        JSONObject body = ProviderClient.buildRequest(provider(Provider.WIRE_OPENAI), PROMPT, SYSTEM);

        assertEquals("the-model", JSONCompat.optString(body, "model", null));

        var messages = JSONCompat.optJSONArray(body, "messages");
        assertNotNull("messages array is required", messages);
        assertEquals(2, JSONCompat.size(messages));

        JSONObject system = JSONCompat.optJSONObject(messages, 0);
        assertEquals("system", JSONCompat.optString(system, "role", null));
        assertEquals(SYSTEM, JSONCompat.optString(system, "content", null));

        JSONObject user = JSONCompat.optJSONObject(messages, 1);
        assertEquals("user", JSONCompat.optString(user, "role", null));
        assertEquals(PROMPT, JSONCompat.optString(user, "content", null));
    }

    @Test
    public void openAiResponseIsParsedFromChoices() throws IOException {
        JSONObject response = new JSONObject("{\"choices\":[{\"message\":"
                + "{\"role\":\"assistant\",\"content\":\"[1] Merhaba\\n[2] Hosca kal\"}}]}");
        assertEquals("[1] Merhaba\n[2] Hosca kal",
                ProviderClient.parseResponse(provider(Provider.WIRE_OPENAI), response));
    }

    @Test
    public void openAiContentMayArriveAsAnArrayOfParts() throws IOException {
        // Some OpenAI-compatible servers return the newer parts array instead
        // of a plain string; both must read the same.
        JSONObject response = new JSONObject("{\"choices\":[{\"message\":{\"content\":"
                + "[{\"type\":\"text\",\"text\":\"Merhaba\"}]}}]}");
        assertEquals("Merhaba",
                ProviderClient.parseResponse(provider(Provider.WIRE_OPENAI), response));
    }

    // ── Anthropic wire ────────────────────────────────────────────────────────

    @Test
    public void anthropicBodyMatchesTheMessagesSchema() throws IOException {
        JSONObject body = ProviderClient.buildRequest(provider(Provider.WIRE_ANTHROPIC), PROMPT, SYSTEM);

        assertEquals("the-model", JSONCompat.optString(body, "model", null));
        // Anthropic requires max_tokens; omitting it is a 400.
        assertTrue("max_tokens is mandatory on the Messages API",
                JSONCompat.optInt(body, "max_tokens", 0) > 0);
        // The system prompt is a top-level field, never a message role.
        assertEquals(SYSTEM, JSONCompat.optString(body, "system", null));

        var messages = JSONCompat.optJSONArray(body, "messages");
        assertEquals(1, JSONCompat.size(messages));
        JSONObject user = JSONCompat.optJSONObject(messages, 0);
        assertEquals("user", JSONCompat.optString(user, "role", null));

        var content = JSONCompat.optJSONArray(user, "content");
        assertNotNull("content must be a block array", content);
        JSONObject block = JSONCompat.optJSONObject(content, 0);
        assertEquals("text", JSONCompat.optString(block, "type", null));
        assertEquals(PROMPT, JSONCompat.optString(block, "text", null));
    }

    @Test
    public void anthropicResponseConcatenatesTextBlocks() throws IOException {
        JSONObject response = new JSONObject("{\"content\":["
                + "{\"type\":\"text\",\"text\":\"[1] Merhaba\\n\"},"
                + "{\"type\":\"text\",\"text\":\"[2] Hosca kal\"}]}");
        assertEquals("[1] Merhaba\n[2] Hosca kal",
                ProviderClient.parseResponse(provider(Provider.WIRE_ANTHROPIC), response));
    }

    @Test
    public void openRouterAsksForNoReasoning() throws IOException {
        // Hybrid models reason by default there and spend max_tokens on it
        // first; the log showed "Response was empty" from deepseek.
        Provider or = new Provider(Providers.ID_OPENROUTER, "OpenRouter", Provider.WIRE_OPENAI,
                "https://openrouter.ai/api/v1", "KEY", "deepseek/deepseek-v4-flash", null, null, null);
        JSONObject body = ProviderClient.buildRequest(or, PROMPT, SYSTEM);
        JSONObject reasoning = JSONCompat.optJSONObject(body, "reasoning");
        assertNotNull(reasoning);
        assertFalse(reasoning.getBoolean("enabled"));
        assertEquals("low", JSONCompat.optString(reasoning, "effort", null));
        assertTrue("budget still travels as max_tokens", JSONCompat.optInt(body, "max_tokens", 0) >= 2048);

        // A custom OpenAI-compatible endpoint may reject unknown fields.
        assertNull(JSONCompat.optJSONObject(
                ProviderClient.buildRequest(provider(Provider.WIRE_OPENAI), PROMPT, SYSTEM), "reasoning"));
    }

    @Test
    public void openAiReasoningModelsGetTheParametersTheyAccept() throws IOException {
        assertTrue(ProviderClient.isOpenAiReasoningModel("gpt-5.2"));
        assertTrue(ProviderClient.isOpenAiReasoningModel("o4-mini"));
        assertFalse(ProviderClient.isOpenAiReasoningModel("gpt-4.1-mini"));
        assertFalse(ProviderClient.isOpenAiReasoningModel("gpt-4o"));

        Provider five = new Provider(Providers.ID_OPENAI, "OpenAI", Provider.WIRE_OPENAI,
                "https://api.openai.com/v1", "KEY", "gpt-5.2", null, null, null);
        JSONObject body = ProviderClient.buildRequest(five, PROMPT, SYSTEM);
        assertTrue(JSONCompat.optInt(body, "max_completion_tokens", 0) >= 2048);
        assertEquals("low", JSONCompat.optString(body, "reasoning_effort", null));
        assertNull("max_tokens is a 400 on gpt-5", JSONCompat.optString(body, "max_tokens", null));
        assertNull("only the default temperature is accepted", JSONCompat.optString(body, "temperature", null));

        Provider four = new Provider(Providers.ID_OPENAI, "OpenAI", Provider.WIRE_OPENAI,
                "https://api.openai.com/v1", "KEY", "gpt-4.1-mini", null, null, null);
        JSONObject legacy = ProviderClient.buildRequest(four, PROMPT, SYSTEM);
        assertTrue(JSONCompat.optInt(legacy, "max_tokens", 0) >= 2048);
        assertNull(JSONCompat.optString(legacy, "reasoning_effort", null));
    }

    // ── Gemini wire ───────────────────────────────────────────────────────────

    @Test
    public void geminiBodyMatchesTheGenerateContentSchema() throws IOException {
        JSONObject body = ProviderClient.buildRequest(provider(Provider.WIRE_GEMINI), PROMPT, SYSTEM);

        var contents = JSONCompat.optJSONArray(body, "contents");
        assertNotNull(contents);
        var parts = JSONCompat.optJSONArray(JSONCompat.optJSONObject(contents, 0), "parts");
        assertEquals(PROMPT, JSONCompat.optString(JSONCompat.optJSONObject(parts, 0), "text", null));

        JSONObject cfg = JSONCompat.optJSONObject(body, "generationConfig");
        assertNotNull("generationConfig carries the output budget", cfg);
        assertTrue(JSONCompat.optInt(cfg, "maxOutputTokens", 0) >= 4096);
    }

    @Test
    public void geminiThinkingIsKeptOutOfTheOutputBudget() throws IOException {
        // Reasoning tokens are charged against maxOutputTokens; a 50-item
        // batch got cut after 8 items once the model thought for the rest.
        JSONObject v3 = ProviderClient.thinkingConfig("gemini-3.8-flash");
        assertEquals("low", JSONCompat.optString(v3, "thinkingLevel", null));
        assertNull("both fields in one request is a 400", JSONCompat.optString(v3, "thinkingBudget", null));

        JSONObject v25 = ProviderClient.thinkingConfig("gemini-2.5-flash");
        assertEquals(0, JSONCompat.optInt(v25, "thinkingBudget", -1));

        assertNull("2.5 Pro cannot switch thinking off", ProviderClient.thinkingConfig("gemini-2.5-pro"));
        assertNull(ProviderClient.thinkingConfig("gemini-2.0-flash"));

        JSONObject body = ProviderClient.buildRequest(
                new Provider("g", "G", Provider.WIRE_GEMINI, "https://example.invalid",
                        "KEY", "gemini-3-flash-preview", null, null, null), PROMPT, SYSTEM);
        JSONObject cfg = JSONCompat.optJSONObject(body, "generationConfig");
        assertNotNull(JSONCompat.optJSONObject(cfg, "thinkingConfig"));
    }

    @Test
    public void truncatedResponsesReportTheirFinishReason() {
        assertEquals("MAX_TOKENS", ProviderClient.finishReasonOf(new JSONObject(
                "{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\",\"content\":{\"parts\":[{\"text\":\"[1] a\"}]}}]}")));
        assertEquals("length", ProviderClient.finishReasonOf(new JSONObject(
                "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"[1] a\"}}]}")));
        assertNull(ProviderClient.finishReasonOf(new JSONObject("{}")));
    }

    @Test
    public void geminiBodyCarriesNoSystemField() throws IOException {
        // v1beta generateContent has no system role, and the engine folds its
        // instructions into the prompt. Sending one would be silently ignored
        // at best and rejected at worst.
        JSONObject body = ProviderClient.buildRequest(provider(Provider.WIRE_GEMINI), PROMPT, SYSTEM);
        assertNull(JSONCompat.optString(body, "system", null));
        assertNull(JSONCompat.optJSONArray(body, "messages"));
    }

    @Test
    public void geminiResponseIsParsedFromCandidates() throws IOException {
        JSONObject response = new JSONObject("{\"candidates\":[{\"content\":{\"parts\":"
                + "[{\"text\":\"Merhaba\"}]}}]}");
        assertEquals("Merhaba",
                ProviderClient.parseResponse(provider(Provider.WIRE_GEMINI), response));
    }

    @Test
    public void geminiResponseHasSurroundingQuotesStripped() throws IOException {
        JSONObject response = new JSONObject("{\"candidates\":[{\"content\":{\"parts\":"
                + "[{\"text\":\"\\\"Merhaba\\\"\"}]}}]}");
        assertEquals("Merhaba",
                ProviderClient.parseResponse(provider(Provider.WIRE_GEMINI), response));
    }

    // ── Output budget ─────────────────────────────────────────────────────────

    @Test
    public void outputBudgetGrowsWithTheInput() throws IOException {
        // A fixed cap truncates large batches mid-list, losing every item after
        // the cut. Guard the scaling, not just the floor.
        String big = "x".repeat(30000);
        JSONObject small = ProviderClient.buildRequest(provider(Provider.WIRE_OPENAI), "hi", SYSTEM);
        JSONObject large = ProviderClient.buildRequest(provider(Provider.WIRE_OPENAI), big, SYSTEM);
        assertEquals(2048, JSONCompat.optInt(small, "max_tokens", 0));
        assertTrue("large batches must get a larger budget",
                JSONCompat.optInt(large, "max_tokens", 0) > 2048);
    }

    // ── Errors ────────────────────────────────────────────────────────────────

    @Test
    public void errorNodeIsSurfacedRatherThanParsedAsATranslation() {
        JSONObject response = new JSONObject(
                "{\"error\":{\"code\":429,\"message\":\"Rate limit exceeded\"}}");
        JSONObject error = ProviderClient.errorOf(response);
        assertNotNull("a 429 body must be reported, never treated as content", error);
        assertEquals(429, JSONCompat.optInt(error, "code", -1));
        assertEquals("Rate limit exceeded", JSONCompat.optString(error, "message", null));
    }

    @Test
    public void healthyResponseHasNoErrorNode() {
        assertNull(ProviderClient.errorOf(
                new JSONObject("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}")));
    }

    @Test
    public void emptyResponseFailsLoudly() {
        for (String wire : new String[]{
                Provider.WIRE_OPENAI, Provider.WIRE_ANTHROPIC, Provider.WIRE_GEMINI}) {
            try {
                ProviderClient.parseResponse(provider(wire), new JSONObject("{}"));
                fail("empty " + wire + " response must raise, not return empty text");
            } catch (IOException expected) {
                // A silent empty string would overwrite the user's string with "".
            }
        }
    }
}
