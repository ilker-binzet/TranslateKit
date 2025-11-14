package bin.mt.plugin.google;

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
 * HTTP utility class for making REST API requests
 *
 * This class provides a fluent API for HTTP GET and POST requests
 * with support for headers, form data, JSON payloads, and timeouts.
 *
 * Features:
 * - Fluent builder pattern
 * - Automatic charset handling
 * - Timeout configuration
 * - JSON response parsing
 * - Custom header support
 * - Form data encoding
 * - Connection pooling
 *
 * @author MT Manager Plugin Developer
 * @version 1.0.0
 */
public class HttpUtils {

    /**
     * Create a POST request builder
     *
     * @param url Target URL
     * @return Request builder for POST method
     */
    public static Request post(String url) {
        return new Request(url, "POST");
    }

    /**
     * Create a GET request builder
     *
     * @param url Target URL
     * @return Request builder for GET method
     */
    public static Request get(String url) {
        return new Request(url, "GET");
    }

    /**
     * HTTP Request builder with fluent API
     *
     * Usage example:
     * <pre>
     * String response = HttpUtils.get("https://api.example.com/data")
     *     .header("Authorization", "Bearer token")
     *     .setTimeout(30000)
     *     .execute();
     * </pre>
     */
    public static class Request {
        private final String url;
        private final String method;
        private final Map<String, String> headers;
        private String charset = "UTF-8";
        private int connectTimeout = GoogleConstants.DEFAULT_TIMEOUT;
        private int readTimeout = GoogleConstants.DEFAULT_TIMEOUT;
        private byte[] requestBody;
        private String contentType;

        /**
         * Internal constructor - use static factory methods
         *
         * @param url Target URL
         * @param method HTTP method (GET, POST, etc.)
         */
        private Request(String url, String method) {
            this.url = url;
            this.method = method;
            this.headers = new LinkedHashMap<>();
            this.headers.put("User-Agent", GoogleConstants.USER_AGENT);
        }

        /**
         * Set character encoding for request and response
         *
         * @param charset Character encoding (default: UTF-8)
         * @return this Request for chaining
         */
        public Request setCharset(String charset) {
            this.charset = charset;
            return this;
        }

        /**
         * Set custom HTTP header
         *
         * @param name Header name
         * @param value Header value
         * @return this Request for chaining
         */
        public Request header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        /**
         * Set request timeout in milliseconds
         *
         * @param timeoutMs Timeout in milliseconds (both connect and read)
         * @return this Request for chaining
         */
        public Request setTimeout(int timeoutMs) {
            this.connectTimeout = timeoutMs;
            this.readTimeout = timeoutMs;
            return this;
        }

        /**
         * Set separate connect and read timeouts
         *
         * @param connectTimeoutMs Connection timeout in milliseconds
         * @param readTimeoutMs Read timeout in milliseconds
         * @return this Request for chaining
         */
        public Request setTimeout(int connectTimeoutMs, int readTimeoutMs) {
            this.connectTimeout = connectTimeoutMs;
            this.readTimeout = readTimeoutMs;
            return this;
        }

        /**
         * Set JSON request body
         *
         * @param json JSONObject to send
         * @return this Request for chaining
         */
        public Request jsonBody(JSONObject json) {
            this.requestBody = json.toString().getBytes(StandardCharsets.UTF_8);
            this.contentType = GoogleConstants.CONTENT_TYPE_JSON;
            return this;
        }

        /**
         * Set raw text request body
         *
         * @param body Text content
         * @param contentType Content-Type header value
         * @return this Request for chaining
         */
        public Request textBody(String body, String contentType) {
            this.requestBody = body.getBytes(StandardCharsets.UTF_8);
            this.contentType = contentType;
            return this;
        }

        /**
         * Add form data parameter (application/x-www-form-urlencoded)
         * Multiple calls will append parameters
         *
         * @param key Parameter name
         * @param value Parameter value
         * @return this Request for chaining
         */
        public Request formData(String key, String value) {
            if (this.contentType == null) {
                this.contentType = "application/x-www-form-urlencoded; charset=" + charset;
                this.requestBody = new byte[0];
            }

            String encodedParam = encodeFormParam(key, value);
            byte[] paramBytes = encodedParam.getBytes(StandardCharsets.UTF_8);

            // Append to existing body with & separator if needed
            if (this.requestBody.length > 0) {
                byte[] separator = "&".getBytes(StandardCharsets.UTF_8);
                this.requestBody = concat(this.requestBody, separator, paramBytes);
            } else {
                this.requestBody = paramBytes;
            }

            return this;
        }

        /**
         * Execute the HTTP request and return response as String
         *
         * @return Response body as String
         * @throws IOException If network error or non-2xx response code
         */
        public String execute() throws IOException {
            HttpURLConnection conn = null;
            InputStream inputStream = null;
            InputStream errorStream = null;

            try {
                // Create and configure connection
                conn = createConnection();

                // Get response code
                int responseCode = conn.getResponseCode();

                // Read response body
                if (responseCode >= 200 && responseCode < 300) {
                    // Success response
                    inputStream = conn.getInputStream();
                    return readStream(inputStream, charset);
                } else {
                    // Error response - try to read error body
                    errorStream = conn.getErrorStream();
                    String errorBody = errorStream != null ? readStream(errorStream, charset) : "";

                    // Try to parse error as JSON for better error messages
                    String errorMessage = extractErrorMessage(errorBody, responseCode);
                    throw new IOException("HTTP " + responseCode + ": " + errorMessage);
                }

            } finally {
                // Clean up resources
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

        /**
         * Execute the HTTP request and parse response as JSON
         *
         * @return Response body parsed as JSONObject
         * @throws IOException If network error, non-2xx response, or invalid JSON
         */
        public JSONObject executeToJson() throws IOException {
            String responseBody = execute();

            try {
                return new JSONObject(responseBody);
            } catch (JSONException e) {
                throw new IOException("Failed to parse JSON response: " + e.getMessage(), e);
            }
        }

        /**
         * Create and configure HttpURLConnection
         *
         * @return Configured HttpURLConnection
         * @throws IOException If connection cannot be established
         */
        private HttpURLConnection createConnection() throws IOException {
            URL urlObject = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) urlObject.openConnection();

            // Configure connection
            conn.setRequestMethod(method);
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setUseCaches(false);
            conn.setDoInput(true);

            // Set headers
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                conn.setRequestProperty(entry.getKey(), entry.getValue());
            }

            // Configure request body
            if (requestBody != null && requestBody.length > 0) {
                conn.setDoOutput(true);
                if (contentType != null) {
                    conn.setRequestProperty("Content-Type", contentType);
                }
                conn.setFixedLengthStreamingMode(requestBody.length);

                // Write request body
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

        /**
         * Read entire input stream into String
         *
         * @param is Input stream to read
         * @param charset Character encoding
         * @return Stream content as String
         * @throws IOException If read error occurs
         */
        private String readStream(InputStream is, String charset) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int bytesRead;

            while ((bytesRead = is.read(chunk)) != -1) {
                buffer.write(chunk, 0, bytesRead);
            }

            return buffer.toString(charset);
        }

        /**
         * Extract meaningful error message from HTTP error response
         *
         * @param errorBody Raw error response body
         * @param responseCode HTTP response code
         * @return Human-readable error message
         */
        private String extractErrorMessage(String errorBody, int responseCode) {
            // Try to parse as JSON error
            try {
                JSONObject json = new JSONObject(errorBody);
                if (json.has("error")) {
                    JSONObject error = json.getJSONObject("error");
                    String message = error.optString("message", "");
                    if (!message.isEmpty()) {
                        return message;
                    }
                }
            } catch (JSONException ignored) {
                // Not JSON or invalid format - continue with fallback
            }

            // Fallback to HTTP status code description
            return getStatusDescription(responseCode);
        }

        /**
         * Get human-readable description for HTTP status code
         *
         * @param statusCode HTTP status code
         * @return Status description
         */
        private String getStatusDescription(int statusCode) {
            switch (statusCode) {
                case 400: return "Bad Request";
                case 401: return "Unauthorized - Check API key";
                case 403: return "Forbidden - Insufficient permissions";
                case 404: return "Not Found";
                case 429: return "Too Many Requests - Rate limit exceeded";
                case 500: return "Internal Server Error";
                case 502: return "Bad Gateway";
                case 503: return "Service Unavailable";
                case 504: return "Gateway Timeout";
                default: return "HTTP Error";
            }
        }

        /**
         * URL-encode form parameter key=value pair
         *
         * @param key Parameter name
         * @param value Parameter value
         * @return Encoded key=value string
         */
        private String encodeFormParam(String key, String value) {
            try {
                return java.net.URLEncoder.encode(key, charset) + "=" +
                       java.net.URLEncoder.encode(value, charset);
            } catch (Exception e) {
                throw new RuntimeException("Failed to encode form parameter", e);
            }
        }

        /**
         * Concatenate multiple byte arrays
         *
         * @param arrays Byte arrays to concatenate
         * @return Combined byte array
         */
        private byte[] concat(byte[]... arrays) {
            int totalLength = 0;
            for (byte[] array : arrays) {
                totalLength += array.length;
            }

            byte[] result = new byte[totalLength];
            int offset = 0;
            for (byte[] array : arrays) {
                System.arraycopy(array, 0, result, offset, array.length);
                offset += array.length;
            }

            return result;
        }
    }
}
