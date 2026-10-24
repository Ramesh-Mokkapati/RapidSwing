package com.rapidswing.rest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One item of an API resource when screens are built at runtime: its field values by JSON property name.
 * Records with an id are equal when their ids are equal, so a reloaded or saved record matches its row.
 */
public final class ApiRecord {

    private final String idField;
    private final Map<String, Object> values = new LinkedHashMap<>();

    public ApiRecord(String idField) {
        this.idField = Objects.requireNonNull(idField, "idField");
    }

    /** Copy constructor, used to edit a copy. */
    public ApiRecord(ApiRecord other) {
        this(other.idField);
        values.putAll(other.values);
    }

    public Object get(String field) {
        return values.get(field);
    }

    public ApiRecord set(String field, Object value) {
        if (value == null) {
            values.remove(field);
        } else {
            values.put(field, value);
        }
        return this;
    }

    public Object getId() {
        return values.get(idField);
    }

    public Map<String, Object> values() {
        return Collections.unmodifiableMap(values);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ApiRecord)) {
            return false;
        }
        Object id = getId();
        return id != null && id.equals(((ApiRecord) other).getId());
    }

    @Override
    public int hashCode() {
        Object id = getId();
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
