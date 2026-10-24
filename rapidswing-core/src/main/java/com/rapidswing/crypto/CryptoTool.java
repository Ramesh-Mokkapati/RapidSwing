package com.rapidswing.crypto;

import com.rapidswing.app.AppConfig;

import java.io.Console;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Command-line helper for keys, encrypted config values and encrypted files. Secrets are read without echo.
 * <pre>
 * java -cp rapidswing-core.jar com.rapidswing.crypto.CryptoTool genkey
 * java -cp rapidswing-core.jar com.rapidswing.crypto.CryptoTool encrypt            (uses APP_ENCRYPTION_KEY)
 * java -cp rapidswing-core.jar com.rapidswing.crypto.CryptoTool encrypt-file in out (asks for a password)
 * java -cp rapidswing-core.jar com.rapidswing.crypto.CryptoTool decrypt-file in out
 * </pre>
 */
public final class CryptoTool {

    private CryptoTool() {
    }

    public static void main(String[] args) throws Exception {
        String command = args.length > 0 ? args[0] : "";
        switch (command) {
            case "genkey":
                System.out.println(Keys.toBase64(Keys.generate()));
                break;
            case "encrypt":
                encryptConfigValue();
                break;
            case "encrypt-file":
            case "decrypt-file":
                if (args.length != 3) {
                    usage();
                    return;
                }
                transformFile(command.equals("encrypt-file"), args[1], args[2]);
                break;
            default:
                usage();
        }
    }

    private static void encryptConfigValue() {
        String key = System.getenv(AppConfig.KEY_ENVIRONMENT_VARIABLE);
        if (key == null || key.trim().isEmpty()) {
            fail("Set " + AppConfig.KEY_ENVIRONMENT_VARIABLE + " to a key from 'genkey' first.");
        }
        char[] value = console().readPassword("Value to encrypt: ");
        try {
            String encrypted = Encryptor.withKey(Keys.fromBase64(key)).encryptToString(new String(value));
            System.out.println("ENC(" + encrypted + ")");
        } finally {
            Arrays.fill(value, '\0');
        }
    }

    private static void transformFile(boolean encrypt, String source, String target) throws Exception {
        Console console = console();
        char[] password = console.readPassword("Password: ");
        Encryptor encryptor = null;
        try {
            if (encrypt) {
                char[] repeated = console.readPassword("Repeat password: ");
                boolean same = Arrays.equals(password, repeated);
                Arrays.fill(repeated, '\0');
                if (!same) {
                    fail("The passwords do not match.");
                }
            }
            encryptor = Encryptor.withPassword(password);
            if (encrypt) {
                encryptor.encryptFile(Paths.get(source), Paths.get(target));
            } else {
                encryptor.decryptFile(Paths.get(source), Paths.get(target));
            }
            System.out.println((encrypt ? "Encrypted " : "Decrypted ") + source + " to " + target);
        } finally {
            Arrays.fill(password, '\0');
            if (encryptor != null) {
                encryptor.destroy();
            }
        }
    }

    private static Console console() {
        Console console = System.console();
        if (console == null) {
            fail("Run this from a terminal, so secrets can be read without echo.");
        }
        return console;
    }

    private static void usage() {
        System.err.println("Usage: CryptoTool genkey | encrypt | encrypt-file <in> <out> | decrypt-file <in> <out>");
        System.exit(2);
    }

    private static void fail(String message) {
        System.err.println(message);
        System.exit(1);
    }
}
