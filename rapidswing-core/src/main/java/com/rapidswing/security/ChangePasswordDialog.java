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

/**
 * A modal dialog around {@link ChangePasswordPanel}. Enter changes the password, Escape or closing cancels.
 */
public final class ChangePasswordDialog {

    private ChangePasswordDialog() {
    }

    /** Shows the dialog and blocks until the password is changed (true) or the user cancels (false). */
    public static boolean show(Window owner, String username, PasswordChanger changer, TaskService tasks,
                               int minLength) {
        Edt.checkEdt();
        JDialog dialog = new JDialog(owner, I18n.global().get("password.title"), Dialog.ModalityType.APPLICATION_MODAL);
        boolean[] changed = new boolean[1];
        ChangePasswordPanel panel = new ChangePasswordPanel(username, changer, tasks, minLength, () -> {
            changed[0] = true;
            dialog.dispose();
        }, dialog::dispose);

        dialog.setContentPane(panel);
        dialog.getRootPane().setDefaultButton(panel.getChangeButton());
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("ESCAPE"), "rapidswing.password.cancel");
        dialog.getRootPane().getActionMap().put("rapidswing.password.cancel", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                panel.cancel();
            }
        });
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                panel.getCurrentField().requestFocusInWindow();
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
        return changed[0];
    }
}
