package com.rapidswing.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    @Test
    void verifiesOnlyTheRightPassword() {
        String hash = PasswordHasher.hash("s3cret!".toCharArray(), 1000);

        assertTrue(hash.startsWith("pbkdf2-sha256$1000$"));
        assertTrue(PasswordHasher.verify("s3cret!".toCharArray(), hash));
        assertFalse(PasswordHasher.verify("s3cret".toCharArray(), hash));
        assertFalse(PasswordHasher.verify("S3cret!".toCharArray(), hash));
    }

    @Test
    void saltMakesEveryHashDifferent() {
        assertNotEquals(PasswordHasher.hash("same".toCharArray(), 1000), PasswordHasher.hash("same".toCharArray(), 1000));
    }

    @Test
    void rejectsEmptyAndMalformedInput() {
        String hash = PasswordHasher.hash("x".toCharArray(), 1000);

        assertFalse(PasswordHasher.verify(new char[0], hash));
        assertFalse(PasswordHasher.verify(null, hash));
        assertFalse(PasswordHasher.verify("x".toCharArray(), null));
        assertFalse(PasswordHasher.verify("x".toCharArray(), "plain-text-password"));
        assertFalse(PasswordHasher.verify("x".toCharArray(), "pbkdf2-sha256$abc$AAAA$AAAA"));
        assertFalse(PasswordHasher.verify("x".toCharArray(), "pbkdf2-sha256$1000$not base64!$AAAA"));
        assertFalse(PasswordHasher.verify("x".toCharArray(), "pbkdf2-sha256$999999999$AAAA$AAAA"));
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash(new char[0]));
    }
}
