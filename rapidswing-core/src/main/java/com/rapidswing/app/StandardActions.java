package com.rapidswing.app;

import com.rapidswing.i18n.I18n;
import com.rapidswing.property.Property;
import com.rapidswing.ui.AppAction;
import com.rapidswing.ui.DetachedWindow;
import com.rapidswing.ui.HelpService;
import com.rapidswing.ui.ShortcutsDialog;

/**
 * Ready-made menu actions for the services most applications offer, for Help and Window menus.
 * <pre>
 * frame.addMenu("Help", StandardActions.help(help, ctx), StandardActions.shortcuts(ctx), null,
 *         StandardActions.systemInfo(ctx));
 * </pre>
 * Their names are translated when created; rebuild menus in {@link SwingApp#onLocaleChanged} if the language
 * can change at run time.
 */
public final class StandardActions {

    private StandardActions() {
    }

    /** F1: help for the current view. */
    public static AppAction help(HelpService help, AppContext ctx) {
        return AppAction.of(text("help.contents"),
                () -> help.show(ctx.mainFrame(), ctx.views().currentId().get())).shortcut("F1");
    }

    public static AppAction shortcuts(AppContext ctx) {
        return AppAction.of(text("shortcuts.title") + "...", () -> ShortcutsDialog.show(ctx.mainFrame()));
    }

    public static AppAction systemInfo(AppContext ctx) {
        return AppAction.of(text("sysinfo.title") + "...", () -> SystemInfo.show(ctx));
    }

    /** Shown only while signed in through an authenticator that can change passwords. */
    public static AppAction changePassword(SwingApp app) {
        Property<Boolean> possible = new Property<>(app.canChangePassword());
        app.context().session().user().onChange(user -> possible.set(app.canChangePassword()));
        return AppAction.of(text("password.title") + "...", app::changePassword).requires(possible);
    }

    /** Opens the current view in a window of its own; enabled while a view is shown. */
    public static AppAction newWindow(AppContext ctx) {
        Property<Boolean> hasView = new Property<>(ctx.views().currentId().get() != null);
        ctx.views().currentId().onChange(id -> hasView.set(id != null));
        return AppAction.of(text("window.newWindow"), () -> {
            String id = ctx.views().currentId().get();
            if (id == null) {
                return;
            }
            try {
                DetachedWindow.open(ctx.views(), id, ctx.mainFrame());
            } catch (IllegalStateException e) {
                ctx.dialogs().warn(e.getMessage());
            }
        }).enabledWhen(hasView);
    }

    private static String text(String key) {
        return I18n.global().get(key);
    }
}
