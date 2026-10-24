package com.rapidswing.rest;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A small JSON-over-HTTP client for Spring Boot style REST APIs, using only the JDK (Java 8 has no HttpClient)
 * and Jackson. Calls block, so run them in background tasks.
 * <pre>
 * RestClient api = new RestClient("https://shop.example.com").withBearerToken(token);
 * List&lt;Customer&gt; customers = api.getList("/api/customers", Customer.class);
 * </pre>
 * Lists also work when the server returns a Spring Data page ({@code {"content": [...], ...}}). Error responses
 * become a {@link RestException} with the server's message.
 */
public class RestClient {

    /**
     * Configured for typical Spring Boot JSON: ISO dates, unknown properties ignored, exact decimals. JSON trees
     * keep decimals as sent: Jackson's default would turn {@code 12500.00} into {@code 1.25E+4}.
     */
    public static final ObjectMapper JSON = new ObjectMapper()
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true))
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private final String baseUrl;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private int connectTimeoutMillis = 10_000;
    private int readTimeoutMillis = 30_000;

    public RestClient(String baseUrl) {
        String url = Objects.requireNonNull(baseUrl, "baseUrl").trim();
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        headers.put("Accept", "application/json");
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public RestClient withBearerToken(String token) {
        return withHeader("Authorization", "Bearer " + token);
    }

    /** HTTP Basic authentication. Use it only over HTTPS: the credentials are merely encoded, not encrypted. */
    public RestClient withBasicAuth(String username, char[] password) {
        byte[] credentials = (username + ":" + new String(password)).getBytes(StandardCharsets.UTF_8);
        return withHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials));
    }

    public RestClient withHeader(String name, String value) {
        headers.put(name, value);
        return this;
    }

    public RestClient withTimeouts(int connectMillis, int readMillis) {
        this.connectTimeoutMillis = connectMillis;
        this.readTimeoutMillis = readMillis;
        return this;
    }

    // ---- Untyped (JSON trees) ------------------------------------------------------------------------

    public JsonNode get(String path) {
        return send("GET", path, null);
    }

    public JsonNode post(String path, Object body) {
        return send("POST", path, body);
    }

    public JsonNode put(String path, Object body) {
        return send("PUT", path, body);
    }

    public void delete(String path) {
        send("DELETE", path, null);
    }

    /** The elements of a JSON array response, or of the {@code content} array of a Spring Data page. */
    public List<JsonNode> getArray(String path) {
        JsonNode response = get(path);
        JsonNode array = response.isArray() ? response : response.path("content");
        if (!array.isArray()) {
            throw new RestException(200, "Expected a JSON array from " + path);
        }
        List<JsonNode> elements = new ArrayList<>();
        ((ArrayNode) array).forEach(elements::add);
        return elements;
    }

    // ---- Typed (used by generated code) --------------------------------------------------------------

    public <T> T get(String path, Class<T> type) {
        return JSON.convertValue(get(path), type);
    }

    public <T> List<T> getList(String path, Class<T> type) {
        List<T> items = new ArrayList<>();
        for (JsonNode element : getArray(path)) {
            items.add(JSON.convertValue(element, type));
        }
        return items;
    }

    public <T> T post(String path, Object body, Class<T> type) {
        return JSON.convertValue(post(path, body), type);
    }

    public <T> T put(String path, Object body, Class<T> type) {
        return JSON.convertValue(put(path, body), type);
    }

    /** Fills the first {@code {...}} placeholder of a path template with the URL-encoded value. */
    public static String expand(String template, Object value) {
        int start = template.indexOf('{');
        int end = template.indexOf('}', start);
        if (start < 0 || end < 0) {
            throw new IllegalArgumentException("No {placeholder} in " + template);
        }
        try {
            return template.substring(0, start) + URLEncoder.encode(String.valueOf(value), "UTF-8")
                    + template.substring(end + 1);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- HTTP ----------------------------------------------------------------------------------------

    private JsonNode send(String method, String path, Object body) {
        String url = path.startsWith("http://") || path.startsWith("https://") ? path
                : baseUrl + (path.startsWith("/") ? path : "/" + path);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(connectTimeoutMillis);
            connection.setReadTimeout(readTimeoutMillis);
            for (Map.Entry<String, String> header : headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            if (body != null) {
                byte[] json = JSON.writeValueAsBytes(body);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(json);
                }
            }
            int status = connection.getResponseCode();
            byte[] response = readAll(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            if (status >= 400) {
                throw new RestException(status, errorMessage(status, method, url, response));
            }
            return response.length == 0 ? JSON.nullNode() : JSON.readTree(response);
        } catch (IOException e) {
            throw new UncheckedIOException(method + " " + url + " failed: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** Spring's error body ({@code message}/{@code error}) or a problem detail ({@code detail}/{@code title}). */
    private static String errorMessage(int status, String method, String url, byte[] body) {
        try {
            JsonNode error = JSON.readTree(body);
            for (String field : Arrays.asList("message", "detail", "error", "title")) {
                String text = error.path(field).asText("");
                if (!text.isEmpty()) {
                    return text;
                }
            }
        } catch (IOException ignored) {
            // Not JSON: fall back to the status.
        }
        return method + " " + url + " returned HTTP " + status;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        if (in == null) {
            return new byte[0];
        }
        try (InputStream input = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }
}
