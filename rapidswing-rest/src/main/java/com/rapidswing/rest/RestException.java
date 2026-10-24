package com.rapidswing.rest;

/** An HTTP error response, with the message from the server's error body when it has one. */
public class RestException extends RuntimeException {

    private final int status;

    public RestException(int status, String message) {
        super(message);
        this.status = status;
    }

    /** The HTTP status code, e.g. 400 or 404. */
    public int getStatus() {
        return status;
    }
}
