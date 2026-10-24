package com.rapidswing.security;

import com.formdev.flatlaf.FlatClientProperties;
import com.rapidswing.i18n.I18n;
import com.rapidswing.task.TaskService;
import com.rapidswing.ui.AppAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The sign-in form: user name, password, error message, Sign in and Cancel. Shown by {@link LoginDialog};
 * usable on its own, e.g. embedded in a welcome screen.
 * <p>
 * Credentials are checked in the background. The password is cleared from the field and from memory after
 * every attempt. Failures never say whether the user name or the password was wrong. After a few failed
 * attempts, the next attempt is delayed for a growing number of seconds.
 */
public class LoginPanel extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(LoginPanel.class);

    /** Failed attempts allowed before a delay is added. */
    static final int FREE_ATTEMPTS = 2;
    private static final int DELAY_STEP_SECONDS = 5;
    private static final int MAX_DELAY_SECONDS = 60;

    private final Authenticator authenticator;
    private final TaskService tasks;
    private final Consumer<User> onSuccess;
    private final Runnable onCancel;

    private final JTextField username = new JTextField(20);
    private final JPasswordField password = new JPasswordField(20);
    /** Two wrapping lines, so long (e.g. translated) messages fit without resizing the dialog. */
    private final JTextArea message = new JTextArea(2, 1);
    private final AppAction signInAction;
    private final JButton signInButton;
    private final Timer cooldownTimer = new Timer(1000, e -> tick());

    private int failures;
    private int cooldownSeconds;
    private boolean busy;
    private boolean cancelled;

    public LoginPanel(String appName, Authenticator authenticator, TaskService tasks, Consumer<User> onSuccess,
                      Runnable onCancel, String initialUsername) {
        super(new GridBagLayout());
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.onSuccess = Objects.requireNonNull(onSuccess, "onSuccess");
        this.onCancel = Objects.requireNonNull(onCancel, "onCancel");
        I18n i18n = I18n.global();

        signInAction = AppAction.of(i18n.get("login.signIn"), this::signIn);
        signInButton = new JButton(signInAction);
        AppAction cancelAction = AppAction.of(i18n.get("login.cancel"), this::cancel);
        if (initialUsername != null) {
            username.setText(initialUsername);
        }
        password.putClientProperty(FlatClientProperties.STYLE, "showRevealButton: true");
        Color error = UIManager.getColor("Component.error.focusedBorderColor");
        message.setForeground(error != null ? error : new Color(0xCC3333));
        message.setFont(UIManager.getFont("Label.font"));
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setEditable(false);
        message.setFocusable(false);
        message.setOpaque(false);
        message.setBorder(null);

        JLabel heading = new JLabel(i18n.get("login.heading", appName));
        heading.putClientProperty(FlatClientProperties.STYLE_CLASS, "h2");
        JLabel usernameLabel = new JLabel(i18n.get("login.username"));
        usernameLabel.setLabelFor(username);
        JLabel passwordLabel = new JLabel(i18n.get("login.password"));
        passwordLabel.setLabelFor(password);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(new JButton(cancelAction));
        buttons.add(signInButton);

        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        add(heading, constraints(0, 0, 2, new Insets(0, 0, 16, 0)));
        add(usernameLabel, constraints(0, 1, 1, new Insets(4, 0, 4, 12)));
        add(username, constraints(1, 1, 1, new Insets(4, 0, 4, 0)));
        add(passwordLabel, constraints(0, 2, 1, new Insets(4, 0, 4, 12)));
        add(password, constraints(1, 2, 1, new Insets(4, 0, 4, 0)));
        GridBagConstraints messageConstraints = constraints(0, 3, 2, new Insets(8, 0, 8, 0));
        messageConstraints.fill = GridBagConstraints.HORIZONTAL;
        add(message, messageConstraints);
        GridBagConstraints buttonConstraints = constraints(0, 4, 2, new Insets(8, 0, 0, 0));
        buttonConstraints.anchor = GridBagConstraints.LINE_END;
        buttonConstraints.fill = GridBagConstraints.NONE;
        add(buttons, buttonConstraints);
    }

    /** Checks the entered credentials in the background. */
    public void signIn() {
        if (busy || cooldownSeconds > 0) {
            return;
        }
        String name = username.getText().trim();
        char[] secret = password.getPassword();
        if (name.isEmpty() || secret.length == 0) {
            Arrays.fill(secret, '\0');
            showMessage(I18n.global().get("login.required"));
            focusFirstField();
            return;
        }
        setBusy(true);
        showMessage("");
        tasks.call(() -> authenticator.authenticate(name, secret))
                .onSuccess(user -> {
                    if (user == null) {
                        failed(new AuthenticationException());
                    } else if (!cancelled) {
                        failures = 0;
                        password.setText("");
                        onSuccess.accept(user);
                    }
                })
                .onError(this::failed)
                .onFinally(() -> {
                    Arrays.fill(secret, '\0');
                    setBusy(false);
                })
                .start();
    }

    /** Gives up signing in; a result arriving later is ignored. */
    public void cancel() {
        cancelled = true;
        cooldownTimer.stop();
        onCancel.run();
    }

    /** Focuses the user name, or the password if a user name is already filled in. */
    public void focusFirstField() {
        (username.getText().trim().isEmpty() ? username : password).requestFocusInWindow();
    }

    public JButton getSignInButton() {
        return signInButton;
    }

    public JTextField getUsernameField() {
        return username;
    }

    public JPasswordField getPasswordField() {
        return password;
    }

    /** The error or hint currently shown, or an empty string. */
    public String getMessage() {
        return message.getText().trim();
    }

    @Override
    public void removeNotify() {
        cooldownTimer.stop();
        super.removeNotify();
    }

    private void failed(Throwable error) {
        if (cancelled) {
            return;
        }
        password.setText("");
        if (error instanceof AuthenticationException) {
            failures++;
            if (failures > FREE_ATTEMPTS) {
                startCooldown(Math.min((failures - FREE_ATTEMPTS) * DELAY_STEP_SECONDS, MAX_DELAY_SECONDS));
            } else {
                showMessage(I18n.global().get("login.failed"));
            }
        } else {
            log.warn("Sign-in failed", error);
            showMessage(I18n.global().get("login.unavailable"));
        }
        password.requestFocusInWindow();
    }

    private void startCooldown(int seconds) {
        cooldownSeconds = seconds;
        showMessage(I18n.global().get("login.wait", seconds));
        updateState();
        cooldownTimer.restart();
    }

    private void tick() {
        cooldownSeconds--;
        if (cooldownSeconds <= 0) {
            cooldownSeconds = 0;
            cooldownTimer.stop();
            showMessage(I18n.global().get("login.failed"));
        } else {
            showMessage(I18n.global().get("login.wait", cooldownSeconds));
        }
        updateState();
    }

    private void setBusy(boolean isBusy) {
        busy = isBusy;
        updateState();
    }

    private void updateState() {
        signInAction.setEnabled(!busy && cooldownSeconds == 0);
        username.setEnabled(!busy);
        password.setEnabled(!busy);
    }

    private void showMessage(String text) {
        message.setText(text);
    }

    private static GridBagConstraints constraints(int x, int y, int width, Insets insets) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.gridwidth = width;
        c.insets = insets;
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = x == 1 ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        c.weightx = x == 1 ? 1 : 0;
        return c;
    }
}
