package com.rapidswing.rest;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An OpenAPI 3 document, such as the one springdoc-openapi serves for a Spring Boot app at {@code /v3/api-docs},
 * reduced to the CRUD resources a GUI can offer.
 * <p>
 * A resource is a collection path with a GET returning a list of objects (e.g. {@code /api/customers}), plus
 * optionally POST on it and GET/PUT/DELETE on an item path ({@code /api/customers/{id}}). Its fields come from
 * the object schema, including {@code required}, {@code readOnly}, length and range limits, {@code format: email}
 * and enums. Nested objects and arrays are left out. Swagger 2 documents are not supported.
 */
public final class OpenApiSpec {

    /** Where springdoc-openapi serves the document by default. */
    public static final String SPRINGDOC_PATH = "/v3/api-docs";

    private final JsonNode root;
    private final List<ApiResource> resources;

    private OpenApiSpec(JsonNode root) {
        if (root.has("swagger")) {
            throw new IllegalArgumentException("Swagger 2 documents are not supported: use springdoc-openapi, "
                    + "which serves OpenAPI 3 at " + SPRINGDOC_PATH);
        }
        if (!root.path("openapi").asText("").startsWith("3.")) {
            throw new IllegalArgumentException("Not an OpenAPI 3 document");
        }
        this.root = root;
        this.resources = Collections.unmodifiableList(findResources());
    }

    public static OpenApiSpec parse(String json) {
        try {
            return parse(RestClient.JSON.readTree(json));
        } catch (IOException e) {
            throw new IllegalArgumentException("Not valid JSON: " + e.getMessage(), e);
        }
    }

    public static OpenApiSpec parse(JsonNode document) {
        return new OpenApiSpec(document);
    }

    public static OpenApiSpec load(Path file) throws IOException {
        return parse(RestClient.JSON.readTree(Files.readAllBytes(file)));
    }

    /** Downloads the document from a running Spring Boot app with springdoc-openapi. Blocks. */
    public static OpenApiSpec fetch(RestClient client) {
        return parse(client.get(SPRINGDOC_PATH));
    }

    public String getTitle() {
        return root.path("info").path("title").asText("API");
    }

    public String getVersion() {
        return root.path("info").path("version").asText("");
    }

    /** The first server URL in the document, or {@code null}. */
    public String getServerUrl() {
        JsonNode servers = root.path("servers");
        return servers.size() > 0 ? servers.get(0).path("url").asText(null) : null;
    }

    public List<ApiResource> getResources() {
        return resources;
    }

    public ApiResource resource(String name) {
        for (ApiResource resource : resources) {
            if (resource.getName().equals(name)) {
                return resource;
            }
        }
        throw new IllegalArgumentException("No resource '" + name + "' in " + getTitle());
    }

    // ---- Resource detection --------------------------------------------------------------------------

    private List<ApiResource> findResources() {
        JsonNode paths = root.path("paths");
        List<ApiResource> found = new ArrayList<>();
        for (Iterator<String> names = paths.fieldNames(); names.hasNext(); ) {
            String path = names.next();
            if (path.contains("{")) {
                continue;
            }
            JsonNode collection = paths.get(path);
            if (!collection.has("get")) {
                continue;
            }
            String itemPath = null;
            String idParameter = null;
            String prefix = (path.endsWith("/") ? path : path + "/") + "{";
            for (Iterator<String> others = paths.fieldNames(); others.hasNext(); ) {
                String candidate = others.next();
                if (candidate.startsWith(prefix) && candidate.endsWith("}")
                        && candidate.indexOf('/', prefix.length()) < 0) {
                    itemPath = candidate;
                    idParameter = candidate.substring(prefix.length(), candidate.length() - 1);
                }
            }
            JsonNode item = itemPath != null ? paths.get(itemPath) : null;
            String schemaRef = schemaRef(collection, item);
            JsonNode schema = schemaRef != null ? resolve(schemaRef) : listItemSchema(collection.path("get"));
            if (schema == null || !schema.has("properties")) {
                continue;
            }
            List<ApiField> fields = fields(schema, idParameter);
            if (fields.isEmpty()) {
                continue;
            }
            String name = path.substring(path.lastIndexOf('/', path.length() - 2) + 1).replace("/", "");
            found.add(new ApiResource(name, path, itemPath, idParameter,
                    schemaRef != null ? schemaRef.substring(schemaRef.lastIndexOf('/') + 1) : null, fields,
                    collection.has("post"), item != null && item.has("put"), item != null && item.has("delete")));
        }
        return found;
    }

    /** The schema reference of the resource: from the create body, the list response or the item response. */
    private String schemaRef(JsonNode collection, JsonNode item) {
        String ref = ref(content(collection.path("post").path("requestBody")));
        if (ref == null) {
            JsonNode listSchema = content(collection.path("get").path("responses").path("200"));
            ref = ref(arrayItems(listSchema));
        }
        if (ref == null && item != null) {
            ref = ref(content(item.path("get").path("responses").path("200")));
        }
        return ref;
    }

    private JsonNode listItemSchema(JsonNode get) {
        JsonNode items = arrayItems(content(get.path("responses").path("200")));
        return items != null && items.has("properties") ? items : null;
    }

    /** The items of an array schema, or of the {@code content} array of a Spring Data page schema. */
    private JsonNode arrayItems(JsonNode schema) {
        if (schema == null) {
            return null;
        }
        JsonNode resolved = schema.has("$ref") ? resolve(schema.get("$ref").asText()) : schema;
        if (resolved == null) {
            return null;
        }
        if ("array".equals(resolved.path("type").asText())) {
            return resolved.path("items");
        }
        JsonNode content = resolved.path("properties").path("content");
        return "array".equals(content.path("type").asText()) ? content.path("items") : null;
    }

    /** The schema of the first media type of a request body or response. */
    private static JsonNode content(JsonNode bodyOrResponse) {
        JsonNode content = bodyOrResponse.path("content");
        Iterator<JsonNode> mediaTypes = content.elements();
        return mediaTypes.hasNext() ? mediaTypes.next().path("schema") : null;
    }

    private static String ref(JsonNode schema) {
        return schema != null && schema.has("$ref") ? schema.get("$ref").asText() : null;
    }

    private JsonNode resolve(String ref) {
        if (!ref.startsWith("#/")) {
            return null;
        }
        JsonNode node = root;
        for (String part : ref.substring(2).split("/")) {
            node = node.path(part.replace("~1", "/").replace("~0", "~"));
        }
        return node.isMissingNode() ? null : node;
    }

    // ---- Fields --------------------------------------------------------------------------------------

    private List<ApiField> fields(JsonNode schema, String idParameter) {
        Map<String, JsonNode> properties = new LinkedHashMap<>();
        Set<String> required = new HashSet<>();
        collectProperties(schema, properties, required, 0);
        List<ApiField> fields = new ArrayList<>();
        for (Map.Entry<String, JsonNode> property : properties.entrySet()) {
            String name = property.getKey();
            JsonNode definition = property.getValue();
            JsonNode resolved = definition.has("$ref") ? resolve(definition.get("$ref").asText()) : definition;
            if (resolved == null && definition.has("allOf") && definition.get("allOf").size() == 1) {
                resolved = resolve(definition.get("allOf").get(0).path("$ref").asText(""));
            }
            if (resolved == null) {
                continue;
            }
            ApiField.Type type = type(resolved);
            if (type == null) {
                continue;
            }
            boolean readOnly = definition.path("readOnly").asBoolean(false)
                    || name.equals(idParameter) || ("id".equals(name) && idParameter != null);
            List<String> enumValues = new ArrayList<>();
            resolved.path("enum").forEach(value -> enumValues.add(value.asText()));
            fields.add(new ApiField(name, type, required.contains(name), readOnly,
                    intOrNull(resolved.get("minLength")), intOrNull(resolved.get("maxLength")),
                    decimalOrNull(resolved.get("minimum")), decimalOrNull(resolved.get("maximum")),
                    resolved.path("pattern").asText(null), "email".equals(resolved.path("format").asText()),
                    enumValues));
        }
        return fields;
    }

    /** Properties and required names of an object schema, following {@code allOf} composition. */
    private void collectProperties(JsonNode schema, Map<String, JsonNode> properties, Set<String> required,
                                   int depth) {
        if (schema == null || depth > 5) {
            return;
        }
        JsonNode resolved = schema.has("$ref") ? resolve(schema.get("$ref").asText()) : schema;
        if (resolved == null) {
            return;
        }
        for (JsonNode part : resolved.path("allOf")) {
            collectProperties(part, properties, required, depth + 1);
        }
        for (Iterator<Map.Entry<String, JsonNode>> fields = resolved.path("properties").fields(); fields.hasNext(); ) {
            Map.Entry<String, JsonNode> field = fields.next();
            properties.put(field.getKey(), field.getValue());
        }
        resolved.path("required").forEach(name -> required.add(name.asText()));
    }

    private static ApiField.Type type(JsonNode schema) {
        String format = schema.path("format").asText("");
        switch (schema.path("type").asText("")) {
            case "string":
                if (schema.has("enum")) {
                    return ApiField.Type.ENUM;
                }
                return "date".equals(format) ? ApiField.Type.DATE
                        : "date-time".equals(format) ? ApiField.Type.DATE_TIME : ApiField.Type.STRING;
            case "integer":
                return "int64".equals(format) ? ApiField.Type.LONG : ApiField.Type.INTEGER;
            case "number":
                return ApiField.Type.DECIMAL;
            case "boolean":
                return ApiField.Type.BOOLEAN;
            default:
                return null;
        }
    }

    private static Integer intOrNull(JsonNode node) {
        return node != null && node.isNumber() ? node.asInt() : null;
    }

    private static BigDecimal decimalOrNull(JsonNode node) {
        return node != null && node.isNumber() ? node.decimalValue() : null;
    }
}
