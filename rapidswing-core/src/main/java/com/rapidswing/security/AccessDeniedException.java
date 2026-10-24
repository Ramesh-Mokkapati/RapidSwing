package com.rapidswing.security;

/** Thrown by {@link Session#require} when the current user lacks the required role. */
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
