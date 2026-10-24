package com.rapidswing.security;

import com.rapidswing.task.TaskService;
import com.rapidswing.util.Edt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginPanelTest {

    /** Runs tasks immediately on the calling thread, so tests need no waiting. */
    static final class DirectExecutor extends AbstractExecutorService {
        private boolean shutdown;

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return new ArrayList<>();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }

    private final List<User> signedIn = new ArrayList<>();
    private final List<char[]> passwordsSeen = new ArrayList<>();
    private int cancels;
    private boolean serverDown;
    private LoginPanel panel;

    @BeforeEach
    void setUp() {
        SimpleAuthenticator accounts = new SimpleAuthenticator()
                .addUser("ada", "Ada", PasswordHasher.hash("right".toCharArray(), 1000), "admin");
        Authenticator authenticator = (username, password) -> {
            passwordsSeen.add(password);
            if (serverDown) {
                throw new IllegalStateException("Connection refused");
            }
            return accounts.authenticate(username, password);
        };
        onEdt(() -> panel = new LoginPanel("Demo", authenticator, new TaskService(new DirectExecutor()),
                signedIn::add, () -> cancels++, "ada"));
    }

    /** Runs the code on the EDT, then lets the task callbacks it queued run too. */
    private static void onEdt(Runnable code) {
        Edt.runAndWait(code);
        Edt.runAndWait(() -> { });
    }

    private void attempt(String password) {
        onEdt(() -> {
            panel.getPasswordField().setText(password);
            panel.signIn();
        });
    }

    @Test
    void correctPasswordSignsIn() {
        attempt("right");

        assertEquals(1, signedIn.size());
        assertEquals("ada", signedIn.get(0).getUsername());
        assertEquals("", new String(panel.getPasswordField().getPassword()));
    }

    @Test
    void passwordIsWipedFromMemoryAfterTheAttempt() {
        attempt("wrong");

        assertArrayEquals(new char[5], passwordsSeen.get(0));
    }

    @Test
    void failureDoesNotRevealWhichPartWasWrong() {
        attempt("wrong");
        String wrongPassword = panel.getMessage();
        onEdt(() -> panel.getUsernameField().setText("nobody"));
        attempt("right");

        assertEquals("The user name or password is incorrect.", wrongPassword);
        assertEquals(wrongPassword, panel.getMessage());
        assertTrue(signedIn.isEmpty());
        assertEquals("", new String(panel.getPasswordField().getPassword()));
    }

    @Test
    void repeatedFailuresAddADelay() {
        for (int i = 0; i < LoginPanel.FREE_ATTEMPTS; i++) {
            attempt("wrong");
            assertTrue(panel.getSignInButton().isEnabled());
        }
        attempt("wrong");

        assertFalse(panel.getSignInButton().isEnabled());
        assertEquals("Too many attempts. Please wait 5 seconds.", panel.getMessage());

        // Signing in is refused while the delay runs, even with the right password.
        attempt("right");
        assertTrue(signedIn.isEmpty());
        assertEquals(LoginPanel.FREE_ATTEMPTS + 1, passwordsSeen.size());
    }

    @Test
    void missingInputIsCheckedBeforeContactingTheAuthenticator() {
        attempt("");

        assertEquals("Enter your user name and password.", panel.getMessage());
        assertTrue(passwordsSeen.isEmpty());
    }

    @Test
    void unreachableServerIsReportedSeparately() {
        serverDown = true;
        attempt("right");

        assertEquals("Sign-in is not possible right now. Please try again later.", panel.getMessage());
        assertTrue(signedIn.isEmpty());
    }

    @Test
    void cancelIgnoresLateResults() {
        onEdt(() -> panel.cancel());
        attempt("right");

        assertEquals(1, cancels);
        assertTrue(signedIn.isEmpty());
    }
}
