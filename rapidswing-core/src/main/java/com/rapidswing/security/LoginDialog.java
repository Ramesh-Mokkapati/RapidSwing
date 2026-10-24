package com.rapidswing.security;

import com.rapidswing.i18n.I18n;
import com.rapidswing.task.TaskService;
import com.rapidswing.util.Edt;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import java.awt.Dialog;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Optional;

/**
 * A modal sign-in dialog around {@link LoginPanel}. Enter signs in, Escape or closing the window cancels.
 */
public final class LoginDialog {

    private LoginDialog() {
    }

    /**
     * Shows the dialog and blocks until the user signs in or cancels.
     *
     * @param owner         the window to center on, or {@code null} for a stand-alone dialog with its own
     *                      taskbar entry (used before the main window is shown)
     * @param lastUsername  pre-filled user name, or {@code null}
     * @return the signed-in user, or empty if the user cancelled
     */
    public static Optional<User> show(Window owner, String appName, Authenticator authenticator, TaskService tasks,
                                      String lastUsername) {
        Edt.checkEdt();
        JDialog dialog = new JDialog(owner, I18n.global().get("login.title"), Dialog.ModalityType.APPLICATION_MODAL);
        User[] result = new User[1];
        LoginPanel panel = new LoginPanel(appName, authenticator, tasks, user -> {
            result[0] = user;
            dialog.dispose();
        }, dialog::dispose, lastUsername);

        dialog.setContentPane(panel);
        dialog.getRootPane().setDefaultButton(panel.getSignInButton());
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("ESCAPE"), "rapidswing.login.cancel");
        dialog.getRootPane().getActionMap().put("rapidswing.login.cancel", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                panel.cancel();
            }
        });
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                panel.focusFirstField();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                panel.cancel();
            }
        });
        dialog.setResizable(false);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return Optional.ofNullable(result[0]);
    }
}
