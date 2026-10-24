package com.rapidswing.forms;

import com.rapidswing.i18n.I18n;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Built-in validation rules. Apart from {@link #required()}, every rule accepts empty values.
 * Messages come from {@link I18n#global()} when a value fails, so they follow locale changes.
 */
public final class Validators {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private Validators() {
    }

    /** Rejects {@code null}, blank text and empty collections. */
    public static <T> Validator<T> required() {
        return value -> isEmpty(value) ? text("validation.required") : null;
    }

    public static <T> Validator<T> required(String message) {
        return value -> isEmpty(value) ? message : null;
    }

    public static Validator<CharSequence> minLength(int min) {
        return text -> isEmpty(text) || text.length() >= min ? null : text("validation.minLength", min);
    }

    public static Validator<CharSequence> maxLength(int max) {
        return text -> isEmpty(text) || text.length() <= max ? null : text("validation.maxLength", max);
    }

    public static Validator<CharSequence> pattern(String regex, String message) {
        Pattern pattern = Pattern.compile(regex);
        return text -> isEmpty(text) || pattern.matcher(text).matches() ? null : message;
    }

    /** A pragmatic check for {@code name@domain.tld}; real verification needs a confirmation email. */
    public static Validator<CharSequence> email() {
        return text -> isEmpty(text) || EMAIL.matcher(text).matches() ? null : text("validation.email");
    }

    public static <T extends Comparable<? super T>> Validator<T> min(T min) {
        Objects.requireNonNull(min, "min");
        return value -> value == null || value.compareTo(min) >= 0 ? null : text("validation.min", min);
    }

    public static <T extends Comparable<? super T>> Validator<T> max(T max) {
        Objects.requireNonNull(max, "max");
        return value -> value == null || value.compareTo(max) <= 0 ? null : text("validation.max", max);
    }

    public static <T extends Comparable<? super T>> Validator<T> range(T min, T max) {
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
        return value -> value == null || (value.compareTo(min) >= 0 && value.compareTo(max) <= 0)
                ? null : text("validation.range", min, max);
    }

    public static Validator<Number> positive() {
        return value -> value == null || value.doubleValue() > 0 ? null : text("validation.positive");
    }

    /** Dates before today. */
    public static Validator<LocalDate> past() {
        return date -> date == null || date.isBefore(LocalDate.now()) ? null : text("validation.past");
    }

    /** Dates after today. */
    public static Validator<LocalDate> future() {
        return date -> date == null || date.isAfter(LocalDate.now()) ? null : text("validation.future");
    }

    /** A rule from a condition: the value is valid when the predicate is true. Empty values are accepted. */
    public static <T> Validator<T> of(Predicate<? super T> condition, String message) {
        Objects.requireNonNull(condition, "condition");
        return value -> isEmpty(value) || condition.test(value) ? null : message;
    }

    private static String text(String key, Object... args) {
        return I18n.global().get(key, args);
    }

    static boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof CharSequence) {
            return value.toString().trim().isEmpty();
        }
        return value instanceof Collection && ((Collection<?>) value).isEmpty();
    }
}
