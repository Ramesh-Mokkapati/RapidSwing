package com.rapidswing.security;

/**
 * Wrong user name or password. Deliberately carries no detail about which one was wrong,
 * so the login screen cannot reveal whether a user name exists.
 */
public class AuthenticationException extends Exception {

    public AuthenticationException() {
        super("Invalid credentials");
    }
}
