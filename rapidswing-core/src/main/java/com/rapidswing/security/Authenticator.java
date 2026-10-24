package com.rapidswing.security;

/**
 * Checks credentials: against a database, LDAP, a REST API, or a {@link SimpleAuthenticator}.
 * <ul>
 *   <li>Called on a background thread, so it may block.</li>
 *   <li>Throw {@link AuthenticationException} for wrong credentials. Any other exception means sign-in
 *       is unavailable (e.g. the server is down) and is reported as such.</li>
 *   <li>Do not keep the password array: the login screen clears it as soon as this method returns.</li>
 *   <li>Account lockout and rate limiting belong here or on the server; the login screen only adds a
 *       growing delay after repeated failures.</li>
 * </ul>
 */
@FunctionalInterface
public interface Authenticator {

    User authenticate(String username, char[] password) throws Exception;
}
