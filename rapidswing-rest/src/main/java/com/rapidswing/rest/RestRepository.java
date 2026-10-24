package com.rapidswing.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rapidswing.crud.CrudRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A {@link CrudRepository} for an API resource described by OpenAPI: lists with GET, creates with POST, updates
 * with PUT and deletes with DELETE, converting JSON to typed values ({@code Long}, {@code BigDecimal},
 * {@code LocalDate}, ...) according to the schema.
 */
public class RestRepository implements CrudRepository<ApiRecord> {

    private final RestClient client;
    private final ApiResource resource;

    public RestRepository(RestClient client, ApiResource resource) {
        this.client = Objects.requireNonNull(client, "client");
        this.resource = Objects.requireNonNull(resource, "resource");
    }

    @Override
    public List<ApiRecord> findAll() {
        List<ApiRecord> records = new ArrayList<>();
        for (JsonNode node : client.getArray(resource.getPath())) {
            records.add(toRecord(node));
        }
        return records;
    }

    @Override
    public ApiRecord save(ApiRecord record) {
        ObjectNode body = toJson(record);
        JsonNode response = record.getId() == null
                ? client.post(resource.getPath(), body)
                : client.put(resource.itemPathFor(record.getId()), body);
        return response.isObject() ? toRecord(response) : record;
    }

    @Override
    public void delete(ApiRecord record) {
        client.delete(resource.itemPathFor(record.getId()));
    }

    ApiRecord toRecord(JsonNode node) {
        ApiRecord record = new ApiRecord(resource.getIdField());
        for (ApiField field : resource.getFields()) {
            record.set(field.getName(), fromJson(node.get(field.getName()), field));
        }
        return record;
    }

    /** All non-null values; read-only fields except the id are left to the server. */
    ObjectNode toJson(ApiRecord record) {
        ObjectNode body = RestClient.JSON.createObjectNode();
        for (ApiField field : resource.getFields()) {
            Object value = record.get(field.getName());
            boolean id = field.getName().equals(resource.getIdField());
            if (value != null && (!field.isReadOnly() || id)) {
                body.set(field.getName(), RestClient.JSON.valueToTree(value));
            }
        }
        return body;
    }

    private static Object fromJson(JsonNode value, ApiField field) {
        if (value == null || value.isNull()) {
            return null;
        }
        switch (field.getType()) {
            case INTEGER:
                return value.isNumber() ? value.intValue() : Integer.valueOf(value.asText());
            case LONG:
                return value.isNumber() ? value.longValue() : Long.valueOf(value.asText());
            case DECIMAL:
                return value.isNumber() ? value.decimalValue() : new BigDecimal(value.asText());
            case BOOLEAN:
                return value.asBoolean();
            case DATE:
                try {
                    return LocalDate.parse(value.asText());
                } catch (DateTimeParseException e) {
                    return null;
                }
            default:
                return value.asText();
        }
    }
}
