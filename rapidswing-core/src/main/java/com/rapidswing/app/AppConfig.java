package com.rapidswing.app;

import com.rapidswing.crypto.Encryptor;
import com.rapidswing.crypto.Keys;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Application settings. {@link #load(String...)} reads {@code /app.properties} from the classpath and then
 * applies command-line overrides of the form {@code --key=value} (a bare {@code --flag} means {@code true}).
 * <p>
 * Secrets can be stored encrypted as {@code db.password=ENC(...)} (create the value with
 * {@code java -cp rapidswing-core.jar com.rapidswing.crypto.CryptoTool encrypt}). They are decrypted when read,
 * with the Base64 AES-256 key from the {@code APP_ENCRYPTION_KEY} environment variable or the
 * {@code app.encryption.key} system property, or with {@link #decryptWith}.
 */
public class AppConfig {

    public static final String DEFAULT_RESOURCE = "/app.properties";
    public static final String KEY_ENVIRONMENT_VARIABLE = "APP_ENCRYPTION_KEY";
    public static final String KEY_SYSTEM_PROPERTY = "app.encryption.key";

    private final Properties properties = new Properties();
    private Encryptor decryptor;

    public static AppConfig load(String... args) {
        AppConfig config = new AppConfig()
                .loadResource(DEFAULT_RESOURCE)
                .applyArgs(args);
        String key = System.getProperty(KEY_SYSTEM_PROPERTY, System.getenv(KEY_ENVIRONMENT_VARIABLE));
        if (key != null && !key.trim().isEmpty()) {
            config.decryptWith(Encryptor.withKey(Keys.fromBase64(key)));
        }
        return config;
    }

    /** Loads a UTF-8 properties file from the classpath. A missing resource is ignored. */
    public AppConfig loadResource(String resource) {
        try (InputStream in = AppConfig.class.getResourceAsStream(resource)) {
            if (in != null) {
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + resource, e);
        }
        return this;
    }

    public AppConfig applyArgs(String... args) {
        if (args == null) {
            return this;
        }
        for (String arg : args) {
            if (arg == null || !arg.startsWith("--")) {
                continue;
            }
            String option = arg.substring(2);
            int eq = option.indexOf('=');
            if (eq > 0) {
                set(option.substring(0, eq), option.substring(eq + 1));
            } else if (!option.isEmpty()) {
                set(option, "true");
            }
        }
        return this;
    }

    public AppConfig set(String key, String value) {
        properties.setProperty(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
        return this;
    }

    /** Decrypts values written as {@code ENC(...)}. */
    public AppConfig decryptWith(Encryptor encryptor) {
        this.decryptor = Objects.requireNonNull(encryptor, "encryptor");
        return this;
    }

    public Optional<String> find(String key) {
        return Optional.ofNullable(value(key));
    }

    public String get(String key, String defaultValue) {
        String value = value(key);
        return value != null ? value : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        String value = value(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Config '" + key + "' is not an integer: " + value, e);
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = value(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    private String value(String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("ENC(") || !trimmed.endsWith(")")) {
            return value;
        }
        if (decryptor == null) {
            throw new IllegalStateException("Config '" + key + "' is encrypted, but no key is configured: set the "
                    + KEY_ENVIRONMENT_VARIABLE + " environment variable");
        }
        return decryptor.decryptToString(trimmed.substring(4, trimmed.length() - 1));
    }
}
