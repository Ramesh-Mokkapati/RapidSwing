package com.rapidswing.binding;

/**
 * Thrown when text typed by the user cannot be converted. The message is shown to the user,
 * so keep it short and helpful, e.g. "Enter a whole number".
 */
public class ConversionException extends Exception {

    public ConversionException(String message) {
        super(message);
    }

    public ConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
