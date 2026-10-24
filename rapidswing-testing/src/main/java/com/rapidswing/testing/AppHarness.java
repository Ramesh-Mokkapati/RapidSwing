package com.rapidswing.testing;

import com.rapidswing.app.AppConfig;
import com.rapidswing.app.AppContext;
import com.rapidswing.i18n.I18n;
import com.rapidswing.security.User;
import com.rapidswing.ui.MainFrame;
import com.rapidswing.ui.View;
import com.rapidswing.util.Edt;
import org.junit.jupiter.api.Assumptions;

import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * A complete {@link AppContext} for testing real views and screens: a main window that is never shown,
 * {@link TestTasks#immediate() immediate tasks}, {@link ScriptedDialogs scripted dialogs} and
 * {@link MemoryPreferences in-memory preferences}.
 * <pre>
 * AppHarness app = AppHarness.create()
 *         .service(CustomerService.class, new FakeCustomerService())
 *         .signIn(User.of("ada", "Ada", "admin"));
 * CrudScreen&lt;Customer&gt; screen = app.show("customers", CustomersScreen::create);
 * ...
 * app.close();
 * </pre>
 * In JUnit tests, prefer {@link AppHarnessExtension}, which creates and closes a harness around each test.
 * <p>
 * Swing windows need a display, even when they are never shown. Without one (e.g. on CI without a virtual
 * display such as Xvfb), {@link #create()} skips the test instead of failing it.
 */
public final class AppHarness implements AutoCloseable {

    private final AppContext context;
    private final ScriptedDialogs dialogs;
    private final Locale originalLocale;
    private boolean closed;

    private AppHarness(AppContext context, ScriptedDialogs dialogs, Locale originalLocale) {
        this.context = context;
        this.dialogs = dialogs;
        this.originalLocale = originalLocale;
    }

    public static AppHarness create() {
        return create(new AppConfig());
    }

    public static AppHarness create(AppConfig config) {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
                "AppHarness needs a display; on CI, run the tests under a virtual display such as Xvfb");
        Locale originalLocale = I18n.global().getLocale();
        return Ui.onEdt(() -> {
            ScriptedDialogs dialogs = new ScriptedDialogs();
            MainFrame frame = new MainFrame(config.get("app.title", "Test"));
            AppContext context = new AppContext(config, frame, new MemoryPreferences(), TestTasks.immediate(), dialogs);
            I18n i18n = context.i18n();
            config.find("app.bundle").ifPresent(i18n::addBundle);
            // The same wiring SwingApp does at startup.
            context.tasks().setDefaultErrorHandler(e -> dialogs.error(i18n.get("app.operationFailed"), e));
            context.views().onAccessDenied(id -> dialogs.warn(i18n.get("security.accessDenied")));
            return new AppHarness(context, dialogs, originalLocale);
        });
    }

    /** Registers a service, typically a fake of a repository or remote service. */
    public <T> AppHarness service(Class<T> type, T instance) {
        context.services().register(type, instance);
        return this;
    }

    public AppHarness signIn(User user) {
        Ui.onEdt(() -> context.session().login(user));
        return this;
    }

    public AppHarness signOut() {
        Ui.onEdt(() -> context.session().logout());
        return this;
    }

    /** Switches the language for this test; {@link #close()} switches back. */
    public AppHarness locale(Locale locale) {
        Ui.onEdt(() -> context.i18n().setLocale(locale));
        return this;
    }

    /**
     * Registers the view (unless already registered) and shows it, like a user navigating to it, then lets
     * its loading finish. Returns the view, or fails if navigation was refused (e.g. access denied).
     */
    public <V extends View> V show(String id, Function<AppContext, V> factory) {
        Objects.requireNonNull(factory, "factory");
        View shown = Ui.onEdt(() -> {
            if (!context.views().isRegistered(id)) {
                context.views().register(id, () -> factory.apply(context));
            }
            return context.views().show(id) ? context.views().currentView().get() : null;
        });
        if (shown == null) {
            throw new AssertionError("Could not show view '" + id + "'. Dialogs: " + dialogs.messages());
        }
        @SuppressWarnings("unchecked")
        V view = (V) shown;
        return view;
    }

    /** Finds components in the main window's content: the current view, toolbar and status bar. */
    public Find find() {
        return Find.in(context.mainFrame().getContentPane());
    }

    /** A menu item, e.g. {@code menuItem("View", "Customers")}, as the user sees it (translated, role-filtered). */
    public JMenuItem menuItem(String menu, String item) {
        return Ui.onEdt(() -> {
            for (int i = 0; i < context.mainFrame().getJMenuBar().getMenuCount(); i++) {
                JMenu candidate = context.mainFrame().getJMenuBar().getMenu(i);
                if (candidate != null && menu.equals(candidate.getText())) {
                    for (Component child : candidate.getMenuComponents()) {
                        if (child instanceof JMenuItem && item.equals(((JMenuItem) child).getText())
                                && child.isVisible()) {
                            return (JMenuItem) child;
                        }
                    }
                    throw new AssertionError("Menu '" + menu + "' has no visible item '" + item + "'");
                }
            }
            throw new AssertionError("No menu '" + menu + "'");
        });
    }

    /** The status bar message. */
    public String status() {
        return Ui.onEdt(() -> context.mainFrame().statusBar().getMessage());
    }

    public AppContext context() {
        return context;
    }

    public ScriptedDialogs dialogs() {
        return dialogs;
    }

    public JComponent content() {
        return (JComponent) context.mainFrame().getContentPane();
    }

    /**
     * Discards the window, stops tasks and restores the language. Call it after each test, or let
     * {@link AppHarnessExtension} do it. Calling it again has no effect.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        Edt.runAndWait(() -> {
            context.views().reset();
            context.session().logout();
            context.i18n().setLocale(originalLocale);
            context.tasks().shutdown();
            context.mainFrame().dispose();
        });
    }
}
