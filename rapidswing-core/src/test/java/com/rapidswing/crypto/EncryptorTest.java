package com.rapidswing.crypto;

import com.rapidswing.app.AppConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncryptorTest {

    private static final int FAST_ITERATIONS = 1000;
    private final Encryptor encryptor = Encryptor.withKey(Keys.generate());

    @Test
    void roundTripsBytesAndStrings() {
        byte[] plain = "Größe 42 €".getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(plain, encryptor.decrypt(encryptor.encrypt(plain)));
        assertEquals("secret", encryptor.decryptToString(encryptor.encryptToString("secret")));
        assertEquals("", encryptor.decryptToString(encryptor.encryptToString("")));
    }

    @Test
    void sameInputEncryptsDifferentlyEveryTime() {
        assertNotEquals(encryptor.encryptToString("same"), encryptor.encryptToString("same"));
    }

    @Test
    void anyModificationIsDetected() {
        byte[] sealed = encryptor.encrypt("transfer 100".getBytes(StandardCharsets.UTF_8));
        for (int position : new int[]{0, 4, 6, sealed.length / 2, sealed.length - 1}) {
            byte[] tampered = sealed.clone();
            tampered[position] ^= 1;
            assertThrows(CryptoException.class, () -> encryptor.decrypt(tampered), "byte " + position);
        }
        assertThrows(CryptoException.class, () -> encryptor.decrypt(Arrays.copyOf(sealed, sealed.length - 1)));
    }

    @Test
    void wrongKeyFailsWithoutDetails() {
        byte[] sealed = encryptor.encrypt(new byte[]{1, 2, 3});
        CryptoException e = assertThrows(CryptoException.class,
                () -> Encryptor.withKey(Keys.generate()).decrypt(sealed));

        assertEquals("Decryption failed: wrong key or password, or the data was modified", e.getMessage());
    }

    @Test
    void associatedDataMustMatch() {
        byte[] sealed = encryptor.encrypt("4111 1111".getBytes(StandardCharsets.UTF_8), "customer-7".getBytes(StandardCharsets.UTF_8));

        assertEquals("4111 1111", new String(encryptor.decrypt(sealed, "customer-7".getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
        assertThrows(CryptoException.class, () -> encryptor.decrypt(sealed, "customer-8".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void passwordIsAllThatIsNeededToDecrypt() {
        String sealed = Encryptor.withPassword("correct horse".toCharArray(), FAST_ITERATIONS).encryptToString("diary");

        assertEquals("diary", Encryptor.withPassword("correct horse".toCharArray(), FAST_ITERATIONS).decryptToString(sealed));
        assertThrows(CryptoException.class,
                () -> Encryptor.withPassword("wrong horse".toCharArray(), FAST_ITERATIONS).decryptToString(sealed));
    }

    @Test
    void keysAndPasswordsAreNotMixedUp() {
        String byKey = encryptor.encryptToString("x");
        Encryptor byPassword = Encryptor.withPassword("pw".toCharArray(), FAST_ITERATIONS);

        assertEquals("This data was encrypted with a key, not a password",
                assertThrows(CryptoException.class, () -> byPassword.decryptToString(byKey)).getMessage());
        assertEquals("This data was encrypted with a password, not a key",
                assertThrows(CryptoException.class, () -> encryptor.decryptToString(byPassword.encryptToString("x"))).getMessage());
    }

    @Test
    void keysExportAndImport() {
        SecretKey key = Keys.generate();
        String sealed = Encryptor.withKey(key).encryptToString("value");

        assertEquals("value", Encryptor.withKey(Keys.fromBase64(Keys.toBase64(key))).decryptToString(sealed));
        assertThrows(CryptoException.class, () -> Keys.fromBase64("c2hvcnQ="));
    }

    @Test
    void streamsRoundTripAtEveryChunkBoundary() throws IOException {
        int chunk = Encryptor.CHUNK_SIZE;
        for (int size : new int[]{0, 1, chunk - 1, chunk, chunk + 1, 3 * chunk + 5}) {
            byte[] plain = randomBytes(size);
            assertArrayEquals(plain, decryptStream(encryptStream(plain)), "size " + size);
        }
    }

    @Test
    void truncatedOrExtendedStreamsAreDetected() throws IOException {
        byte[] sealed = encryptStream(randomBytes(2 * Encryptor.CHUNK_SIZE + 10));
        int lastChunk = 4 + 10 + 16;

        assertThrows(CryptoException.class, () -> decryptStream(Arrays.copyOf(sealed, sealed.length - lastChunk)));
        assertThrows(CryptoException.class, () -> decryptStream(Arrays.copyOf(sealed, sealed.length - 1)));
        assertThrows(CryptoException.class, () -> decryptStream(Arrays.copyOf(sealed, sealed.length + 1)));
    }

    @Test
    void filesAreReplacedOnlyOnSuccess(@TempDir Path dir) throws IOException {
        Path plain = dir.resolve("report.txt");
        Path sealed = dir.resolve("report.txt.enc");
        Path restored = dir.resolve("restored.txt");
        Files.write(plain, randomBytes(100_000));

        encryptor.encryptFile(plain, sealed);
        assertTrue(Encryptor.isEncryptedFile(sealed));
        assertFalse(Encryptor.isEncryptedFile(plain));
        encryptor.decryptFile(sealed, restored);
        assertArrayEquals(Files.readAllBytes(plain), Files.readAllBytes(restored));

        Path failed = dir.resolve("failed.txt");
        assertThrows(CryptoException.class, () -> Encryptor.withKey(Keys.generate()).decryptFile(sealed, failed));
        assertFalse(Files.exists(failed));
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(3, files.count(), "only the three expected files, no temporary ones");
        }
    }

    @Test
    void configDecryptsEncValues() {
        String sealed = encryptor.encryptToString("s3cret");
        AppConfig config = new AppConfig().set("db.password", "ENC(" + sealed + ")").set("db.user", "app");

        assertThrows(IllegalStateException.class, () -> config.get("db.password", null));
        config.decryptWith(encryptor);
        assertEquals("s3cret", config.get("db.password", null));
        assertEquals("app", config.get("db.user", null));
    }

    private byte[] encryptStream(byte[] plain) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        encryptor.encrypt(new ByteArrayInputStream(plain), out);
        return out.toByteArray();
    }

    private byte[] decryptStream(byte[] sealed) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        encryptor.decrypt(new ByteArrayInputStream(sealed), out);
        return out.toByteArray();
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        new Random(size).nextBytes(bytes);
        return bytes;
    }
}
