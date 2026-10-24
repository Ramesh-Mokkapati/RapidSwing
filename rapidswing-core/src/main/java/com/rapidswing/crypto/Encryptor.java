package com.rapidswing.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AES-256-GCM encryption of bytes, strings, streams and files. GCM also authenticates: decrypting modified,
 * truncated or reordered data fails with a {@link CryptoException} instead of returning garbage.
 * <pre>
 * Encryptor encryptor = Encryptor.withKey(Keys.fromBase64(System.getenv("APP_ENCRYPTION_KEY")));
 * String secret = encryptor.encryptToString("account number");
 * String plain = encryptor.decryptToString(secret);
 *
 * Encryptor.withPassword(password).encryptFile(report, encryptedReport);
 * </pre>
 * <ul>
 *   <li>Every message gets a random 96-bit IV; password-based encryption stores a random salt and the PBKDF2
 *       iteration count in the output, so the password is all that is needed to decrypt.</li>
 *   <li>Streams and files are encrypted in 64 KB chunks, so they are processed in constant memory. The chunk
 *       order and the end of the data are authenticated as well.</li>
 *   <li>The output starts with a short header identifying the format, which keeps old data readable when the
 *       format evolves.</li>
 * </ul>
 * Instances are thread-safe. Call {@link #destroy()} when done with a password-based encryptor.
 */
public final class Encryptor {

    private static final byte[] MESSAGE_MAGIC = {'R', 'S', 'E', '1'};
    private static final byte[] STREAM_MAGIC = {'R', 'S', 'F', '1'};
    private static final byte MODE_KEY = 0;
    private static final byte MODE_PASSWORD = 1;
    private static final int IV_BYTES = 12;
    private static final int TAG_BYTES = 16;
    private static final int NONCE_PREFIX_BYTES = 8;
    static final int CHUNK_SIZE = 64 * 1024;
    private static final int LAST_CHUNK_FLAG = 0x80000000;

    private final SecretKey key;
    private final char[] password;
    private final byte[] salt;
    private final int iterations;
    private final Map<String, SecretKey> derivedKeys = new ConcurrentHashMap<>();

    private Encryptor(SecretKey key, char[] password, byte[] salt, int iterations) {
        this.key = key;
        this.password = password;
        this.salt = salt;
        this.iterations = iterations;
    }

    public static Encryptor withKey(SecretKey key) {
        Objects.requireNonNull(key, "key");
        byte[] encoded = key.getEncoded();
        try {
            if (encoded == null || encoded.length != Keys.KEY_BYTES) {
                throw new CryptoException("An AES-256 key must be exactly " + Keys.KEY_BYTES + " bytes");
            }
        } finally {
            if (encoded != null) {
                Arrays.fill(encoded, (byte) 0);
            }
        }
        Keys.checkAes256Available();
        return new Encryptor(key, null, null, 0);
    }

    /** Password-based encryption with {@link Keys#DEFAULT_ITERATIONS}. The password array is copied. */
    public static Encryptor withPassword(char[] password) {
        return withPassword(password, Keys.DEFAULT_ITERATIONS);
    }

    public static Encryptor withPassword(char[] password, int iterations) {
        byte[] salt = Keys.newSalt();
        SecretKey derived = Keys.derive(password, salt, iterations);
        Encryptor encryptor = new Encryptor(derived, password.clone(), salt, iterations);
        encryptor.derivedKeys.put(cacheKey(salt, iterations), derived);
        return encryptor;
    }

    // ---- Bytes and strings ---------------------------------------------------------------------------

    public byte[] encrypt(byte[] plaintext) {
        return encrypt(plaintext, new byte[0]);
    }

    /**
     * @param associatedData data that is not encrypted but must match on decryption, e.g. a record id, so an
     *                       encrypted value cannot be copied to another record unnoticed
     */
    public byte[] encrypt(byte[] plaintext, byte[] associatedData) {
        Objects.requireNonNull(plaintext, "plaintext");
        byte[] header = header(MESSAGE_MAGIC);
        byte[] iv = new byte[IV_BYTES];
        Keys.RANDOM.nextBytes(iv);
        byte[] ciphertext = seal(key, iv, plaintext, 0, plaintext.length, header, associatedData);
        ByteBuffer out = ByteBuffer.allocate(header.length + iv.length + ciphertext.length);
        return out.put(header).put(iv).put(ciphertext).array();
    }

    public byte[] decrypt(byte[] data) {
        return decrypt(data, new byte[0]);
    }

    public byte[] decrypt(byte[] data, byte[] associatedData) {
        Objects.requireNonNull(data, "data");
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            Header header = readHeader(in, MESSAGE_MAGIC);
            byte[] iv = readExactly(in, IV_BYTES);
            byte[] ciphertext = new byte[in.available()];
            in.readFully(ciphertext);
            return open(header.key, iv, ciphertext, header.bytes, associatedData);
        } catch (IOException e) {
            throw CryptoException.decryptionFailed(e);
        }
    }

    /** Encrypts UTF-8 text to Base64, e.g. for a database column or a config value. */
    public String encryptToString(String plaintext) {
        return Base64.getEncoder().encodeToString(encrypt(plaintext.getBytes(StandardCharsets.UTF_8)));
    }

    public String decryptToString(String base64) {
        byte[] data;
        try {
            data = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw CryptoException.decryptionFailed(e);
        }
        return new String(decrypt(data), StandardCharsets.UTF_8);
    }

    // ---- Streams and files ---------------------------------------------------------------------------

    /** Encrypts everything from {@code in} to {@code out}. Neither stream is closed. */
    public void encrypt(InputStream in, OutputStream out) throws IOException {
        byte[] header = header(STREAM_MAGIC);
        byte[] noncePrefix = new byte[NONCE_PREFIX_BYTES];
        Keys.RANDOM.nextBytes(noncePrefix);
        DataOutputStream data = new DataOutputStream(out);
        data.write(header);
        data.write(noncePrefix);

        byte[] current = new byte[CHUNK_SIZE];
        byte[] next = new byte[CHUNK_SIZE];
        int currentLength = readUpTo(in, current);
        for (int counter = 0; ; counter++) {
            // Read ahead, so the last chunk can be marked as last: this is what detects truncation.
            int nextLength = currentLength == CHUNK_SIZE ? readUpTo(in, next) : 0;
            boolean last = nextLength == 0;
            byte[] sealed = seal(key, chunkIv(noncePrefix, counter), current, 0, currentLength, header,
                    chunkData(counter, last));
            data.writeInt(last ? sealed.length | LAST_CHUNK_FLAG : sealed.length);
            data.write(sealed);
            if (last) {
                break;
            }
            if (counter == Integer.MAX_VALUE) {
                throw new CryptoException("The data is too large to encrypt as one stream");
            }
            byte[] swap = current;
            current = next;
            next = swap;
            currentLength = nextLength;
        }
        data.flush();
    }

    /**
     * Decrypts everything from {@code in} to {@code out}. Neither stream is closed. Each chunk is verified before
     * it is written, but a later chunk may still fail: use {@link #decryptFile} to never leave partial output.
     */
    public void decrypt(InputStream in, OutputStream out) throws IOException {
        DataInputStream data = new DataInputStream(in);
        Header header = readHeader(data, STREAM_MAGIC);
        byte[] noncePrefix = readExactly(data, NONCE_PREFIX_BYTES);
        for (int counter = 0; ; counter++) {
            int lengthField;
            try {
                lengthField = data.readInt();
            } catch (EOFException e) {
                throw CryptoException.decryptionFailed(e);
            }
            boolean last = (lengthField & LAST_CHUNK_FLAG) != 0;
            int length = lengthField & ~LAST_CHUNK_FLAG;
            if (length < TAG_BYTES || length > CHUNK_SIZE + TAG_BYTES) {
                throw CryptoException.decryptionFailed(null);
            }
            byte[] sealed = readExactly(data, length);
            out.write(open(header.key, chunkIv(noncePrefix, counter), sealed, header.bytes, chunkData(counter, last)));
            if (last) {
                break;
            }
        }
        if (data.read() != -1) {
            throw CryptoException.decryptionFailed(null);
        }
        out.flush();
    }

    /** Encrypts a file. The target is replaced only once encryption has completed. */
    public void encryptFile(Path source, Path target) throws IOException {
        transform(source, target, true);
    }

    /** Decrypts a file. If decryption fails, the target is left untouched: no partial plaintext is written. */
    public void decryptFile(Path source, Path target) throws IOException {
        transform(source, target, false);
    }

    /** Whether the file starts like a file written by {@link #encryptFile}. Does not check the key. */
    public static boolean isEncryptedFile(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] magic = new byte[STREAM_MAGIC.length];
            return readUpTo(in, magic) == magic.length && Arrays.equals(magic, STREAM_MAGIC);
        }
    }

    /** Wipes the copy of the password and the derived keys cache. The encryptor cannot be used afterwards. */
    public void destroy() {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
        derivedKeys.clear();
    }

    // ---- Internals -----------------------------------------------------------------------------------

    private void transform(Path source, Path target, boolean encrypt) throws IOException {
        Path directory = target.toAbsolutePath().getParent();
        Path temp = Files.createTempFile(directory, ".rapidswing-", ".tmp");
        try {
            try (InputStream in = new BufferedInputStream(Files.newInputStream(source));
                 OutputStream out = new BufferedOutputStream(Files.newOutputStream(temp))) {
                if (encrypt) {
                    encrypt(in, out);
                } else {
                    decrypt(in, out);
                }
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private byte[] header(byte[] magic) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        try {
            out.write(magic);
            if (password == null) {
                out.writeByte(MODE_KEY);
            } else {
                out.writeByte(MODE_PASSWORD);
                out.write(salt);
                out.writeInt(iterations);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    private Header readHeader(DataInputStream in, byte[] expectedMagic) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        byte[] magic = readExactly(in, expectedMagic.length);
        if (!Arrays.equals(magic, expectedMagic)) {
            throw new CryptoException("This is not data encrypted by RapidSwing in the expected format");
        }
        raw.write(magic);
        int mode = in.read();
        raw.write(mode);
        if (mode == MODE_KEY) {
            if (password != null) {
                throw new CryptoException("This data was encrypted with a key, not a password");
            }
            return new Header(key, raw.toByteArray());
        }
        if (mode != MODE_PASSWORD) {
            throw CryptoException.decryptionFailed(null);
        }
        if (password == null) {
            throw new CryptoException("This data was encrypted with a password, not a key");
        }
        byte[] dataSalt = readExactly(in, Keys.SALT_BYTES);
        int dataIterations = in.readInt();
        if (dataIterations < 1 || dataIterations > Keys.MAX_ITERATIONS) {
            throw CryptoException.decryptionFailed(null);
        }
        raw.write(dataSalt);
        new DataOutputStream(raw).writeInt(dataIterations);
        SecretKey derived = derivedKeys.computeIfAbsent(cacheKey(dataSalt, dataIterations),
                cacheKey -> Keys.derive(password, dataSalt, dataIterations));
        return new Header(derived, raw.toByteArray());
    }

    private static byte[] seal(SecretKey key, byte[] iv, byte[] plaintext, int offset, int length, byte[] header,
                               byte[] associatedData) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BYTES * 8, iv));
            cipher.updateAAD(header);
            cipher.updateAAD(associatedData);
            return cipher.doFinal(plaintext, offset, length);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Encryption failed", e);
        }
    }

    private static byte[] open(SecretKey key, byte[] iv, byte[] sealed, byte[] header, byte[] associatedData) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BYTES * 8, iv));
            cipher.updateAAD(header);
            cipher.updateAAD(associatedData);
            return cipher.doFinal(sealed);
        } catch (GeneralSecurityException e) {
            // Usually an AEADBadTagException: wrong key or modified data.
            throw CryptoException.decryptionFailed(e);
        }
    }

    private static byte[] chunkIv(byte[] noncePrefix, int counter) {
        return ByteBuffer.allocate(IV_BYTES).put(noncePrefix).putInt(counter).array();
    }

    private static byte[] chunkData(int counter, boolean last) {
        return ByteBuffer.allocate(5).putInt(counter).put((byte) (last ? 1 : 0)).array();
    }

    private static String cacheKey(byte[] salt, int iterations) {
        return Base64.getEncoder().encodeToString(salt) + ":" + iterations;
    }

    private static byte[] readExactly(DataInputStream in, int length) throws IOException {
        byte[] bytes = new byte[length];
        try {
            in.readFully(bytes);
        } catch (EOFException e) {
            throw CryptoException.decryptionFailed(e);
        }
        return bytes;
    }

    private static int readUpTo(InputStream in, byte[] buffer) throws IOException {
        int total = 0;
        while (total < buffer.length) {
            int read = in.read(buffer, total, buffer.length - total);
            if (read < 0) {
                break;
            }
            total += read;
        }
        return total;
    }

    private static final class Header {
        final SecretKey key;
        final byte[] bytes;

        Header(SecretKey key, byte[] bytes) {
            this.key = key;
            this.bytes = bytes;
        }
    }
}
