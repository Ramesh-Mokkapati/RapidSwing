package com.rapidswing.crypto;

/**
 * Encryption or decryption failed. For decryption this deliberately does not say why: a wrong key, a wrong
 * password and modified or truncated data all look the same, so the message cannot help an attacker.
 */
public class CryptoException extends RuntimeException {

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }

    static CryptoException decryptionFailed(Throwable cause) {
        return new CryptoException("Decryption failed: wrong key or password, or the data was modified", cause);
    }
}
