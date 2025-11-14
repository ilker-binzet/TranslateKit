package bin.mt.plugin.gemini;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP utility class for Gemini API requests
 *
 * @author MT Manager Plugin Developer
 * @version 1.0.0
 */
public class GeminiHttpUtils {

    public static Request post(String url) {
        return new Request(url, "POST");
    }

    public static Request get(String url) {
        return new Request(url, "GET");
    }

    public static class Request {
        private final String url;
        private final String method;
        private final Map<String, String> headers;
        private int connectTimeout = GeminiConstants.DEFAULT_TIMEOUT;
        private int readTimeout = GeminiConstants.DEFAULT_TIMEOUT;
        private byte[] requestBody;

        private Request(String url, String method) {
            this.url = url;
            this.method = method;
            this.headers = new LinkedHashMap<>();
            this.headers.put("User-Agent", "MTManager-Gemini-Plugin/1.0");
        }

        public Request header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        public Request setTimeout(int timeoutMs) {
            this.connectTimeout = timeoutMs;
            this.readTimeout = timeoutMs;
            return this;
        }

        public Request jsonBody(JSONObject json) {
            this.requestBody = json.toString().getBytes(StandardCharsets.UTF_8);
            this.headers.put("Content-Type", "application/json; charset=UTF-8");
            return this;
        }

        public String execute() throws IOException {
            HttpURLConnection conn = null;
            InputStream inputStream = null;
            InputStream errorStream = null;

            try {
                conn = createConnection();
                int responseCode = conn.getResponseCode();

                if (responseCode >= 200 && responseCode < 300) {
                    inputStream = conn.getInputStream();
                    return readStream(inputStream);
                } else {
                    errorStream = conn.getErrorStream();
                    String errorBody = errorStream != null ? readStream(errorStream) : "";
                    throw new IOException("HTTP " + responseCode + ": " + errorBody);
                }

            } finally {
                if (inputStream != null) {
                    try { inputStream.close(); } catch (IOException ignored) {}
                }
                if (errorStream != null) {
                    try { errorStream.close(); } catch (IOException ignored) {}
                }
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }

        public JSONObject executeToJson() throws IOException {
            String responseBody = execute();
            try {
                return new JSONObject(responseBody);
            } catch (JSONException e) {
                throw new IOException("Failed to parse JSON response: " + e.getMessage(), e);
            }
        }

        private HttpURLConnection createConnection() throws IOException {
            URL urlObject = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) urlObject.openConnection();

            conn.setRequestMethod(method);
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setUseCaches(false);
            conn.setDoInput(true);

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                conn.setRequestProperty(entry.getKey(), entry.getValue());
            }

            if (requestBody != null && requestBody.length > 0) {
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(requestBody.length);

                OutputStream out = conn.getOutputStream();
                try {
                    out.write(requestBody);
                    out.flush();
                } finally {
                    out.close();
                }
            }

            return conn;
        }

        private String readStream(InputStream is) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int bytesRead;

            while ((bytesRead = is.read(chunk)) != -1) {
                buffer.write(chunk, 0, bytesRead);
            }

            return buffer.toString("UTF-8");
        }
    }
}
