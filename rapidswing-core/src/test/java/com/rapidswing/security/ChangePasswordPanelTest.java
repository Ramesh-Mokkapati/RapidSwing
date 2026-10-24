package com.rapidswing.security;

import com.rapidswing.task.TaskService;
import com.rapidswing.util.Edt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePasswordPanelTest {

    private SimpleAuthenticator accounts;
    private ChangePasswordPanel panel;
    private int changes;
    private boolean serverDown;

    @BeforeEach
    void setUp() {
        accounts = new SimpleAuthenticator()
                .addUser("ada", "Ada", PasswordHasher.hash("old-secret".toCharArray(), 1000), "admin");
        PasswordChanger changer = (username, current, next) -> {
            if (serverDown) {
                throw new IllegalStateException("Connection refused");
            }
            accounts.changePassword(username, current, next);
        };
        onEdt(() -> panel = new ChangePasswordPanel("ada", changer,
                new TaskService(new LoginPanelTest.DirectExecutor()), 8, () -> changes++, () -> { }));
    }

    private static void onEdt(Runnable code) {
        Edt.runAndWait(code);
        Edt.runAndWait(() -> { });
    }

    private void attempt(String current, String next, String confirmation) {
        onEdt(() -> {
            panel.getCurrentField().setText(current);
            panel.getNewField().setText(next);
            panel.getConfirmField().setText(confirmation);
            panel.change();
        });
    }

    @Test
    void changesThePassword() throws Exception {
        attempt("old-secret", "new-secret", "new-secret");

        assertEquals(1, changes);
        assertEquals("ada", accounts.authenticate("ada", "new-secret".toCharArray()).getUsername());
        assertThrows(AuthenticationException.class, () -> accounts.authenticate("ada", "old-secret".toCharArray()));
    }

    @Test
    void checksTheInputBeforeAsking() {
        attempt("old-secret", "short", "short");
        assertEquals("The new password needs at least 8 characters.", panel.getMessage());
        attempt("old-secret", "new-secret", "new-secrets");
        assertEquals("The new passwords do not match.", panel.getMessage());
        attempt("old-secret", "old-secret", "old-secret");
        assertEquals("The new password must differ from the current one.", panel.getMessage());
        attempt("", "new-secret", "new-secret");
        assertEquals("Please fill in all fields.", panel.getMessage());
        assertEquals(0, changes);
    }

    @Test
    void reportsAWrongCurrentPasswordAndOutages() {
        attempt("guess", "new-secret", "new-secret");
        assertEquals("The current password is wrong.", panel.getMessage());
        assertEquals("", new String(panel.getCurrentField().getPassword()));

        serverDown = true;
        attempt("old-secret", "new-secret", "new-secret");
        assertEquals("The password could not be changed. Please try again later.", panel.getMessage());
        assertTrue(changes == 0);
    }
}
