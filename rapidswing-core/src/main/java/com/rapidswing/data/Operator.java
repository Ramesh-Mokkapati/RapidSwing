package com.rapidswing.data;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * How a {@link Filter} compares a field with its value(s). Text comparisons ignore
 * case. {@link #matches} evaluates an operator in memory; repositories that query a server or database translate
 * operators into their own query language instead.
 */
public enum Operator {
    EQUALS(1), NOT_EQUALS(1), CONTAINS(1), STARTS_WITH(1), ENDS_WITH(1),
    /** {@code *} matches any text, {@code ?} one character. */
    LIKE(1),
    GREATER(1), GREATER_OR_EQUAL(1), LESS(1), LESS_OR_EQUAL(1),
    /** Inclusive on both ends. */
    BETWEEN(2),
    IS_EMPTY(0), NOT_EMPTY(0);

    private final int valueCount;

    Operator(int valueCount) {
        this.valueCount = valueCount;
    }

    /** How many values the operator needs: 0, 1 or 2. */
    public int valueCount() {
        return valueCount;
    }

    /** Whether it applies to text only. */
    public boolean isTextOnly() {
        return this == CONTAINS || this == STARTS_WITH || this == ENDS_WITH || this == LIKE;
    }

    /** Whether it needs values that can be ordered, such as numbers or dates. */
    public boolean isOrdering() {
        return this == GREATER || this == GREATER_OR_EQUAL || this == LESS || this == LESS_OR_EQUAL
                || this == BETWEEN;
    }

    /** The operators that make sense for values of the type, for search forms. */
    public static Operator[] forType(Class<?> type) {
        if (type == String.class) {
            return new Operator[]{CONTAINS, EQUALS, NOT_EQUALS, STARTS_WITH, ENDS_WITH, LIKE, IS_EMPTY, NOT_EMPTY};
        }
        if (type.isEnum() || type == Boolean.class || type == boolean.class) {
            return new Operator[]{EQUALS, NOT_EQUALS, IS_EMPTY, NOT_EMPTY};
        }
        if (Comparable.class.isAssignableFrom(type) || type.isPrimitive()) {
            return new Operator[]{EQUALS, NOT_EQUALS, GREATER, GREATER_OR_EQUAL, LESS, LESS_OR_EQUAL, BETWEEN,
                    IS_EMPTY, NOT_EMPTY};
        }
        return new Operator[]{EQUALS, NOT_EQUALS, IS_EMPTY, NOT_EMPTY};
    }

    public boolean matches(Object actual, Object value, Object secondValue) {
        switch (this) {
            case IS_EMPTY:
                return isEmpty(actual);
            case NOT_EMPTY:
                return !isEmpty(actual);
            case EQUALS:
                return equal(actual, value);
            case NOT_EQUALS:
                return !equal(actual, value);
            case CONTAINS:
                return actual != null && text(actual).contains(text(value));
            case STARTS_WITH:
                return actual != null && text(actual).startsWith(text(value));
            case ENDS_WITH:
                return actual != null && text(actual).endsWith(text(value));
            case LIKE:
                return actual != null && wildcard(text(value)).matcher(text(actual)).matches();
            case GREATER:
                return actual != null && compare(actual, value) > 0;
            case GREATER_OR_EQUAL:
                return actual != null && compare(actual, value) >= 0;
            case LESS:
                return actual != null && compare(actual, value) < 0;
            case LESS_OR_EQUAL:
                return actual != null && compare(actual, value) <= 0;
            case BETWEEN:
                return actual != null && compare(actual, value) >= 0 && compare(actual, secondValue) <= 0;
            default:
                throw new AssertionError(this);
        }
    }

    /**
     * Orders two values: numbers of different classes numerically, text ignoring case, other comparables
     * naturally, and {@code null} first.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static int compare(Object a, Object b) {
        if (a == b) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        if (a instanceof Number && b instanceof Number) {
            return decimal((Number) a).compareTo(decimal((Number) b));
        }
        if (a instanceof String && b instanceof String) {
            return ((String) a).compareToIgnoreCase((String) b);
        }
        if (a instanceof Comparable && a.getClass().isInstance(b)) {
            return ((Comparable) a).compareTo(b);
        }
        return text(a).compareTo(text(b));
    }

    private static boolean equal(Object actual, Object value) {
        if (actual == null || value == null) {
            return actual == value;
        }
        if (actual instanceof Number && value instanceof Number) {
            return compare(actual, value) == 0;
        }
        if (actual instanceof String && value instanceof String) {
            return ((String) actual).equalsIgnoreCase((String) value);
        }
        return actual.equals(value);
    }

    private static boolean isEmpty(Object value) {
        return value == null || (value instanceof CharSequence && value.toString().trim().isEmpty());
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().toLowerCase(Locale.ROOT);
    }

    private static BigDecimal decimal(Number number) {
        return number instanceof BigDecimal ? (BigDecimal) number : new BigDecimal(number.toString());
    }

    private static Pattern wildcard(String pattern) {
        StringBuilder regex = new StringBuilder();
        for (char c : pattern.toCharArray()) {
            if (c == '*') {
                regex.append(".*");
            } else if (c == '?') {
                regex.append('.');
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString(), Pattern.DOTALL);
    }
}
