package com.rapidswing.binding;

import com.rapidswing.i18n.I18n;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Built-in converters. Apart from the string converters, blank text converts to {@code null}
 * and {@code null} displays as an empty field. Error messages come from {@link I18n#global()}.
 */
public final class Converters {

    private Converters() {
    }

    private static Supplier<String> message(String key) {
        return () -> I18n.global().get(key);
    }

    /** Text as typed. {@code null} displays as an empty field. */
    public static Converter<String> string() {
        return new Converter<String>() {
            @Override
            public String fromString(String text) {
                return text;
            }

            @Override
            public String toString(String value) {
                return value == null ? "" : value;
            }
        };
    }

    /** Text with leading and trailing whitespace removed. */
    public static Converter<String> trimmed() {
        return new Converter<String>() {
            @Override
            public String fromString(String text) {
                return text.trim();
            }

            @Override
            public String toString(String value) {
                return value == null ? "" : value;
            }
        };
    }

    public static Converter<Integer> integer() {
        return parsing(Integer::valueOf, String::valueOf, message("convert.wholeNumber"));
    }

    public static Converter<Long> longs() {
        return parsing(Long::valueOf, String::valueOf, message("convert.wholeNumber"));
    }

    public static Converter<Double> doubles() {
        return parsing(Double::valueOf, String::valueOf, message("convert.number"));
    }

    public static Converter<BigDecimal> decimal() {
        return parsing(BigDecimal::new, BigDecimal::toPlainString, message("convert.number"));
    }

    /** ISO dates such as {@code 2026-10-23}. */
    public static Converter<LocalDate> localDate() {
        return localDate(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /**
     * Dates in a custom format. For strict validation use a formatter with
     * {@code ResolverStyle.STRICT}, and {@code uuuu} rather than {@code yyyy} for the year.
     */
    public static Converter<LocalDate> localDate(DateTimeFormatter formatter) {
        Objects.requireNonNull(formatter, "formatter");
        String example = formatter.format(LocalDate.of(2026, 12, 31));
        return parsing(text -> LocalDate.parse(text, formatter), formatter::format,
                () -> I18n.global().get("convert.date", example));
    }

    /**
     * Creates a converter from a parse and a format function. Blank text converts to {@code null}.
     * Any runtime exception thrown by {@code parse} becomes a {@link ConversionException} with the given message.
     */
    public static <T> Converter<T> parsing(Function<String, ? extends T> parse, Function<? super T, String> format,
                                           String errorMessage) {
        return parsing(parse, format, () -> errorMessage);
    }

    /** Like {@link #parsing(Function, Function, String)}, with the message looked up when an error occurs. */
    public static <T> Converter<T> parsing(Function<String, ? extends T> parse, Function<? super T, String> format,
                                           Supplier<String> errorMessage) {
        Objects.requireNonNull(parse, "parse");
        Objects.requireNonNull(format, "format");
        return new Converter<T>() {
            @Override
            public T fromString(String text) throws ConversionException {
                String trimmed = text == null ? "" : text.trim();
                if (trimmed.isEmpty()) {
                    return null;
                }
                try {
                    return parse.apply(trimmed);
                } catch (RuntimeException e) {
                    throw new ConversionException(errorMessage.get(), e);
                }
            }

            @Override
            public String toString(T value) {
                return value == null ? "" : format.apply(value);
            }
        };
    }
}
