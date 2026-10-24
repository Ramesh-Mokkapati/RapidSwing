package com.rapidswing.data;

import java.util.Objects;

/** One search condition: a field, an {@link Operator} and its values, e.g. "city CONTAINS ber". Immutable. */
public final class Filter {

    private final String field;
    private final Operator operator;
    private final Object value;
    private final Object secondValue;

    private Filter(String field, Operator operator, Object value, Object secondValue) {
        this.field = Objects.requireNonNull(field, "field");
        this.operator = Objects.requireNonNull(operator, "operator");
        this.value = value;
        this.secondValue = secondValue;
    }

    public static Filter of(String field, Operator operator, Object value) {
        return new Filter(field, operator, value, null);
    }

    public static Filter between(String field, Object from, Object to) {
        return new Filter(field, Operator.BETWEEN, from, to);
    }

    public static Filter of(String field, Operator operator) {
        return new Filter(field, operator, null, null);
    }

    public String getField() {
        return field;
    }

    public Operator getOperator() {
        return operator;
    }

    public Object getValue() {
        return value;
    }

    /** The upper bound of {@link Operator#BETWEEN}. */
    public Object getSecondValue() {
        return secondValue;
    }

    public boolean matches(Object actual) {
        return operator.matches(actual, value, secondValue);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Filter)) {
            return false;
        }
        Filter that = (Filter) other;
        return field.equals(that.field) && operator == that.operator && Objects.equals(value, that.value)
                && Objects.equals(secondValue, that.secondValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, operator, value, secondValue);
    }

    @Override
    public String toString() {
        switch (operator.valueCount()) {
            case 0:
                return field + " " + operator;
            case 2:
                return field + " " + operator + " " + value + " AND " + secondValue;
            default:
                return field + " " + operator + " " + value;
        }
    }
}
