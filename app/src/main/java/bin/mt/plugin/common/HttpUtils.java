package bin.mt.plugin.common;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import bin.mt.json.JSONObject;

/**
 * Unified HTTP client for TranslateKit.
 *
 * <p>Single OkHttp-backed entry point used by both the Gemini / OpenAI / Claude
 * engines and the per-provider preference screens. Replaces the previous
 * {@code GeminiHttpUtils} and {@code bin.mt.plugin.google.HttpUtils} which
 * both wrapped {@link java.net.HttpURLConnection} with duplicated logic.</p>
 *
 * <p>Built on OkHttp <b>3.12.13</b> — the exact version the MT Plugin SDK
 * declares transitively and that MT Manager provides from its own runtime
 * classloader. Do NOT add a newer OkHttp to the build: the host's classes
 * always win at runtime (parent-first classloading), so code compiled
 * against a newer API dies with {@link NoSuchMethodError}. Stick to
 * 3.12-era signatures, e.g. {@code RequestBody.create(MediaType, String)}.</p>
 */
public final class HttpUtils {

    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.get("application/json; charset=utf-8");

    /** Shared, long-lived client. OkHttp is designed to be reused. */
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .callTimeout(120, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    private HttpUtils() {}

    /**
     * Execute a POST with a JSON body and return the parsed {@link JSONObject}.
     *
     * @param url          full request URL
     * @param headers      optional headers (may be null)
     * @param jsonBody     serialised JSON body
     * @return parsed response
     * @throws IOException on network error or non-2xx response (with
     *                     [Retry-After: N] prefix when the server returns one)
     */
    public static JSONObject postJson(String url,
                                      Map<String, String> headers,
                                      String jsonBody) throws IOException {
        return postJson(url, headers, jsonBody, 0);
    }

    /**
     * POST with a caller-supplied timeout. {@code timeoutMs <= 0} keeps the
     * shared client's defaults. The per-call client is a cheap
     * {@code newBuilder()} clone — it shares the connection pool and threads.
     */
    public static JSONObject postJson(String url,
                                      Map<String, String> headers,
                                      String jsonBody,
                                      int timeoutMs) throws IOException {
        Request.Builder rb = new Request.Builder()
                .url(url)
                .post(RequestBody.create(JSON_MEDIA_TYPE, jsonBody));
        applyHeaders(rb, headers);
        return execute(rb.build(), timeoutMs);
    }

    /** Convenience overload with no extra headers. */
    public static JSONObject postJson(String url, String jsonBody) throws IOException {
        return postJson(url, null, jsonBody);
    }

    /**
     * Execute a GET and return the parsed {@link JSONObject}.
     */
    public static JSONObject getJson(String url,
                                     Map<String, String> headers) throws IOException {
        Request.Builder rb = new Request.Builder().url(url).get();
        applyHeaders(rb, headers);
        return execute(rb.build());
    }

    /** Convenience overload with no extra headers. */
    public static JSONObject getJson(String url) throws IOException {
        return getJson(url, null);
    }

    /**
     * Build the raw {@link Request} for callers that need finer control
     * (custom timeouts, streaming bodies, etc.). Use {@link #execute(Request)}
     * to run it.
     */
    public static Request.Builder requestBuilder(String url) {
        Request.Builder rb = new Request.Builder().url(url);
        rb.header("User-Agent", "TranslateKit/0.4 (MT Manager plugin)");
        return rb;
    }

    /**
     * Execute a pre-built {@link Request} and parse the response as JSON.
     * Throws IOException with a [Retry-After: N] prefix when the server
     * returns a 429/503 with that header — matches the previous convention
     * so the engine's {@code parseRetryAfterMs} keeps working unchanged.
     */
    public static JSONObject execute(Request request) throws IOException {
        return execute(request, 0);
    }

    /** Execute with a caller-supplied timeout; {@code timeoutMs <= 0} keeps defaults. */
    public static JSONObject execute(Request request, int timeoutMs) throws IOException {
        OkHttpClient client = CLIENT;
        if (timeoutMs > 0) {
            client = CLIENT.newBuilder()
                    .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .connectTimeout(Math.min(timeoutMs, 15_000), TimeUnit.MILLISECONDS)
                    .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .build();
        }
        try (Response response = client.newCall(request).execute()) {
            ResponseBody body = response.body();
            String bodyText = body == null ? "" : body.string();
            int code = response.code();
            if (code < 200 || code >= 300) {
                String retryAfter = (code == 429 || code == 503)
                        ? response.header("Retry-After")
                        : null;
                String prefix = "HTTP " + code;
                if (retryAfter != null && !retryAfter.isEmpty()) {
                    prefix += " [Retry-After: " + retryAfter + "]";
                }
                throw new IOException(prefix + ": " + bodyText);
            }
            if (bodyText.isEmpty()) {
                return new JSONObject();
            }
            return new JSONObject(bodyText);
        }
    }

    /** Plain-text GET for endpoints that don't return JSON. */
    public static String getString(String url,
                                   Map<String, String> headers) throws IOException {
        Request.Builder rb = new Request.Builder().url(url).get();
        applyHeaders(rb, headers);
        try (Response response = CLIENT.newCall(rb.build()).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": "
                        + (body == null ? "" : body.string()));
            }
            return body == null ? "" : body.string();
        }
    }

    private static void applyHeaders(Request.Builder rb, Map<String, String> headers) {
        if (headers == null) return;
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            rb.header(e.getKey(), e.getValue());
        }
    }
}
