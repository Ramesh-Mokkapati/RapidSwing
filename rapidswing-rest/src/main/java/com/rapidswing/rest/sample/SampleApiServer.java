package com.rapidswing.rest.sample;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rapidswing.rest.RestClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A tiny stand-in for a Spring Boot app with springdoc-openapi, for demos and tests: it serves an OpenAPI 3
 * document at {@code /v3/api-docs} and in-memory CRUD endpoints for customers, products (no delete) and orders
 * (read-only, as a Spring Data page), answering errors in Spring's JSON error format.
 * <p>
 * It listens on the loopback address only. It is not a server to deploy.
 */
public final class SampleApiServer implements AutoCloseable {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern ITEM = Pattern.compile("^/api/(customers|products)/(\\d+)$");

    private final HttpServer server;
    private final String spec;
    private final Map<String, Map<Long, ObjectNode>> stores = new LinkedHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    private SampleApiServer(HttpServer server) {
        this.server = server;
        this.spec = resource("shop-api.json").replace("${serverUrl}", getBaseUrl());
        stores.put("customers", new LinkedHashMap<>());
        stores.put("products", new LinkedHashMap<>());
        stores.put("orders", new LinkedHashMap<>());
        seed();
        server.createContext("/", this::handle);
        server.start();
    }

    /** Starts on the given port of the loopback address; 0 picks a free port. */
    public static SampleApiServer start(int port) {
        try {
            return new SampleApiServer(HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start the sample API on port " + port, e);
        }
    }

    public String getBaseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void seed() {
        customer("Asha Rao", "asha.rao@example.com", "Hyderabad", "12500.00", "2019-04-12", true, "GOLD");
        customer("Liam Carter", "liam.carter@example.com", "London", "980.50", "2021-11-03", true, "SILVER");
        customer("Mei Tanaka", "mei.tanaka@example.com", "Osaka", "0.00", "2018-01-22", false, "BRONZE");
        product("Keyboard", "49.90", 120);
        product("Monitor", "219.00", 35);
        product("Docking station", "159.00", 0);
        order("Asha Rao", "268.90", "2026-10-01");
        order("Liam Carter", "49.90", "2026-10-15");
    }

    private void customer(String name, String email, String city, String balance, String since, boolean active,
                          String tier) {
        ObjectNode customer = RestClient.JSON.createObjectNode().put("name", name).put("email", email)
                .put("city", city).put("balance", new BigDecimal(balance)).put("since", since).put("active", active)
                .put("tier", tier);
        customer.putObject("address").put("street", "Main Street 1").put("zip", "10001");
        insert("customers", customer);
    }

    private void product(String name, String price, int stock) {
        insert("products", RestClient.JSON.createObjectNode().put("name", name).put("price", new BigDecimal(price))
                .put("stock", stock));
    }

    private void order(String customer, String total, String placedOn) {
        insert("orders", RestClient.JSON.createObjectNode().put("customer", customer)
                .put("total", new BigDecimal(total)).put("placedOn", placedOn));
    }

    private ObjectNode insert(String store, ObjectNode item) {
        long id = nextId.getAndIncrement();
        item.put("id", id);
        stores.get(store).put(id, item);
        return item;
    }

    private synchronized void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        try {
            if ("/v3/api-docs".equals(path) && "GET".equals(method)) {
                send(exchange, 200, spec);
                return;
            }
            if ("/api/health".equals(path) && "GET".equals(method)) {
                send(exchange, 200, "{\"status\":\"UP\"}");
                return;
            }
            if ("/api/orders".equals(path) && "GET".equals(method)) {
                ObjectNode page = RestClient.JSON.createObjectNode();
                ArrayNode content = page.putArray("content");
                stores.get("orders").values().forEach(content::add);
                page.put("totalElements", content.size());
                send(exchange, 200, page.toString());
                return;
            }
            Matcher item = ITEM.matcher(path);
            if (path.equals("/api/customers") || path.equals("/api/products")) {
                collection(exchange, method, path.substring("/api/".length()));
            } else if (item.matches()) {
                item(exchange, method, item.group(1), Long.parseLong(item.group(2)));
            } else {
                error(exchange, 404, "Not Found", "No endpoint " + method + " " + path);
            }
        } catch (IOException e) {
            error(exchange, 400, "Bad Request", "Malformed JSON: " + e.getMessage());
        }
    }

    private void collection(HttpExchange exchange, String method, String store) throws IOException {
        if ("GET".equals(method)) {
            ArrayNode list = RestClient.JSON.createArrayNode();
            stores.get(store).values().forEach(list::add);
            send(exchange, 200, list.toString());
        } else if ("POST".equals(method)) {
            ObjectNode body = readBody(exchange);
            String problem = validate(store, body);
            if (problem != null) {
                error(exchange, 400, "Bad Request", problem);
                return;
            }
            send(exchange, 200, insert(store, body).toString());
        } else {
            error(exchange, 405, "Method Not Allowed", "Request method '" + method + "' is not supported");
        }
    }

    private void item(HttpExchange exchange, String method, String store, long id) throws IOException {
        ObjectNode existing = stores.get(store).get(id);
        boolean deleteAllowed = "customers".equals(store);
        if (!"PUT".equals(method) && !"GET".equals(method) && !("DELETE".equals(method) && deleteAllowed)) {
            error(exchange, 405, "Method Not Allowed", "Request method '" + method + "' is not supported");
        } else if (existing == null) {
            error(exchange, 404, "Not Found", store.substring(0, store.length() - 1) + " " + id + " not found");
        } else if ("GET".equals(method)) {
            send(exchange, 200, existing.toString());
        } else if ("DELETE".equals(method)) {
            stores.get(store).remove(id);
            send(exchange, 200, "");
        } else {
            ObjectNode body = readBody(exchange);
            String problem = validate(store, body);
            if (problem != null) {
                error(exchange, 400, "Bad Request", problem);
                return;
            }
            body.put("id", id);
            if (existing.has("address") && !body.has("address")) {
                body.set("address", existing.get("address"));
            }
            stores.get(store).put(id, body);
            send(exchange, 200, body.toString());
        }
    }

    /** Bean Validation-style checks, with Spring's message format. */
    private static String validate(String store, ObjectNode body) {
        List<String> required = "customers".equals(store) ? Arrays.asList("name", "email") : Arrays.asList("name", "price");
        for (String field : required) {
            JsonNode value = body.get(field);
            if (value == null || value.isNull() || value.asText().trim().isEmpty()) {
                return field + ": must not be blank";
            }
        }
        if (body.has("email") && !EMAIL.matcher(body.get("email").asText()).matches()) {
            return "email: must be a well-formed email address";
        }
        if (body.has("price") && body.get("price").decimalValue().signum() < 0) {
            return "price: must be greater than or equal to 0";
        }
        return null;
    }

    private static ObjectNode readBody(HttpExchange exchange) throws IOException {
        JsonNode body = RestClient.JSON.readTree(readAll(exchange.getRequestBody()));
        if (!(body instanceof ObjectNode)) {
            throw new IOException("Expected a JSON object");
        }
        ObjectNode object = (ObjectNode) body;
        object.remove("id");
        return object;
    }

    private static void error(HttpExchange exchange, int status, String error, String message) throws IOException {
        ObjectNode body = RestClient.JSON.createObjectNode()
                .put("timestamp", Instant.now().toString())
                .put("status", status).put("error", error).put("message", message)
                .put("path", exchange.getRequestURI().getPath());
        send(exchange, status, body.toString());
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String resource(String name) {
        try (InputStream in = SampleApiServer.class.getResourceAsStream(name)) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Runs the sample API until the process is stopped, e.g. to try the generator against it. */
    public static void main(String[] args) {
        SampleApiServer api = start(args.length > 0 ? Integer.parseInt(args[0]) : 8080);
        System.out.println("Sample API running at " + api.getBaseUrl() + " (OpenAPI document at /v3/api-docs)");
    }
}
