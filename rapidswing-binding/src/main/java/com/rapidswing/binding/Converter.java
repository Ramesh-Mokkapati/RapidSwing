package com.rapidswing.binding;

/**
 * Converts between a value and the text shown in a text component. See {@link Converters} for built-in ones.
 */
public interface Converter<T> {

    T fromString(String text) throws ConversionException;

    /** Must accept {@code null} and return the text for an empty field, usually {@code ""}. */
    String toString(T value);
}
