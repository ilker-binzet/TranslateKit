package bin.mt.plugin.provider;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable description of one translation provider: where to send a request,
 * how to authenticate, and which wire format the endpoint speaks.
 *
 * <p>Providers are many; wire formats are three. Everything that varies per
 * provider is data on this object, so adding an endpoint is a registry row
 * rather than a new code path.
 *
 * <p>Deliberately free of Android and JSON imports — that is what keeps the
 * addressing rules unit testable. Request bodies live in {@link ProviderClient},
 * which cannot be loaded outside the app because the SDK's JSON types are not
 * on the unit test classpath.
 *
 * <p>Instances are built by {@link Providers}.
 *
 * @author Ilker Binzet
 */
public final class Provider {

    /** POST {endpoint} with a chat/completions body. OpenAI, OpenRouter, Groq, Ollama, ... */
    public static final String WIRE_OPENAI = "openai";

    /** POST {endpoint} with a Messages API body. Anthropic Claude. */
    public static final String WIRE_ANTHROPIC = "anthropic";

    /** POST {endpoint}/{model}:generateContent?key={apiKey}. Google Gemini. */
    public static final String WIRE_GEMINI = "gemini";

    public static final String ANTHROPIC_VERSION = "2023-06-01";

    private static final String CHAT_COMPLETIONS = "/chat/completions";

    public final String id;
    public final String displayName;
    public final String wire;
    public final String endpoint;
    public final String apiKey;
    public final String model;

    /** Regex the API key must match, or null when the provider needs no key. */
    public final String keyPattern;

    /** Catalog fetch URL, or null when the provider has no model listing API. */
    public final String modelsEndpoint;

    /** Always non-null; empty for every provider except OpenRouter. */
    public final Map<String, String> extraHeaders;

    public Provider(String id,
                    String displayName,
                    String wire,
                    String endpoint,
                    String apiKey,
                    String model,
                    String keyPattern,
                    String modelsEndpoint,
                    Map<String, String> extraHeaders) {
        this.id = id;
        this.displayName = displayName;
        this.wire = wire;
        this.endpoint = endpoint;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model;
        this.keyPattern = keyPattern;
        this.modelsEndpoint = modelsEndpoint;
        this.extraHeaders = extraHeaders == null
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(extraHeaders));
    }

    /** Copy carrying a different model. Used when a retired model id forces a fallback. */
    public Provider withModel(String newModel) {
        return new Provider(id, displayName, wire, endpoint, apiKey, newModel,
                keyPattern, modelsEndpoint, extraHeaders);
    }

    /** Whether this provider needs an API key at all. */
    public boolean requiresKey() {
        return keyPattern != null;
    }

    /**
     * True when no key is required, or the configured key matches the expected
     * format. A false result is a warning, not a hard failure — providers
     * change their key formats without notice.
     */
    public boolean hasValidKeyFormat() {
        if (keyPattern == null) {
            return true;
        }
        return apiKey != null && apiKey.matches(keyPattern);
    }

    /**
     * How this provider's configuration reads: {@code "ready"}, {@code "invalid"}
     * or {@code "neutral"}.
     *
     * <p>Lives here rather than on a screen so that every place showing provider
     * health reaches the same verdict — the settings list and the diagnostics
     * dashboard used to decide this separately and disagreed.
     *
     * <p>The values are the ones {@code GeminiColorTokens.getStatusColor}
     * understands, so the colour follows from the verdict.
     */
    public String statusType() {
        if (!requiresKey()) {
            // A self-hosted endpoint is configured once it names a model.
            return model == null || model.isEmpty() ? "neutral" : "ready";
        }
        if (apiKey.isEmpty()) {
            return "neutral";
        }
        return hasValidKeyFormat() ? "ready" : "invalid";
    }

    /**
     * Gemini takes the model and key in the path and query string; every other
     * wire posts to the configured endpoint unchanged.
     */
    public String url() {
        if (WIRE_GEMINI.equals(wire)) {
            return endpoint + "/" + model + ":generateContent?key=" + apiKey;
        }
        return endpoint;
    }

    public Map<String, String> headers() {
        Map<String, String> h = new HashMap<>();
        if (WIRE_OPENAI.equals(wire)) {
            // A blank bearer token is worse than none: local servers reject it.
            if (!apiKey.isEmpty()) {
                h.put("Authorization", "Bearer " + apiKey);
            }
        } else if (WIRE_ANTHROPIC.equals(wire)) {
            h.put("x-api-key", apiKey);
            h.put("anthropic-version", ANTHROPIC_VERSION);
        }
        // Gemini authenticates through the query string, so it adds nothing here.
        h.putAll(extraHeaders);
        return h;
    }

    /** Accepts either a base URL or an already-complete chat/completions URL. */
    public static String chatCompletionsUrl(String baseUrl) {
        String s = stripTrailingSlash(baseUrl);
        return s.endsWith(CHAT_COMPLETIONS) ? s : s + CHAT_COMPLETIONS;
    }

    /** Derives the model listing URL from either form of base URL. */
    public static String modelsUrl(String baseUrl) {
        String s = stripTrailingSlash(baseUrl);
        if (s.endsWith(CHAT_COMPLETIONS)) {
            s = s.substring(0, s.length() - CHAT_COMPLETIONS.length());
        }
        return s + "/models";
    }

    /** Stable id fragment for a user-supplied provider name. */
    public static String slug(String name) {
        String s = name.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-");
        if (s.startsWith("-")) {
            s = s.substring(1);
        }
        if (s.endsWith("-")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    @Override
    public String toString() {
        return "Provider{" + id + ", wire=" + wire + ", model=" + model + "}";
    }
}
