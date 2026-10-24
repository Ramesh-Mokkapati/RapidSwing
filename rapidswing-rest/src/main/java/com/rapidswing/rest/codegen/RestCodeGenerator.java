package com.rapidswing.rest.codegen;

import com.rapidswing.rest.ApiField;
import com.rapidswing.rest.ApiResource;
import com.rapidswing.rest.OpenApiSpec;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Generates Java source for a REST API's resources, to own and customize, instead of building screens at
 * runtime. Per resource (here {@code customers} with schema {@code Customer}):
 * <ul>
 *   <li>{@code model/Customer.java}: the data class, with a copy constructor and equality by id</li>
 *   <li>{@code api/CustomerRepository.java}: a {@code CrudRepository} calling the API with {@code RestClient}</li>
 *   <li>{@code ui/CustomerScreen.java}: the {@code CrudScreen} definition: columns, form and validation</li>
 * </ul>
 * plus {@code ui/ApiViews.java}, which registers all screens as views.
 * <pre>
 * Map&lt;String, String&gt; files = new RestCodeGenerator(spec, "com.acme.shop").generate();
 * // keys are paths below src/main/java, e.g. "com/acme/shop/model/Customer.java"
 * </pre>
 */
public final class RestCodeGenerator {

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
            "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null"));

    private final OpenApiSpec spec;
    private final String basePackage;

    public RestCodeGenerator(OpenApiSpec spec, String basePackage) {
        if (!basePackage.matches("[a-z_][a-z0-9_]*(\\.[a-z_][a-z0-9_]*)*")) {
            throw new IllegalArgumentException("Not a valid Java package name: " + basePackage);
        }
        this.spec = spec;
        this.basePackage = basePackage;
    }

    /** Source files by path relative to {@code src/main/java}. */
    public Map<String, String> generate() {
        Map<String, String> files = new LinkedHashMap<>();
        String folder = basePackage.replace('.', '/') + "/";
        for (ApiResource resource : spec.getResources()) {
            String type = typeName(resource);
            files.put(folder + "model/" + type + ".java", model(resource, type));
            files.put(folder + "api/" + type + "Repository.java", repository(resource, type));
            files.put(folder + "ui/" + type + "Screen.java", screen(resource, type));
        }
        files.put(folder + "ui/ApiViews.java", views());
        return files;
    }

    /** The view ids and titles of the generated screens, in API order, e.g. {@code customers -> Customers}. */
    public Map<String, String> viewTitles() {
        Map<String, String> titles = new LinkedHashMap<>();
        for (ApiResource resource : spec.getResources()) {
            titles.put(resource.getName(), resource.getTitle());
        }
        return titles;
    }

    // ---- Model ---------------------------------------------------------------------------------------

    private String model(ApiResource resource, String type) {
        Set<String> imports = new TreeSet<>();
        imports.add("java.util.Objects");
        StringBuilder fields = new StringBuilder();
        StringBuilder copies = new StringBuilder();
        StringBuilder accessors = new StringBuilder();
        String idField = javaName(resource.getIdField());
        boolean hasId = false;
        for (ApiField field : resource.getFields()) {
            String name = javaName(field.getName());
            String javaType = javaType(field, imports);
            hasId |= name.equals(idField);
            if (!name.equals(field.getName())) {
                imports.add("com.fasterxml.jackson.annotation.JsonProperty");
                fields.append("    @JsonProperty(\"").append(escape(field.getName())).append("\")\n");
            }
            fields.append("    private ").append(javaType).append(' ').append(name).append(";\n");
            copies.append("        this.").append(name).append(" = other.").append(name).append(";\n");
            accessors.append("\n    public ").append(javaType).append(' ').append(getter(field)).append("() {\n")
                    .append("        return ").append(name).append(";\n    }\n")
                    .append("\n    public void ").append(setter(field)).append('(').append(javaType).append(' ')
                    .append(name).append(") {\n        this.").append(name).append(" = ").append(name)
                    .append(";\n    }\n");
        }
        String identity = hasId ? idField : null;
        StringBuilder out = new StringBuilder();
        out.append("package ").append(basePackage).append(".model;\n\n");
        imports.forEach(name -> out.append("import ").append(name).append(";\n"));
        out.append("\n/** ").append(resource.getSingularTitle()).append(", generated from ").append(escapeComment(spec.getTitle()))
                .append(" (").append(resource.getPath()).append("). */\n");
        out.append("public class ").append(type).append(" {\n\n").append(fields);
        out.append("\n    public ").append(type).append("() {\n    }\n");
        out.append("\n    /** Copy constructor, used to edit a copy until it is saved. */\n");
        out.append("    public ").append(type).append('(').append(type).append(" other) {\n").append(copies).append("    }\n");
        out.append(accessors);
        if (identity != null) {
            out.append("\n    /** Equal by id, so a reloaded or saved copy matches its row. */\n");
            out.append("    @Override\n    public boolean equals(Object other) {\n");
            out.append("        return this == other || (other instanceof ").append(type).append(" && ").append(identity)
                    .append(" != null\n                && Objects.equals(").append(identity).append(", ((").append(type)
                    .append(") other).").append(identity).append("));\n    }\n");
            out.append("\n    @Override\n    public int hashCode() {\n        return ").append(identity)
                    .append(" != null ? ").append(identity).append(".hashCode() : System.identityHashCode(this);\n    }\n");
        }
        out.append("\n    @Override\n    public String toString() {\n        return ").append(displayExpression(resource, "this"))
                .append(";\n    }\n}\n");
        return out.toString();
    }

    // ---- Repository ----------------------------------------------------------------------------------

    private String repository(ApiResource resource, String type) {
        String getId = getterFor(resource, resource.getIdField());
        StringBuilder out = new StringBuilder();
        out.append("package ").append(basePackage).append(".api;\n\n");
        out.append("import com.rapidswing.crud.CrudRepository;\n");
        out.append("import com.rapidswing.rest.RestClient;\n");
        out.append("import ").append(basePackage).append(".model.").append(type).append(";\n\n");
        out.append("import java.util.List;\n\n");
        out.append("/** ").append(resource.getTitle()).append(" from ").append(escapeComment(spec.getTitle()))
                .append(". Methods block: CrudScreen calls them in background tasks. */\n");
        out.append("public class ").append(type).append("Repository implements CrudRepository<").append(type).append("> {\n\n");
        out.append("    public static final String PATH = \"").append(escape(resource.getPath())).append("\";\n");
        if (resource.getItemPath() != null) {
            out.append("    public static final String ITEM_PATH = \"").append(escape(resource.getItemPath())).append("\";\n");
        }
        out.append("\n    private final RestClient client;\n\n");
        out.append("    public ").append(type).append("Repository(RestClient client) {\n        this.client = client;\n    }\n\n");
        out.append("    @Override\n    public List<").append(type).append("> findAll() {\n");
        out.append("        return client.getList(PATH, ").append(type).append(".class);\n    }\n\n");
        out.append("    @Override\n    public ").append(type).append(" save(").append(type).append(" item) {\n");
        if (getId == null) {
            out.append(resource.canCreate()
                    ? "        return client.post(PATH, item, " + type + ".class);\n"
                    : "        throw new UnsupportedOperationException(\"The API does not allow saving " + resource.getTitle() + "\");\n");
        } else {
            out.append("        if (item.").append(getId).append("() == null) {\n");
            out.append(resource.canCreate()
                    ? "            return client.post(PATH, item, " + type + ".class);\n"
                    : "            throw new UnsupportedOperationException(\"The API does not allow creating " + resource.getTitle() + "\");\n");
            out.append("        }\n");
            out.append(resource.canUpdate()
                    ? "        return client.put(RestClient.expand(ITEM_PATH, item." + getId + "()), item, " + type + ".class);\n"
                    : "        throw new UnsupportedOperationException(\"The API does not allow updating " + resource.getTitle() + "\");\n");
        }
        out.append("    }\n\n");
        out.append("    @Override\n    public void delete(").append(type).append(" item) {\n");
        out.append(resource.canDelete() && getId != null
                ? "        client.delete(RestClient.expand(ITEM_PATH, item." + getId + "()));\n"
                : "        throw new UnsupportedOperationException(\"The API does not allow deleting " + resource.getTitle() + "\");\n");
        out.append("    }\n}\n");
        return out.toString();
    }

    // ---- Screen --------------------------------------------------------------------------------------

    private String screen(ApiResource resource, String type) {
        Set<String> imports = new TreeSet<>(Arrays.asList("com.rapidswing.app.AppContext", "com.rapidswing.crud.CrudScreen",
                "com.rapidswing.rest.RestClient", basePackage + ".api." + type + "Repository", basePackage + ".model." + type));
        StringBuilder columns = new StringBuilder();
        StringBuilder form = new StringBuilder();
        boolean dates = false;
        for (ApiField field : resource.getFields()) {
            String javaType = javaType(field, new HashSet<>());
            columns.append("\n                        .column(\"").append(escape(field.getLabel())).append("\", ")
                    .append(javaType).append(".class, ").append(type).append("::").append(getter(field)).append(')');
            if (field.getType() == ApiField.Type.DATE) {
                columns.append(".format(dates::format)");
                dates = true;
            }
            if (!field.isReadOnly()) {
                form.append(formField(field, type, imports));
            }
            addTypeImport(field, imports);
        }
        if (dates) {
            imports.add("java.time.format.DateTimeFormatter");
            imports.add("java.time.format.FormatStyle");
        }
        StringBuilder out = new StringBuilder();
        out.append("package ").append(basePackage).append(".ui;\n\n");
        imports.forEach(name -> out.append("import ").append(name).append(";\n"));
        out.append("\n/** The ").append(resource.getTitle()).append(" screen: list, form and validation. Customize freely. */\n");
        out.append("public final class ").append(type).append("Screen {\n\n");
        out.append("    private ").append(type).append("Screen() {\n    }\n\n");
        out.append("    public static CrudScreen<").append(type).append("> create(AppContext ctx, RestClient client) {\n");
        if (dates) {
            out.append("        DateTimeFormatter dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)\n")
                    .append("                .withLocale(ctx.i18n().getLocale());\n");
        }
        out.append("        return CrudScreen.builder(").append(type).append(".class)\n");
        out.append("                .context(ctx)\n");
        out.append("                .names(\"").append(escape(resource.getSingularTitle())).append("\", \"")
                .append(escape(resource.getTitle())).append("\")\n");
        out.append("                .repository(new ").append(type).append("Repository(client))\n");
        out.append("                .newItem(").append(type).append("::new)\n");
        out.append("                .copy(").append(type).append("::new)\n");
        out.append("                .itemName(item -> ").append(displayExpression(resource, "item")).append(")\n");
        out.append("                .allowCreate(").append(resource.canCreate()).append(")\n");
        out.append("                .allowEdit(").append(resource.canUpdate()).append(")\n");
        out.append("                .allowDelete(").append(resource.canDelete()).append(")\n");
        out.append("                .columns(table -> table").append(columns).append(")\n");
        out.append("                .form((form, edit) -> form").append(form).append(")\n");
        out.append("                .build();\n    }\n}\n");
        return out.toString();
    }

    private String formField(ApiField field, String type, Set<String> imports) {
        String label = escape(field.getLabel());
        String property = "edit.property(" + type + "::" + getter(field) + ", " + type + "::" + setter(field) + ")";
        StringBuilder out = new StringBuilder("\n                        ");
        switch (field.getType()) {
            case INTEGER:
                out.append(".integer(\"").append(label).append("\", ").append(property).append(')');
                break;
            case LONG:
                imports.add("com.rapidswing.binding.Converters");
                out.append(".text(\"").append(label).append("\", ").append(property).append(", Converters.longs())");
                break;
            case DECIMAL:
                out.append(".decimal(\"").append(label).append("\", ").append(property).append(')');
                break;
            case BOOLEAN:
                out.append(".checkbox(\"").append(label).append("\", ").append(property).append(')');
                return out.toString();
            case DATE:
                out.append(".date(\"").append(label).append("\", ").append(property).append(')');
                break;
            case ENUM:
                out.append(".combo(\"").append(label).append("\", ").append(property);
                for (String value : field.getEnumValues()) {
                    out.append(", \"").append(escape(value)).append('"');
                }
                out.append(')');
                break;
            default:
                out.append(".text(\"").append(label).append("\", ").append(property).append(')');
        }
        if (field.isRequired()) {
            out.append(".required()");
        }
        List<String> rules = new ArrayList<>();
        if (field.getType() == ApiField.Type.STRING) {
            if (field.getMinLength() != null && field.getMinLength() > 0) {
                rules.add("Validators.minLength(" + field.getMinLength() + ")");
            }
            if (field.getMaxLength() != null) {
                rules.add("Validators.maxLength(" + field.getMaxLength() + ")");
            }
            if (field.isEmail()) {
                rules.add("Validators.email()");
            }
            if (field.getPattern() != null) {
                rules.add("Validators.pattern(\"" + escape(field.getPattern()) + "\", \"Must match "
                        + escape(field.getPattern()) + "\")");
            }
        }
        String range = range(field);
        if (range != null) {
            rules.add(range);
        }
        if (field.getType() == ApiField.Type.DECIMAL && (field.getMinimum() != null || field.getMaximum() != null)) {
            imports.add("java.math.BigDecimal");
        }
        for (String rule : rules) {
            imports.add("com.rapidswing.forms.Validators");
            out.append("\n                            .validate(").append(rule).append(')');
        }
        return out.toString();
    }

    private static String range(ApiField field) {
        BigDecimal min = field.getMinimum();
        BigDecimal max = field.getMaximum();
        if (min == null && max == null) {
            return null;
        }
        String low = min != null ? literal(field, min) : null;
        String high = max != null ? literal(field, max) : null;
        if (low == null || high == null) {
            return low != null ? "Validators.min(" + low + ")" : "Validators.max(" + high + ")";
        }
        return "Validators.range(" + low + ", " + high + ")";
    }

    private static String literal(ApiField field, BigDecimal value) {
        switch (field.getType()) {
            case INTEGER:
                return String.valueOf(value.intValue());
            case LONG:
                return value.longValue() + "L";
            case DECIMAL:
                return "new BigDecimal(\"" + value.toPlainString() + "\")";
            default:
                return null;
        }
    }

    // ---- Views ---------------------------------------------------------------------------------------

    private String views() {
        StringBuilder out = new StringBuilder();
        out.append("package ").append(basePackage).append(".ui;\n\n");
        out.append("import com.rapidswing.app.AppContext;\n");
        out.append("import com.rapidswing.rest.RestClient;\n\n");
        out.append("import java.util.LinkedHashMap;\nimport java.util.Map;\n\n");
        out.append("/** Registers a view per API resource of ").append(escapeComment(spec.getTitle())).append(". */\n");
        out.append("public final class ApiViews {\n\n");
        out.append("    private ApiViews() {\n    }\n\n");
        out.append("    /** View ids and titles, in API order, e.g. for menus. */\n");
        out.append("    public static Map<String, String> titles() {\n");
        out.append("        Map<String, String> titles = new LinkedHashMap<>();\n");
        for (ApiResource resource : spec.getResources()) {
            out.append("        titles.put(\"").append(escape(resource.getName())).append("\", \"")
                    .append(escape(resource.getTitle())).append("\");\n");
        }
        out.append("        return titles;\n    }\n\n");
        out.append("    public static void register(AppContext ctx, RestClient client) {\n");
        for (ApiResource resource : spec.getResources()) {
            out.append("        ctx.views().register(\"").append(escape(resource.getName())).append("\", () -> ")
                    .append(typeName(resource)).append("Screen.create(ctx, client));\n");
        }
        out.append("    }\n}\n");
        return out.toString();
    }

    // ---- Naming --------------------------------------------------------------------------------------

    static String typeName(ApiResource resource) {
        StringBuilder name = new StringBuilder();
        for (String word : resource.getSingularTitle().split("[^A-Za-z0-9]+")) {
            if (!word.isEmpty()) {
                name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        if (name.length() == 0 || !Character.isJavaIdentifierStart(name.charAt(0))) {
            name.insert(0, "Item");
        }
        return name.toString();
    }

    /** A valid Java field name: {@code first-name} becomes {@code firstName}, {@code class} becomes {@code classValue}. */
    static String javaName(String jsonName) {
        StringBuilder name = new StringBuilder();
        boolean upper = false;
        for (char c : jsonName.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '_') {
                name.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            } else {
                upper = name.length() > 0;
            }
        }
        if (name.length() == 0 || Character.isDigit(name.charAt(0))) {
            name.insert(0, "field");
        }
        String result = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        return KEYWORDS.contains(result) ? result + "Value" : result;
    }

    private static String getter(ApiField field) {
        return "get" + capitalize(javaName(field.getName()));
    }

    private static String setter(ApiField field) {
        return "set" + capitalize(javaName(field.getName()));
    }

    private static String getterFor(ApiResource resource, String fieldName) {
        for (ApiField field : resource.getFields()) {
            if (field.getName().equals(fieldName)) {
                return getter(field);
            }
        }
        return null;
    }

    /** The first required text field (e.g. the name), else the id, for titles and confirmations. */
    private static String displayExpression(ApiResource resource, String target) {
        for (ApiField field : resource.getFields()) {
            if (field.getType() == ApiField.Type.STRING && field.isRequired()) {
                return "String.valueOf(" + target + "." + getter(field) + "())";
            }
        }
        String id = getterFor(resource, resource.getIdField());
        return id != null ? "\"" + escape(resource.getSingularTitle()) + " \" + " + target + "." + id + "()"
                : "\"" + escape(resource.getSingularTitle()) + "\"";
    }

    private static String javaType(ApiField field, Set<String> imports) {
        addTypeImport(field, imports);
        return field.getJavaType().getSimpleName();
    }

    private static void addTypeImport(ApiField field, Set<String> imports) {
        Class<?> type = field.getJavaType();
        if (!type.getName().startsWith("java.lang.")) {
            imports.add(type.getName());
        }
    }

    private static String capitalize(String text) {
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String escapeComment(String text) {
        return text.replace("*/", "* /");
    }
}
