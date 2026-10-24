package com.rapidswing.demo;

import com.rapidswing.app.AppContext;
import com.rapidswing.crypto.Encryptor;
import com.rapidswing.i18n.I18n;
import com.rapidswing.logviewer.LogViewer;
import com.rapidswing.rest.ApiExplorer;
import com.rapidswing.rest.OpenApiSpec;
import com.rapidswing.rest.RestClient;
import com.rapidswing.rest.sample.SampleApiServer;
import com.rapidswing.ui.AppAction;

import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/** The demo's Tools menu: log viewer, file encryption and the REST API explorer. */
final class Tools {

    private static SampleApiServer sampleApi;

    private Tools() {
    }

    /** The demo writes its log here, so the log viewer has something to show. */
    static Path logFile() {
        return Paths.get(System.getProperty("user.home"), ".rapidswing-demo", "rapidswing-demo.log");
    }

    /** Sends SLF4J Simple output to {@link #logFile()}. Must run before anything logs. */
    static void configureLogFile() {
        try {
            Files.createDirectories(logFile().getParent());
        } catch (IOException e) {
            return;
        }
        System.setProperty("org.slf4j.simpleLogger.logFile", logFile().toString());
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd HH:mm:ss.SSS");
    }

    static AppAction logViewer(AppContext ctx) {
        ctx.views().register("logs", () -> {
            LogViewer viewer = new LogViewer(ctx.tasks());
            viewer.open(logFile());
            return viewer;
        });
        return action(ctx, "action.logViewer", () -> ctx.views().show("logs"));
    }

    static AppAction encryptFile(AppContext ctx) {
        return action(ctx, "action.encryptFile", () -> transformFile(ctx, true));
    }

    static AppAction decryptFile(AppContext ctx) {
        return action(ctx, "action.decryptFile", () -> transformFile(ctx, false));
    }

    /** Starts the sample Spring-Boot-style API on first use and opens screens built from its OpenAPI document. */
    static AppAction apiExplorer(AppContext ctx) {
        return action(ctx, "action.apiExplorer", () -> {
            if (ctx.views().isRegistered("api")) {
                ctx.views().show("api");
                return;
            }
            I18n i18n = ctx.i18n();
            ctx.mainFrame().statusBar().setMessage(i18n.get("api.starting"));
            ctx.tasks().call(() -> {
                if (sampleApi == null) {
                    sampleApi = SampleApiServer.start(0);
                }
                RestClient client = new RestClient(sampleApi.getBaseUrl());
                return new Object[]{client, OpenApiSpec.fetch(client)};
            }).onSuccess(loaded -> {
                RestClient client = (RestClient) loaded[0];
                OpenApiSpec spec = (OpenApiSpec) loaded[1];
                ctx.views().register("api", () -> new ApiExplorer(ctx, spec, client));
                ctx.views().show("api");
                ctx.mainFrame().statusBar().setMessage(i18n.get("api.ready", spec.getTitle(), client.getBaseUrl()));
            }).start();
        });
    }

    static void shutdown() {
        if (sampleApi != null) {
            sampleApi.close();
        }
    }

    private static AppAction action(AppContext ctx, String key, Runnable handler) {
        AppAction action = AppAction.of(key, handler);
        ctx.i18n().bindAction(action, key);
        return action;
    }

    /** Encrypts or decrypts a file with a password (AES-256-GCM), in the background. */
    private static void transformFile(AppContext ctx, boolean encrypt) {
        I18n i18n = ctx.i18n();
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(ctx.mainFrame()) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File source = chooser.getSelectedFile();
        String name = source.getName();
        String suggested = encrypt ? name + ".enc"
                : name.endsWith(".enc") ? name.substring(0, name.length() - 4) : name + ".decrypted";
        chooser.setSelectedFile(new File(source.getParentFile(), suggested));
        if (chooser.showSaveDialog(ctx.mainFrame()) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = chooser.getSelectedFile();
        char[] password = askPassword(ctx, encrypt);
        if (password == null) {
            return;
        }
        ctx.tasks().call(() -> {
            Encryptor encryptor = Encryptor.withPassword(password);
            try {
                if (encrypt) {
                    encryptor.encryptFile(source.toPath(), target.toPath());
                } else {
                    encryptor.decryptFile(source.toPath(), target.toPath());
                }
            } finally {
                encryptor.destroy();
                Arrays.fill(password, '\0');
            }
            return target;
        }).onSuccess(file -> ctx.mainFrame().statusBar()
                .setMessage(i18n.get(encrypt ? "crypto.encrypted" : "crypto.decrypted", file.getName())))
                .onError(e -> ctx.dialogs().error(i18n.get("crypto.failed"), e))
                .start();
    }

    private static char[] askPassword(AppContext ctx, boolean confirm) {
        I18n i18n = ctx.i18n();
        JPasswordField first = new JPasswordField(20);
        JPasswordField second = new JPasswordField(20);
        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.add(new JLabel(i18n.get("crypto.password")));
        panel.add(first);
        if (confirm) {
            panel.add(new JLabel(i18n.get("crypto.repeat")));
            panel.add(second);
        }
        int answer = JOptionPane.showConfirmDialog(ctx.mainFrame(), panel,
                i18n.get(confirm ? "action.encryptFile" : "action.decryptFile"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return null;
        }
        char[] password = first.getPassword();
        if (confirm) {
            char[] repeated = second.getPassword();
            boolean same = Arrays.equals(password, repeated);
            Arrays.fill(repeated, '\0');
            if (!same) {
                Arrays.fill(password, '\0');
                ctx.dialogs().warn(i18n.get("crypto.mismatch"));
                return null;
            }
        }
        return password.length == 0 ? null : password;
    }
}
