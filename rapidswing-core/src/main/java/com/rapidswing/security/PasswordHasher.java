package com.rapidswing.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.Console;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Salted PBKDF2-SHA256 password hashes, available on every Java 8 runtime.
 * Stored format: {@code pbkdf2-sha256$<iterations>$<salt base64>$<hash base64>}.
 * <p>
 * Generate a hash from the command line (the password is read without echo):
 * <pre>java -cp rapidswing-core.jar com.rapidswing.security.PasswordHasher</pre>
 */
public final class PasswordHasher {

    public static final int DEFAULT_ITERATIONS = 310_000;

    private static final String PREFIX = "pbkdf2-sha256";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final int MAX_ITERATIONS = 10_000_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static String hash(char[] password) {
        return hash(password, DEFAULT_ITERATIONS);
    }

    public static String hash(char[] password, int iterations) {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        if (iterations < 1 || iterations > MAX_ITERATIONS) {
            throw new IllegalArgumentException("Iterations out of range: " + iterations);
        }
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] key = derive(password, salt, iterations, KEY_BITS);
        Base64.Encoder base64 = Base64.getEncoder();
        return PREFIX + "$" + iterations + "$" + base64.encodeToString(salt) + "$" + base64.encodeToString(key);
    }

    /** Constant-time check. Returns false for an empty password or a malformed stored hash. */
    public static boolean verify(char[] password, String stored) {
        if (password == null || password.length == 0 || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 1 || iterations > MAX_ITERATIONS) {
                return false;
            }
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            if (salt.length == 0 || expected.length == 0) {
                return false;
            }
            byte[] actual = derive(password, salt, iterations, expected.length * 8);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations, int bits) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, bits);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(ALGORITHM + " is not available", e);
        } finally {
            spec.clearPassword();
        }
    }

    /** Prints the hash of a password typed on the console. */
    public static void main(String[] args) {
        Console console = System.console();
        if (console == null) {
            System.err.println("Run this from a terminal, so the password can be read without echo.");
            System.exit(1);
        }
        char[] password = console.readPassword("Password: ");
        try {
            System.out.println(hash(password));
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
