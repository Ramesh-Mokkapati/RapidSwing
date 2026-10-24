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
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Current password, new password twice. Checks the length, the confirmation and that the password changes, then
 * calls the {@link PasswordChanger} in the background. Shown by {@link ChangePasswordDialog}.
 */
public class ChangePasswordPanel extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(ChangePasswordPanel.class);

    /** The minimum length of new passwords, unless configured otherwise. */
    public static final int DEFAULT_MIN_LENGTH = 8;

    private final String username;
    private final PasswordChanger changer;
    private final TaskService tasks;
    private final Runnable onChanged;
    private final Runnable onCancel;
    private final int minLength;

    private final JPasswordField current = new JPasswordField(20);
    private final JPasswordField newPassword = new JPasswordField(20);
    private final JPasswordField confirm = new JPasswordField(20);
    private final JTextArea message = new JTextArea(2, 1);
    private final AppAction changeAction;
    private final JButton changeButton;
    private boolean busy;
    private boolean cancelled;

    public ChangePasswordPanel(String username, PasswordChanger changer, TaskService tasks, int minLength,
                               Runnable onChanged, Runnable onCancel) {
        super(new GridBagLayout());
        this.username = Objects.requireNonNull(username, "username");
        this.changer = Objects.requireNonNull(changer, "changer");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.onChanged = Objects.requireNonNull(onChanged, "onChanged");
        this.onCancel = Objects.requireNonNull(onCancel, "onCancel");
        this.minLength = minLength;
        I18n i18n = I18n.global();

        changeAction = AppAction.of(i18n.get("password.change"), this::change);
        changeButton = new JButton(changeAction);
        AppAction cancelAction = AppAction.of(i18n.get("password.cancel"), this::cancel);
        for (JPasswordField field : new JPasswordField[]{current, newPassword, confirm}) {
            field.putClientProperty(FlatClientProperties.STYLE, "showRevealButton: true");
            field.addActionListener(e -> change());
        }
        Color error = UIManager.getColor("Component.error.focusedBorderColor");
        message.setForeground(error != null ? error : new Color(0xCC3333));
        message.setFont(UIManager.getFont("Label.font"));
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setEditable(false);
        message.setFocusable(false);
        message.setOpaque(false);
        message.setBorder(null);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(new JButton(cancelAction));
        buttons.add(changeButton);

        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        addRow(0, i18n.get("password.current"), current);
        addRow(1, i18n.get("password.new"), newPassword);
        addRow(2, i18n.get("password.confirm"), confirm);
        GridBagConstraints messageConstraints = constraints(0, 3, 2, new Insets(8, 0, 8, 0));
        messageConstraints.fill = GridBagConstraints.HORIZONTAL;
        add(message, messageConstraints);
        GridBagConstraints buttonConstraints = constraints(0, 4, 2, new Insets(8, 0, 0, 0));
        buttonConstraints.anchor = GridBagConstraints.LINE_END;
        buttonConstraints.fill = GridBagConstraints.NONE;
        add(buttons, buttonConstraints);
    }

    /** Checks the input and changes the password in the background. */
    public void change() {
        if (busy) {
            return;
        }
        char[] oldSecret = current.getPassword();
        char[] newSecret = newPassword.getPassword();
        char[] confirmation = confirm.getPassword();
        String problem = check(oldSecret, newSecret, confirmation);
        Arrays.fill(confirmation, '\0');
        if (problem != null) {
            clear(oldSecret, newSecret);
            showMessage(problem);
            return;
        }
        setBusy(true);
        showMessage("");
        tasks.call(() -> {
            changer.changePassword(username, oldSecret, newSecret);
            return Boolean.TRUE;
        }).onSuccess(ignored -> {
            if (!cancelled) {
                onChanged.run();
            }
        }).onError(this::failed).onFinally(() -> {
            clear(oldSecret, newSecret);
            setBusy(false);
        }).start();
    }

    public void cancel() {
        cancelled = true;
        onCancel.run();
    }

    public JPasswordField getCurrentField() {
        return current;
    }

    public JPasswordField getNewField() {
        return newPassword;
    }

    public JPasswordField getConfirmField() {
        return confirm;
    }

    public JButton getChangeButton() {
        return changeButton;
    }

    /** The error currently shown, or an empty string. */
    public String getMessage() {
        return message.getText().trim();
    }

    private String check(char[] oldSecret, char[] newSecret, char[] confirmation) {
        I18n i18n = I18n.global();
        if (oldSecret.length == 0 || newSecret.length == 0 || confirmation.length == 0) {
            return i18n.get("password.required");
        }
        if (newSecret.length < minLength) {
            return i18n.get("password.tooShort", minLength);
        }
        if (!Arrays.equals(newSecret, confirmation)) {
            return i18n.get("password.mismatch");
        }
        if (Arrays.equals(oldSecret, newSecret)) {
            return i18n.get("password.same");
        }
        return null;
    }

    private void failed(Throwable error) {
        if (cancelled) {
            return;
        }
        Throwable cause = error;
        if (cause instanceof AuthenticationException) {
            showMessage(I18n.global().get("password.wrong"));
            current.setText("");
            current.requestFocusInWindow();
        } else if (cause instanceof IllegalArgumentException && cause.getMessage() != null) {
            showMessage(cause.getMessage());
            newPassword.requestFocusInWindow();
        } else {
            log.warn("Password change failed", cause);
            showMessage(I18n.global().get("password.failed"));
        }
    }

    private void setBusy(boolean isBusy) {
        busy = isBusy;
        changeAction.setEnabled(!isBusy);
        current.setEnabled(!isBusy);
        newPassword.setEnabled(!isBusy);
        confirm.setEnabled(!isBusy);
    }

    private void showMessage(String text) {
        message.setText(text);
    }

    private void addRow(int row, String label, JPasswordField field) {
        JLabel jLabel = new JLabel(label);
        jLabel.setLabelFor(field);
        add(jLabel, constraints(0, row, 1, new Insets(4, 0, 4, 12)));
        add(field, constraints(1, row, 1, new Insets(4, 0, 4, 0)));
    }

    private static void clear(char[]... secrets) {
        for (char[] secret : secrets) {
            Arrays.fill(secret, '\0');
        }
    }

    private static GridBagConstraints constraints(int x, int y, int width, Insets insets) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.gridwidth = width;
        c.insets = insets;
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = x == 1 ? 1 : 0;
        return c;
    }
}
