package com.rapidswing.testing;

import com.rapidswing.app.AppContext;
import com.rapidswing.i18n.I18n;
import com.rapidswing.security.User;
import com.rapidswing.ui.AbstractView;
import com.rapidswing.ui.AppAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.swing.JButton;
import javax.swing.JLabel;
import java.awt.FlowLayout;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppHarnessTest {

    /** A small view using the context like a real one: a task, a confirmation and the status bar. */
    static final class GreetingView extends AbstractView {
        GreetingView(AppContext ctx) {
            super("Greeting");
            setLayout(new FlowLayout());
            JLabel greeting = new JLabel("Loading...");
            add(greeting);
            add(new JButton(AppAction.of("Load", () -> ctx.tasks()
                    .call(() -> "Hello, " + ctx.session().getUser().getDisplayName())
                    .onSuccess(greeting::setText)
                    .start())));
            add(new JButton(AppAction.of("Reset", () -> {
                if (ctx.dialogs().confirm("Reset the greeting?")) {
                    greeting.setText("");
                    ctx.mainFrame().statusBar().setMessage("Reset");
                }
            })));
        }
    }

    @RegisterExtension
    final AppHarnessExtension harness = new AppHarnessExtension();

    @Test
    void drivesARealViewWithItsContext() {
        AppHarness app = harness.get().signIn(User.of("ada", "Ada", "admin"));
        app.show("greeting", GreetingView::new);

        Ui.click(app.find().button("Load"));
        assertTrue(app.find().containsText("Hello, Ada"));

        app.dialogs().answerConfirm(true);
        Ui.click(app.find().button("Reset"));
        assertEquals("Reset", app.status());
        assertEquals("confirm: Reset the greeting?", app.dialogs().lastMessage());
        app.dialogs().assertNoErrors();
    }

    @Test
    void restrictedViewsAreRefusedLikeInTheApp() {
        AppHarness app = harness.get().signIn(User.of("val", "Val", "viewer"));
        AppContext ctx = app.context();
        Ui.onEdt(() -> ctx.views()
                .register("admin", () -> new GreetingView(ctx))
                .restrict("admin", ctx.session().anyRole("admin")));

        AssertionError refused = assertThrows(AssertionError.class, () -> app.show("admin", GreetingView::new));

        assertTrue(refused.getMessage().contains("warn: You do not have permission to open this view."));
    }

    @Test
    void localeIsRestoredOnClose() {
        Locale before = I18n.global().getLocale();
        AppHarness app = AppHarness.create().locale(Locale.GERMAN);
        assertEquals("Suchen", I18n.global().get("grid.search"));

        app.close();
        app.close();
        assertEquals(before, I18n.global().getLocale());
    }
}
