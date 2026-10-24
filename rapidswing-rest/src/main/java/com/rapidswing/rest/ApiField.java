package com.rapidswing.rest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * One property of an API resource's schema, with the constraints the GUI turns into validation rules.
 */
public final class ApiField {

    /** The value types RapidSwing supports in tables and forms. Nested objects and arrays are skipped. */
    public enum Type {
        STRING(String.class), INTEGER(Integer.class), LONG(Long.class), DECIMAL(BigDecimal.class),
        BOOLEAN(Boolean.class), DATE(LocalDate.class), DATE_TIME(String.class), ENUM(String.class);

        private final Class<?> javaType;

        Type(Class<?> javaType) {
            this.javaType = javaType;
        }

        public Class<?> getJavaType() {
            return javaType;
        }
    }

    private final String name;
    private final Type type;
    private final boolean required;
    private final boolean readOnly;
    private final Integer minLength;
    private final Integer maxLength;
    private final BigDecimal minimum;
    private final BigDecimal maximum;
    private final String pattern;
    private final boolean email;
    private final List<String> enumValues;

    ApiField(String name, Type type, boolean required, boolean readOnly, Integer minLength, Integer maxLength,
             BigDecimal minimum, BigDecimal maximum, String pattern, boolean email, List<String> enumValues) {
        this.name = name;
        this.type = type;
        this.required = required;
        this.readOnly = readOnly;
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.minimum = minimum;
        this.maximum = maximum;
        this.pattern = pattern;
        this.email = email;
        this.enumValues = Collections.unmodifiableList(enumValues);
    }

    /** The JSON property name. */
    public String getName() {
        return name;
    }

    /** A readable label: {@code firstName} and {@code first_name} become "First name". */
    public String getLabel() {
        String spaced = name.replace('_', ' ').replaceAll("([a-z0-9])([A-Z])", "$1 $2").trim().toLowerCase(Locale.ROOT);
        return spaced.isEmpty() ? name : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    public Type getType() {
        return type;
    }

    public Class<?> getJavaType() {
        return type.getJavaType();
    }

    public boolean isRequired() {
        return required;
    }

    /** Set by the server, such as a generated id: shown in tables, not editable in forms. */
    public boolean isReadOnly() {
        return readOnly;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public BigDecimal getMinimum() {
        return minimum;
    }

    public BigDecimal getMaximum() {
        return maximum;
    }

    public String getPattern() {
        return pattern;
    }

    public boolean isEmail() {
        return email;
    }

    public List<String> getEnumValues() {
        return enumValues;
    }

    @Override
    public String toString() {
        return name + ": " + type + (required ? " (required)" : "") + (readOnly ? " (read-only)" : "");
    }
}
