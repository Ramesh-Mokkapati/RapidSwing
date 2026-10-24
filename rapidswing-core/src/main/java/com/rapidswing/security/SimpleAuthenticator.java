package com.rapidswing.security;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Users defined in code or configuration, with {@link PasswordHasher} hashes. Good for prototypes,
 * single-user installations and tests; production apps usually authenticate against a server instead.
 * <pre>
 * new SimpleAuthenticator()
 *         .addUser("admin", "Administrator", "pbkdf2-sha256$310000$...", "admin");
 * </pre>
 * User names are case-insensitive. Password changes are kept in memory only; override
 * {@link #passwordChanged} to store them.
 */
public class SimpleAuthenticator implements Authenticator, PasswordChanger {

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    public SimpleAuthenticator addUser(String username, String displayName, String passwordHash, String... roles) {
        Objects.requireNonNull(passwordHash, "passwordHash");
        accounts.put(normalize(username), new Account(User.of(username, displayName, roles), passwordHash));
        return this;
    }

    @Override
    public User authenticate(String username, char[] password) throws AuthenticationException {
        Account account = username == null ? null : accounts.get(normalize(username));
        // Hash even for unknown users, so response time does not reveal which user names exist.
        String hash = account != null ? account.passwordHash : UnknownUser.HASH;
        boolean matches = PasswordHasher.verify(password, hash);
        if (account == null || !matches) {
            throw new AuthenticationException();
        }
        return account.user;
    }

    @Override
    public void changePassword(String username, char[] currentPassword, char[] newPassword)
            throws AuthenticationException {
        Objects.requireNonNull(newPassword, "newPassword");
        authenticate(username, currentPassword);
        String key = normalize(username);
        Account account = accounts.get(key);
        String hash = PasswordHasher.hash(newPassword);
        accounts.put(key, new Account(account.user, hash));
        passwordChanged(account.user, hash);
    }

    /** Called after a password change with the new hash, e.g. to write it to a configuration file. */
    protected void passwordChanged(User user, String newPasswordHash) {
    }

    private static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private static final class Account {
        final User user;
        final String passwordHash;

        Account(User user, String passwordHash) {
            this.user = user;
            this.passwordHash = passwordHash;
        }
    }

    /** Computed on first use, so creating an authenticator stays fast. */
    private static final class UnknownUser {
        static final String HASH = PasswordHasher.hash("no such user".toCharArray());
    }
}
