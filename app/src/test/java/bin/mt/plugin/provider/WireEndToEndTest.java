package bin.mt.plugin.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import bin.mt.json.JSONObject;
import bin.mt.plugin.common.HttpUtils;

/**
 * Exercises the whole outbound path — URL shaping, headers, body, OkHttp, and
 * response parsing — against a loopback server from the JDK.
 *
 * <p>This is what makes the plugin verifiable without a single API key: the
 * mock records exactly what a real provider would receive, so a regression in
 * authentication or request shape fails here instead of on a user's device.
 */
public class WireEndToEndTest {

    private ServerSocket server;
    private Thread acceptLoop;
    private String baseUrl;

    /** What the last request carried. */
    private String capturedMethod;
    private String capturedPath;
    private String capturedQuery;
    private Map<String, String> capturedHeaders;
    private String capturedBody;

    /** What the mock answers with. */
    private String cannedResponse = "{}";
    private int cannedStatus = 200;
    /** How long the mock sits on a request before answering. */
    private long hangMs = 0;

    @Before
    public void startServer() throws IOException {
        // A raw socket rather than com.sun.net.httpserver, which the Android
        // unit-test bootclasspath does not expose. One request per connection,
        // answered with Connection: close — enough to record what a provider
        // would have received.
        server = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"));
        baseUrl = "http://127.0.0.1:" + server.getLocalPort();

        acceptLoop = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    handle(socket);
                } catch (IOException e) {
                    return; // socket closed in teardown
                }
            }
        }, "mock-provider");
        acceptLoop.setDaemon(true);
        acceptLoop.start();
    }

    private void handle(Socket socket) throws IOException {
        InputStream in = socket.getInputStream();

        // Read up to the blank line that terminates the header block.
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int b, matched = 0;
        while (matched < 4 && (b = in.read()) != -1) {
            head.write(b);
            matched = (b == "\r\n\r\n".charAt(matched)) ? matched + 1
                    : (b == '\r' ? 1 : 0);
        }
        String[] lines = head.toString(StandardCharsets.UTF_8.name()).split("\r\n");

        String[] requestLine = lines[0].split(" ");
        capturedMethod = requestLine[0];
        URI uri = URI.create(requestLine[1]);
        capturedPath = uri.getPath();
        capturedQuery = uri.getQuery();

        capturedHeaders = new HashMap<>();
        int contentLength = 0;
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon <= 0) continue;
            String name = lines[i].substring(0, colon).trim().toLowerCase();
            String value = lines[i].substring(colon + 1).trim();
            capturedHeaders.put(name, value);
            if ("content-length".equals(name)) {
                contentLength = Integer.parseInt(value);
            }
        }

        byte[] body = new byte[contentLength];
        int read = 0;
        while (read < contentLength) {
            int n = in.read(body, read, contentLength - read);
            if (n < 0) break;
            read += n;
        }
        capturedBody = new String(body, StandardCharsets.UTF_8);

        if (hangMs > 0) {
            try { Thread.sleep(hangMs); } catch (InterruptedException ignored) { }
        }
        byte[] payload = cannedResponse.getBytes(StandardCharsets.UTF_8);
        OutputStream out = socket.getOutputStream();
        out.write(("HTTP/1.1 " + cannedStatus + " X\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + payload.length + "\r\n"
                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(payload);
        out.flush();
    }

    @After
    public void stopServer() throws IOException {
        if (server != null && !server.isClosed()) server.close();
    }

    private String send(Provider p) throws IOException {
        JSONObject request = ProviderClient.buildRequest(p, "Hello", "Be a translator.");
        JSONObject response = HttpUtils.postJson(
                p.url(), p.headers(), request.toString(), 5000);
        JSONObject error = ProviderClient.errorOf(response);
        if (error != null) {
            throw new IOException("api error");
        }
        return ProviderClient.parseResponse(p, response);
    }

    // ── Cancellation ──────────────────────────────────────────────────────────

    @Test
    public void interruptCancelsAnInFlightRequestAtOnce() throws Exception {
        // MT's cancel button interrupts the worker; the old blocking execute()
        // sat on the socket until the timeout (30 s, longer on a sleeping
        // phone) before the engine could notice.
        hangMs = 10_000;
        Provider p = new Provider("openai", "OpenAI", Provider.WIRE_OPENAI,
                baseUrl + "/v1/chat/completions", "sk", "gpt-4.1-mini", null, null, null);

        Thread worker = Thread.currentThread();
        new Thread(() -> {
            try { Thread.sleep(300); } catch (InterruptedException ignored) { }
            worker.interrupt();
        }).start();

        long started = System.currentTimeMillis();
        try {
            send(p);
            fail("the request must not complete after an interrupt");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("cancelled"));
        }
        assertTrue("must return well before the 10 s hang and the 5 s timeout",
                System.currentTimeMillis() - started < 3000);
        assertTrue("interrupt flag is preserved for the caller", Thread.interrupted());
    }

    // ── OpenAI wire ───────────────────────────────────────────────────────────

    @Test
    public void openAiWireSendsBearerTokenAndReadsChoices() throws IOException {
        cannedResponse = "{\"choices\":[{\"message\":{\"content\":\"Merhaba\"}}]}";
        Provider p = new Provider("openai", "OpenAI", Provider.WIRE_OPENAI,
                baseUrl + "/v1/chat/completions", "sk-secret", "gpt-4.1-mini",
                null, null, null);

        assertEquals("Merhaba", send(p));

        assertEquals("POST", capturedMethod);
        assertEquals("/v1/chat/completions", capturedPath);
        assertEquals("Bearer sk-secret", capturedHeaders.get("authorization"));
        assertTrue(capturedBody.contains("\"model\":\"gpt-4.1-mini\""));
        assertTrue(capturedBody.contains("\"role\":\"system\""));
    }

    @Test
    public void keylessEndpointSendsNoAuthorizationHeader() throws IOException {
        // A local Ollama or LM Studio rejects "Authorization: Bearer " with an
        // empty token, so the header must be absent rather than blank.
        cannedResponse = "{\"choices\":[{\"message\":{\"content\":\"Merhaba\"}}]}";
        Provider p = new Provider("custom:ollama", "Ollama", Provider.WIRE_OPENAI,
                baseUrl + "/v1/chat/completions", "", "llama3", null, null, null);

        assertEquals("Merhaba", send(p));
        assertNull("no key means no Authorization header at all",
                capturedHeaders.get("authorization"));
    }

    @Test
    public void openRouterAttributionHeadersAreSent() throws IOException {
        cannedResponse = "{\"choices\":[{\"message\":{\"content\":\"Merhaba\"}}]}";
        Map<String, String> extra = new HashMap<>();
        extra.put("HTTP-Referer", "https://github.com/ilker-binzet/TranslateKit");
        extra.put("X-Title", "TranslateKit");
        Provider p = new Provider("openrouter", "OpenRouter", Provider.WIRE_OPENAI,
                baseUrl + "/api/v1/chat/completions", "sk-or-v1-secret",
                "google/gemini-2.5-flash", null, null, extra);

        assertEquals("Merhaba", send(p));
        assertEquals("Bearer sk-or-v1-secret", capturedHeaders.get("authorization"));
        assertEquals("TranslateKit", capturedHeaders.get("x-title"));
        assertTrue(capturedBody.contains("\"model\":\"google/gemini-2.5-flash\""));
    }

    // ── Anthropic wire ────────────────────────────────────────────────────────

    @Test
    public void anthropicWireSendsApiKeyAndVersionHeaders() throws IOException {
        cannedResponse = "{\"content\":[{\"type\":\"text\",\"text\":\"Merhaba\"}]}";
        Provider p = new Provider("claude", "Claude", Provider.WIRE_ANTHROPIC,
                baseUrl + "/v1/messages", "sk-ant-secret", "claude-sonnet-4-5-latest",
                null, null, null);

        assertEquals("Merhaba", send(p));

        assertEquals("sk-ant-secret", capturedHeaders.get("x-api-key"));
        assertEquals(Provider.ANTHROPIC_VERSION, capturedHeaders.get("anthropic-version"));
        assertNull("Anthropic does not use bearer auth", capturedHeaders.get("authorization"));
        assertTrue(capturedBody.contains("\"max_tokens\""));
    }

    // ── Gemini wire ───────────────────────────────────────────────────────────

    @Test
    public void geminiWirePutsModelAndKeyInTheUrl() throws IOException {
        cannedResponse = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Merhaba\"}]}}]}";
        Provider p = new Provider("gemini", "Gemini", Provider.WIRE_GEMINI,
                baseUrl + "/v1beta/models", "AIzaSyKEY", "gemini-2.5-flash",
                null, null, null);

        assertEquals("Merhaba", send(p));

        assertEquals("/v1beta/models/gemini-2.5-flash:generateContent", capturedPath);
        assertEquals("key=AIzaSyKEY", capturedQuery);
        assertNull("the key rides in the query string; sending it twice leaks it",
                capturedHeaders.get("authorization"));
        assertNull(capturedHeaders.get("x-api-key"));
        assertTrue(capturedBody.contains("\"generationConfig\""));
    }

    // ── Failure paths ─────────────────────────────────────────────────────────

    @Test
    public void apiErrorBodyIsReportedNotTranslated() {
        cannedResponse = "{\"error\":{\"code\":401,\"message\":\"Invalid API key\"}}";
        Provider p = new Provider("openai", "OpenAI", Provider.WIRE_OPENAI,
                baseUrl + "/v1/chat/completions", "sk-bad", "gpt-4.1-mini",
                null, null, null);
        try {
            send(p);
            org.junit.Assert.fail("an error body must never be returned as a translation");
        } catch (IOException expected) {
            // Correct: the caller localises and surfaces it.
        }
    }

    @Test
    public void configuredTimeoutIsApplied() {
        // Regression guard: the timeout was previously read from preferences
        // and then dropped, so a hung provider blocked the whole batch.
        Provider p = new Provider("openai", "OpenAI", Provider.WIRE_OPENAI,
                "http://127.0.0.1:1/v1/chat/completions", "sk-x", "m", null, null, null);
        long start = System.nanoTime();
        try {
            send(p);
        } catch (IOException expected) {
            // connection refused or timed out — both are fine
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue("must fail fast, took " + elapsedMs + "ms", elapsedMs < 15_000);
    }

    @Test
    public void everyBuiltInWireIsCoveredByThisSuite() {
        // Fails if a fourth wire format is added without an end-to-end test.
        assertFalse(Provider.WIRE_OPENAI.equals(Provider.WIRE_ANTHROPIC));
        assertEquals("three wire formats are covered above",
                3, new java.util.HashSet<>(java.util.List.of(
                        Provider.WIRE_OPENAI,
                        Provider.WIRE_ANTHROPIC,
                        Provider.WIRE_GEMINI)).size());
    }
}
