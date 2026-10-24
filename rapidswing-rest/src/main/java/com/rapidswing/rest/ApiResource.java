package com.rapidswing.rest;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A CRUD resource found in an OpenAPI document: a collection path such as {@code /api/customers} and, usually,
 * an item path such as {@code /api/customers/{id}}, with the operations the API offers on them.
 */
public final class ApiResource {

    private final String name;
    private final String path;
    private final String itemPath;
    private final String idParameter;
    private final String schemaName;
    private final List<ApiField> fields;
    private final boolean canCreate;
    private final boolean canUpdate;
    private final boolean canDelete;

    ApiResource(String name, String path, String itemPath, String idParameter, String schemaName,
                List<ApiField> fields, boolean canCreate, boolean canUpdate, boolean canDelete) {
        this.name = name;
        this.path = path;
        this.itemPath = itemPath;
        this.idParameter = idParameter;
        this.schemaName = schemaName;
        this.fields = Collections.unmodifiableList(fields);
        this.canCreate = canCreate;
        this.canUpdate = canUpdate;
        this.canDelete = canDelete;
    }

    /** The last path segment, e.g. {@code customers}. */
    public String getName() {
        return name;
    }

    /** E.g. "Customers". */
    public String getTitle() {
        return capitalize(name.replace('-', ' ').replace('_', ' '));
    }

    /** E.g. "Customer": the schema name without a DTO suffix, or the singular of the path segment. */
    public String getSingularTitle() {
        if (schemaName != null) {
            String base = schemaName.replaceAll("(?i)(dto|resource|model)$", "");
            return base.isEmpty() ? schemaName : base;
        }
        return capitalize(singular(name));
    }

    public String getPath() {
        return path;
    }

    /** E.g. {@code /api/customers/{id}}, or {@code null} if the API has no item operations. */
    public String getItemPath() {
        return itemPath;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public List<ApiField> getFields() {
        return fields;
    }

    /** The field holding the item's id: the item path's parameter name if it is a field, else {@code id}. */
    public String getIdField() {
        for (ApiField field : fields) {
            if (field.getName().equals(idParameter)) {
                return idParameter;
            }
        }
        return "id";
    }

    public boolean canCreate() {
        return canCreate;
    }

    public boolean canUpdate() {
        return canUpdate && itemPath != null;
    }

    public boolean canDelete() {
        return canDelete && itemPath != null;
    }

    /** The URL path of one item, with the id URL-encoded. */
    public String itemPathFor(Object id) {
        if (itemPath == null) {
            throw new IllegalStateException(getTitle() + " has no item path");
        }
        try {
            return itemPath.replace("{" + idParameter + "}", URLEncoder.encode(String.valueOf(id), "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    static String singular(String plural) {
        if (plural.endsWith("ies")) {
            return plural.substring(0, plural.length() - 3) + "y";
        }
        if (plural.endsWith("sses") || plural.endsWith("xes") || plural.endsWith("ches") || plural.endsWith("shes")) {
            return plural.substring(0, plural.length() - 2);
        }
        if (plural.endsWith("s") && !plural.endsWith("ss")) {
            return plural.substring(0, plural.length() - 1);
        }
        return plural;
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    @Override
    public String toString() {
        return getTitle() + " " + path + " " + fields;
    }
}
