package com.rapidswing.app;

import com.rapidswing.i18n.I18n;
import com.rapidswing.security.Authenticator;
import com.rapidswing.security.ChangePasswordDialog;
import com.rapidswing.security.ChangePasswordPanel;
import com.rapidswing.security.LoginDialog;
import com.rapidswing.security.PasswordChanger;
import com.rapidswing.security.User;
import com.rapidswing.ui.ErrorMessages;
import com.rapidswing.ui.MainFrame;
import com.rapidswing.ui.Themes;
import com.rapidswing.ui.View;
import com.rapidswing.util.Edt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Locale;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * Base class for a RapidSwing application. Subclass it, override the lifecycle hooks and call
 * {@link #launch(String...)} from {@code main}.
 * <p>
 * Startup order, all on the EDT: load config, apply the locale, install theme, create the main window,
 * {@link #onStartup}, {@link #buildUi}, restore window state, sign in (if {@link #authenticator} returns one),
 * show window, {@link #onReady}, then {@link #onLogin} when signed in.
 * <p>
 * Supported config keys: {@code app.id}, {@code app.title}, {@code app.theme}, {@code app.width}, {@code app.height},
 * {@code app.bundle} (base name of the app's resource bundle) and {@code app.locale} (a language tag such as
 * {@code de} or {@code es-MX}; a language the user picked with {@link #switchLocale} takes precedence),
 * {@code app.version} (shown in {@link SystemInfo}) and {@code security.password.minLength} (default 8).
 */
public abstract class SwingApp {

    private static final Logger log = LoggerFactory.getLogger(SwingApp.class);
    private static final String MAIN_WINDOW = "main";
    private static final String LOCALE_PREFERENCE = "locale";
    private static final String USERNAME_PREFERENCE = "login.username";

    private AppContext context;
    private Authenticator authenticator;

    public final void launch(String... args) {
        AppConfig config = AppConfig.load(args);
        SwingUtilities.invokeLater(() -> {
            try {
                start(config);
            } catch (Throwable t) {
                log.error("Application failed to start", t);
                JOptionPane.showMessageDialog(null, "The application failed to start:\n" + t,
                        "Startup error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }

    /** Called before the UI is built. Register services here. */
    protected void onStartup(AppContext ctx) {
    }

    /** Register views, menus and toolbar actions on {@code ctx.mainFrame()}. */
    protected abstract void buildUi(AppContext ctx);

    /** Called once the main window is visible. Without sign-in, a good place to show the first view. */
    protected void onReady(AppContext ctx) {
    }

    /**
     * Return an authenticator to require sign-in before the main window is shown. The user then appears in
     * {@code ctx.session()}, and actions and views can be restricted to roles. Returns {@code null} by default.
     */
    protected Authenticator authenticator(AppContext ctx) {
        return null;
    }

    /** Called after every successful sign-in, with the main window visible. Show the first view here. */
    protected void onLogin(AppContext ctx, User user) {
    }

    /**
     * Called when the user signs out, before views are discarded, e.g. to end a server session. The user is
     * still in {@code ctx.session()}.
     */
    protected void onLogout(AppContext ctx, User user) {
    }

    /**
     * Called for failed background tasks and uncaught exceptions, on the thread they happened on. Return true
     * if you handled the error (e.g. reconnected, or signed out after a session timeout); the default returns
     * false, and an error dialog with a message from {@link ErrorMessages} is shown.
     */
    protected boolean onRuntimeError(AppContext ctx, Throwable error) {
        return false;
    }

    /** Return false to cancel exiting. By default asks the current view whether it can be left. */
    protected boolean canExit(AppContext ctx) {
        View current = ctx.views().currentView().get();
        return current == null || current.canLeave();
    }

    /** Called just before the application exits. Release resources here. */
    protected void onShutdown(AppContext ctx) {
    }

    /** Runs the shutdown sequence and exits the JVM, unless {@link #canExit} refuses. */
    public final void exit() {
        Edt.checkEdt();
        if (!canExit(context)) {
            return;
        }
        try {
            onShutdown(context);
        } catch (RuntimeException e) {
            log.error("Error during shutdown", e);
        }
        context.windowState().save(MAIN_WINDOW, context.mainFrame());
        context.tasks().shutdown();
        context.mainFrame().dispose();
        System.exit(0);
    }

    /**
     * Signs the current user out: views are discarded, the main window is hidden, and the sign-in dialog is
     * shown again. Cancelling it exits the application. Returns false if the current view refused to be left.
     */
    public final boolean logout() {
        Edt.checkEdt();
        if (authenticator == null) {
            throw new IllegalStateException("Sign-in is not enabled: authenticator() returned null");
        }
        View current = context.views().currentView().get();
        if (current != null && !current.canLeave()) {
            return false;
        }
        User leaving = context.session().getUser();
        if (leaving != null) {
            try {
                onLogout(context, leaving);
            } catch (RuntimeException e) {
                log.error("Error while signing out", e);
            }
        }
        context.views().reset();
        context.session().logout();
        context.mainFrame().setVisible(false);
        Optional<User> user = signIn();
        if (!user.isPresent()) {
            exit();
            return false;
        }
        context.mainFrame().setVisible(true);
        onLogin(context, user.get());
        return true;
    }

    /** Whether the sign-in can change passwords: the authenticator implements {@link PasswordChanger}. */
    public final boolean canChangePassword() {
        return authenticator instanceof PasswordChanger && context.session().isLoggedIn();
    }

    /** Shows the change-password dialog for the signed-in user. Returns true if the password was changed. */
    public final boolean changePassword() {
        Edt.checkEdt();
        if (!canChangePassword()) {
            context.dialogs().warn(context.i18n().get("password.unsupported"));
            return false;
        }
        boolean changed = ChangePasswordDialog.show(context.mainFrame(), context.session().getUser().getUsername(),
                (PasswordChanger) authenticator, context.tasks(),
                context.config().getInt("security.password.minLength", ChangePasswordPanel.DEFAULT_MIN_LENGTH));
        if (changed) {
            context.dialogs().info(context.i18n().get("password.changed"));
        }
        return changed;
    }

    /**
     * Switches the application's language: framework and bound texts update, views are rebuilt, and the
     * choice is remembered for the next start. Returns false if the current view refused to be left.
     */
    public final boolean switchLocale(Locale locale) {
        Edt.checkEdt();
        View current = context.views().currentView().get();
        if (current != null && !current.canLeave()) {
            return false;
        }
        applyLocale(locale);
        context.mainFrame().setLocale(locale);
        context.preferences().put(LOCALE_PREFERENCE, locale.toLanguageTag());
        context.views().reload();
        onLocaleChanged(context);
        return true;
    }

    /** Called after {@link #switchLocale}, for texts that are neither bound nor part of a view. */
    protected void onLocaleChanged(AppContext ctx) {
    }

    public final AppContext context() {
        return context;
    }

    private void start(AppConfig config) {
        String appId = config.get("app.id", getClass().getName());
        config.find("app.bundle").ifPresent(I18n.global()::addBundle);
        applyLocale(startupLocale(config, AppContext.preferencesFor(appId)));
        Themes.install(config.get("app.theme", Themes.LIGHT));

        MainFrame frame = new MainFrame(config.get("app.title", getClass().getSimpleName()));
        context = new AppContext(config, frame, appId);

        context.tasks().setDefaultErrorHandler(e -> {
            log.error("Background task failed", e);
            if (!handled(e)) {
                context.dialogs().error(context.i18n().get("app.operationFailed"), e);
            }
        });
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            log.error("Uncaught exception on thread '{}'", thread.getName(), e);
            if (!handled(e)) {
                context.dialogs().error(context.i18n().get("app.unexpectedError"), e);
            }
        });

        context.views().onAccessDenied(id -> context.dialogs().warn(context.i18n().get("security.accessDenied")));

        onStartup(context);
        buildUi(context);

        frame.statusBar().bindBusy(context.tasks().runningCount());
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exit();
            }
        });
        if (!context.windowState().restore(MAIN_WINDOW, frame)) {
            frame.setSize(config.getInt("app.width", 1024), config.getInt("app.height", 700));
            frame.setLocationRelativeTo(null);
        }

        authenticator = authenticator(context);
        Optional<User> user = Optional.empty();
        if (authenticator != null) {
            user = signIn();
            if (!user.isPresent()) {
                context.tasks().shutdown();
                frame.dispose();
                System.exit(0);
                return;
            }
        }
        frame.setVisible(true);

        onReady(context);
        user.ifPresent(signedIn -> onLogin(context, signedIn));
    }

    private boolean handled(Throwable error) {
        try {
            return onRuntimeError(context, error);
        } catch (RuntimeException e) {
            log.error("Error in onRuntimeError", e);
            return false;
        }
    }

    /** Shows the sign-in dialog; on success, records the user in the session and remembers the user name. */
    private Optional<User> signIn() {
        String title = context.mainFrame().getTitle();
        String lastUsername = context.preferences().get(USERNAME_PREFERENCE, null);
        Optional<User> user = LoginDialog.show(null, title, authenticator, context.tasks(), lastUsername);
        user.ifPresent(signedIn -> {
            context.preferences().put(USERNAME_PREFERENCE, signedIn.getUsername());
            context.session().login(signedIn);
        });
        return user;
    }

    private static Locale startupLocale(AppConfig config, Preferences preferences) {
        String tag = preferences.get(LOCALE_PREFERENCE, null);
        if (tag == null) {
            tag = config.get("app.locale", null);
        }
        return tag == null ? Locale.getDefault() : Locale.forLanguageTag(tag);
    }

    /** Makes the locale the default for formatting, Swing's own texts (e.g. Yes/No buttons) and I18n. */
    private static void applyLocale(Locale locale) {
        Locale.setDefault(locale);
        JComponent.setDefaultLocale(locale);
        I18n.global().setLocale(locale);
    }
}
