package bin.mt.plugin.provider;

import java.io.IOException;

import bin.mt.json.JSONArray;
import bin.mt.json.JSONObject;
import bin.mt.plugin.common.JSONCompat;

/**
 * Builds request bodies and reads responses for each wire format.
 *
 * <p>Every method branches on {@link Provider#wire}, never on provider
 * identity — which is what makes a new OpenAI-compatible endpoint a registry
 * row instead of a new code path.
 *
 * <p>Holds no retry, logging or localisation logic. Error messages are
 * returned raw through {@link #errorOf}; callers that have a
 * {@code PluginContext} format them for display, callers that do not use them
 * as-is.
 *
 * @author Ilker Binzet
 */
public final class ProviderClient {

    private ProviderClient() {
        throw new AssertionError("Cannot instantiate");
    }

    // ── Requests ──────────────────────────────────────────────────────────────

    /**
     * Builds the request body for a provider's wire format.
     *
     * <p>{@code systemPrompt} is ignored on the Gemini wire, which has no
     * system role on v1beta — the callers fold their instructions into the
     * prompt itself instead.
     */
    public static JSONObject buildRequest(Provider p, String prompt, String systemPrompt)
            throws IOException {
        try {
            if (Provider.WIRE_ANTHROPIC.equals(p.wire)) {
                return anthropicBody(p, prompt, systemPrompt);
            }
            if (Provider.WIRE_GEMINI.equals(p.wire)) {
                return geminiBody(p, prompt);
            }
            return openAiBody(p, prompt, systemPrompt);
        } catch (Exception e) {
            throw new IOException("Failed to build " + p.wire + " request: " + e.getMessage(), e);
        }
    }

    private static JSONObject openAiBody(Provider p, String prompt, String systemPrompt) {
        JSONObject request = new JSONObject();
        request.put("model", p.model);

        JSONArray messages = new JSONArray();
        JSONObject system = new JSONObject();
        system.put("role", "system");
        system.put("content", systemPrompt);
        JSONCompat.put(messages, system);

        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", prompt);
        JSONCompat.put(messages, user);

        request.put("messages", messages);
        // Scale with input size — a fixed cap truncates large batches.
        int budget = clampTokens(prompt.length() / 2, 2048, 8192);
        if (Providers.ID_OPENAI.equals(p.id) && isOpenAiReasoningModel(p.model)) {
            // gpt-5 / o-series reject max_tokens and any temperature but the
            // default; reasoning is billed from max_completion_tokens, so keep
            // it low or a 50-item batch comes back empty.
            request.put("max_completion_tokens", budget);
            request.put("reasoning_effort", "low");
            return request;
        }
        request.put("temperature", 0.1);
        request.put("max_tokens", budget);
        if (Providers.ID_OPENROUTER.equals(p.id)) {
            // Hybrid models (DeepSeek, Gemini, Claude) reason by default on
            // OpenRouter and spend max_tokens on it first — "Response was
            // empty" in the log. Off where allowed, "low" where mandatory.
            JSONObject reasoning = new JSONObject();
            reasoning.put("enabled", false);
            reasoning.put("effort", "low");
            request.put("reasoning", reasoning);
        }
        return request;
    }

    static boolean isOpenAiReasoningModel(String model) {
        String m = model == null ? "" : model.toLowerCase();
        return m.startsWith("gpt-5") || m.matches("o\\d.*");
    }

    private static JSONObject anthropicBody(Provider p, String prompt, String systemPrompt) {
        JSONObject request = new JSONObject();
        request.put("model", p.model);
        // ~1 token per 3-4 chars, clamped so we never exceed a model's ceiling.
        request.put("max_tokens", clampTokens(prompt.length() / 3, 2048, 8192));
        request.put("system", systemPrompt);

        JSONArray content = new JSONArray();
        JSONObject textBlock = new JSONObject();
        textBlock.put("type", "text");
        textBlock.put("text", prompt);
        JSONCompat.put(content, textBlock);

        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", content);

        JSONArray messages = new JSONArray();
        JSONCompat.put(messages, userMessage);
        request.put("messages", messages);
        return request;
    }

    private static JSONObject geminiBody(Provider p, String prompt) {
        JSONObject request = new JSONObject();

        JSONObject part = new JSONObject();
        part.put("text", prompt);

        JSONArray parts = new JSONArray();
        JSONCompat.put(parts, part);

        JSONObject content = new JSONObject();
        content.put("parts", parts);

        JSONArray contents = new JSONArray();
        JSONCompat.put(contents, content);
        request.put("contents", contents);

        JSONObject generationConfig = new JSONObject();
        generationConfig.put("temperature", 0.1);
        // Scale the output budget with the input: a fixed cap truncates large
        // batches mid-list (every item after the cut is silently lost), and on
        // thinking models the hidden reasoning tokens also count against it.
        generationConfig.put("maxOutputTokens", clampTokens(prompt.length(), 4096, 32768));
        generationConfig.put("topP", 0.8);
        generationConfig.put("topK", 10);
        JSONObject thinking = thinkingConfig(p.model);
        if (thinking != null) {
            generationConfig.put("thinkingConfig", thinking);
        }
        request.put("generationConfig", generationConfig);
        return request;
    }

    /**
     * Keeps reasoning out of the output budget. Thinking models spend their
     * hidden reasoning tokens from {@code maxOutputTokens} first; on a batch
     * of 50 UI strings that ate the budget and cut the numbered list after a
     * handful of items. Translation needs no deep reasoning.
     *
     * <p>Gemini 3.x takes {@code thinkingLevel} ("minimal" is not on every
     * 3.x model, "low" is); 2.5 Flash takes {@code thinkingBudget: 0}. 2.5
     * Pro cannot switch thinking off, and sending both fields is a 400.
     */
    static JSONObject thinkingConfig(String model) {
        String m = model == null ? "" : model.toLowerCase();
        JSONObject cfg = new JSONObject();
        if (m.startsWith("gemini-3")) {
            cfg.put("thinkingLevel", "low");
            return cfg;
        }
        if (m.startsWith("gemini-2.5") && m.contains("flash")) {
            cfg.put("thinkingBudget", 0);
            return cfg;
        }
        return null;
    }

    static int clampTokens(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ── Responses ─────────────────────────────────────────────────────────────

    /**
     * The API's error node, or null when the response carries none. Returned
     * rather than thrown so callers with a plugin context can localise the
     * message and decide whether the failure is retryable.
     */
    public static JSONObject errorOf(JSONObject response) {
        return response == null ? null : JSONCompat.optJSONObject(response, "error");
    }

    /**
     * The candidate's {@code finishReason} (Gemini wire) or {@code finish_reason}
     * (OpenAI wire), or null. "MAX_TOKENS"/"length" means the text was cut
     * mid-answer; callers log it so a truncated batch is visible as such
     * rather than as an unexplained parse mismatch.
     */
    public static String finishReasonOf(JSONObject response) {
        if (response == null) return null;
        JSONArray candidates = JSONCompat.optJSONArray(response, "candidates");
        if (candidates == null) candidates = JSONCompat.optJSONArray(response, "choices");
        if (candidates == null || JSONCompat.size(candidates) == 0) return null;
        JSONObject first = JSONCompat.optJSONObject(candidates, 0);
        if (first == null) return null;
        String reason = JSONCompat.optString(first, "finishReason", null);
        return reason != null ? reason : JSONCompat.optString(first, "finish_reason", null);
    }

    /** Reads the translated text out of a successful response. */
    public static String parseResponse(Provider p, JSONObject response) throws IOException {
        if (Provider.WIRE_ANTHROPIC.equals(p.wire)) {
            return parseAnthropic(response);
        }
        if (Provider.WIRE_GEMINI.equals(p.wire)) {
            return parseGemini(response);
        }
        return parseOpenAi(response);
    }

    private static String parseGemini(JSONObject json) throws IOException {
        try {
            JSONArray candidates = JSONCompat.optJSONArray(json, "candidates");
            if (candidates == null || JSONCompat.size(candidates) == 0) {
                throw new IOException("⚠️ No translation returned from API");
            }
            JSONObject candidate = JSONCompat.optJSONObject(candidates, 0);
            if (candidate == null) {
                throw new IOException("⚠️ Invalid candidate in response");
            }
            JSONObject content = candidate.getJSONObject("content");
            JSONArray parts = content.getJSONArray("parts");
            if (JSONCompat.size(parts) == 0) {
                throw new IOException("⚠️ Empty translation response");
            }

            String translation = JSONCompat.optJSONObject(parts, 0).getString("text").trim();
            // Models sometimes wrap the whole answer in quotes.
            if (translation.startsWith("\"") && translation.endsWith("\"")) {
                translation = translation.substring(1, translation.length() - 1);
            }
            return translation;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to parse Gemini response: " + e.getMessage(), e);
        }
    }

    private static String parseOpenAi(JSONObject response) throws IOException {
        try {
            JSONArray choices = JSONCompat.optJSONArray(response, "choices");
            if (choices == null || JSONCompat.size(choices) == 0) {
                throw new IOException("⚠️ Response did not include choices");
            }

            JSONObject message = JSONCompat.optJSONObject(choices, 0);
            if (message != null) {
                message = JSONCompat.optJSONObject(message, "message");
            }
            if (message == null) {
                throw new IOException("⚠️ Response missing message payload");
            }

            // `content` may be a plain string or an array of content parts.
            String translation;
            try {
                translation = extractContentText(message.getString("content"));
            } catch (Exception stringFail) {
                translation = extractContentText(JSONCompat.optJSONArray(message, "content"));
            }
            if (translation.isEmpty()) {
                throw new IOException("⚠️ Response was empty");
            }
            return translation;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to parse response: " + e.getMessage(), e);
        }
    }

    private static String parseAnthropic(JSONObject response) throws IOException {
        JSONArray contentArray = JSONCompat.optJSONArray(response, "content");
        if (contentArray == null || JSONCompat.size(contentArray) == 0) {
            throw new IOException("⚠️ Claude response did not include content");
        }

        String translation = extractContentText(contentArray);
        if (translation.isEmpty()) {
            throw new IOException("⚠️ Claude response was empty");
        }
        return translation;
    }

    /** Accepts a plain string, or an array of {"text": ...} blocks. */
    private static String extractContentText(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String) {
            return ((String) content).trim();
        }
        if (content instanceof JSONArray) {
            JSONArray array = (JSONArray) content;
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < JSONCompat.size(array); i++) {
                Object item = array.get(i);
                if (item instanceof JSONObject) {
                    String text = JSONCompat.optString((JSONObject) item, "text", null);
                    if (text != null && !text.isEmpty()) {
                        builder.append(text);
                    }
                } else if (item instanceof String) {
                    builder.append((String) item);
                }
            }
            return builder.toString().trim();
        }
        return content.toString().trim();
    }
}
