package com.rapidswing.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256 keys: random generation, Base64 export/import and password-based derivation (PBKDF2-SHA256).
 * <p>
 * Store keys like passwords: in an environment variable, an OS key store or a secrets manager. Never commit them
 * with the code that uses them.
 */
public final class Keys {

    public static final int KEY_BYTES = 32;
    public static final int DEFAULT_ITERATIONS = 310_000;
    static final int SALT_BYTES = 16;
    static final int MAX_ITERATIONS = 10_000_000;
    static final SecureRandom RANDOM = new SecureRandom();

    private Keys() {
    }

    /** A new random 256-bit AES key. */
    public static SecretKey generate() {
        byte[] bytes = new byte[KEY_BYTES];
        RANDOM.nextBytes(bytes);
        try {
            return fromBytes(bytes);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    public static SecretKey fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length != KEY_BYTES) {
            throw new CryptoException("An AES-256 key must be exactly " + KEY_BYTES + " bytes");
        }
        checkAes256Available();
        return new SecretKeySpec(bytes, "AES");
    }

    public static String toBase64(SecretKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static SecretKey fromBase64(String base64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new CryptoException("The key is not valid Base64", e);
        }
        try {
            return fromBytes(bytes);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    /** Derives an AES-256 key from a password. The same password, salt and iterations give the same key. */
    public static SecretKey derive(char[] password, byte[] salt, int iterations) {
        if (password == null || password.length == 0) {
            throw new CryptoException("The password must not be empty");
        }
        if (iterations < 1 || iterations > MAX_ITERATIONS) {
            throw new CryptoException("Iterations out of range: " + iterations);
        }
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BYTES * 8);
        try {
            byte[] bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            try {
                return fromBytes(bytes);
            } finally {
                Arrays.fill(bytes, (byte) 0);
            }
        } catch (GeneralSecurityException e) {
            throw new CryptoException("PBKDF2WithHmacSHA256 is not available", e);
        } finally {
            spec.clearPassword();
        }
    }

    static byte[] newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return salt;
    }

    /** Java 8 before update 161 limited AES to 128 bits unless the unlimited policy files were installed. */
    static void checkAes256Available() {
        try {
            if (Cipher.getMaxAllowedKeyLength("AES") < 256) {
                throw new CryptoException("AES-256 is disabled by this Java runtime's crypto policy. Use Java 8 "
                        + "update 161 or later, or set crypto.policy=unlimited in java.security.");
            }
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException("AES is not available", e);
        }
    }
}
