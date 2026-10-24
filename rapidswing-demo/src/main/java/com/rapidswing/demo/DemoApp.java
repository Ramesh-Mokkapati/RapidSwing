package com.rapidswing.demo;

import com.rapidswing.app.AppContext;
import com.rapidswing.app.StandardActions;
import com.rapidswing.app.SwingApp;
import com.rapidswing.app.SystemInfo;
import com.rapidswing.forms.SettingsDialog;
import com.rapidswing.demo.model.CustomerSelected;
import com.rapidswing.demo.service.CustomerService;
import com.rapidswing.demo.service.InMemoryCustomerService;
import com.rapidswing.demo.view.CustomersScreen;
import com.rapidswing.demo.view.DashboardView;
import com.rapidswing.demo.view.ImportCustomersWizard;
import com.rapidswing.demo.view.PickingView;
import com.rapidswing.demo.view.ProductsScreen;
import com.rapidswing.demo.view.ProfileView;
import com.rapidswing.i18n.I18n;
import com.rapidswing.security.Authenticator;
import com.rapidswing.security.Session;
import com.rapidswing.security.User;
import com.rapidswing.ui.AppAction;
import com.rapidswing.ui.ErrorMessages;
import com.rapidswing.ui.HelpService;
import com.rapidswing.ui.NavigationTree;
import com.rapidswing.ui.ShortcutsDialog;
import com.rapidswing.ui.MainFrame;
import com.rapidswing.ui.Themes;
import com.rapidswing.ui.ViewManager;
import org.slf4j.LoggerFactory;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.net.ConnectException;
import java.util.Map;

public class DemoApp extends SwingApp {

    /** Language names are shown in their own language, so users can always find theirs. */
    private static final Map<String, Locale> LANGUAGES = new LinkedHashMap<>();

    static {
        LANGUAGES.put("English", Locale.ENGLISH);
        LANGUAGES.put("Deutsch", Locale.GERMAN);
        LANGUAGES.put("Español", new Locale("es"));
    }

    private final Map<JRadioButtonMenuItem, Locale> languageItems = new LinkedHashMap<>();

    public static void main(String[] args) {
        Tools.configureLogFile();
        new DemoApp().launch(args);
    }

    @Override
    protected void onStartup(AppContext ctx) {
        ctx.services().registerLazy(CustomerService.class, InMemoryCustomerService::new);
        ctx.services().register(DemoSettings.class, new DemoSettings(ctx.preferences().node("settings")));
        // Errors users can act on get a message of their own instead of the exception's text.
        ErrorMessages.register(ConnectException.class, e -> ctx.i18n().get("error.noConnection"));
    }

    @Override
    protected Authenticator authenticator(AppContext ctx) {
        LoggerFactory.getLogger(DemoApp.class)
                .info("Demo accounts: admin/admin123, sales/sales123, viewer/viewer123");
        return Roles.demoAccounts();
    }

    @Override
    protected void buildUi(AppContext ctx) {
        MainFrame frame = ctx.mainFrame();
        ViewManager views = ctx.views();
        I18n i18n = ctx.i18n();
        Session session = ctx.session();

        views.register("dashboard", () -> new DashboardView(ctx))
             .register("customers", () -> CustomersScreen.create(ctx))
             .register("products", () -> ProductsScreen.create(ctx))
             .register("picking", () -> new PickingView(ctx))
             .register("profile", () -> new ProfileView(ctx))
             .restrict("profile", session.anyRole(Roles.ADMIN, Roles.SALES))
             .restrict("picking", session.anyRole(Roles.ADMIN, Roles.SALES))
             .restrict("api", session.anyRole(Roles.ADMIN, Roles.SALES));

        // A navigation tree of folders and views; titles are translation keys, so it follows the language.
        NavigationTree navigation = new NavigationTree(views);
        navigation.root()
                .view("dashboard", "action.dashboard")
                .folder("sales", "nav.sales").page("/pages/sales.html")
                    .view("customers", "action.customers")
                    .view("products", "nav.products")
                    .end()
                .folder("warehouse", "nav.warehouse")
                    .view("picking", "nav.picking")
                    .end()
                .view("profile", "action.profile");
        frame.setNavigator(navigation);
        frame.enableQuickOpen();
        HelpService help = new HelpService(views)
                .register("dashboard", "/help/dashboard.html")
                .register("customers", "/help/customers.html")
                .setDefault("/help/index.html");
        DemoSettings settings = ctx.service(DemoSettings.class);

        AppAction dashboard = action(i18n, "action.dashboard", () -> views.show("dashboard")).shortcut("ctrl 1");
        AppAction customers = action(i18n, "action.customers", () -> views.show("customers")).shortcut("ctrl 2");
        AppAction profile = action(i18n, "action.profile", () -> views.show("profile")).shortcut("ctrl 3")
                .requiresRole(session, Roles.ADMIN, Roles.SALES);
        AppAction back = action(i18n, "action.back", views::back).shortcut("alt LEFT").enabledWhen(views.canGoBack());
        AppAction toggleTheme = action(i18n, "action.toggleTheme",
                () -> Themes.switchTo(Themes.isDark() ? Themes.LIGHT : Themes.DARK)).shortcut("ctrl T");
        AppAction signOut = action(i18n, "action.signOut", () -> {
            if (!settings.confirmSignOut().get() || ctx.dialogs().confirm(i18n.get("confirm.signOut"))) {
                logout();
            }
        });
        AppAction changePassword = StandardActions.changePassword(this);
        i18n.bindAction(changePassword, "action.changePassword");
        AppAction settingsAction = action(i18n, "action.settings",
                () -> SettingsDialog.show(frame, settings.settings())).shortcut("ctrl COMMA");
        AppAction importCustomers = action(i18n, "action.importCustomers",
                () -> ImportCustomersWizard.create(ctx).showDialog(frame)).requiresRole(session, Roles.ADMIN, Roles.SALES);
        AppAction newWindow = StandardActions.newWindow(ctx).shortcut("ctrl shift N");
        i18n.bindAction(newWindow, "window.newWindow");
        AppAction helpAction = action(i18n, "help.contents",
                () -> help.show(frame, views.currentId().get())).shortcut("F1");
        AppAction shortcuts = action(i18n, "action.shortcuts", () -> ShortcutsDialog.show(frame));
        AppAction systemInfo = action(i18n, "action.systemInfo", () -> SystemInfo.show(ctx));
        AppAction exit = action(i18n, "action.exit", this::exit).shortcut("ctrl Q");
        AppAction apiExplorer = Tools.apiExplorer(ctx).requiresRole(session, Roles.ADMIN, Roles.SALES);
        AppAction about = action(i18n, "action.about",
                () -> ctx.dialogs().info(i18n.get("about.title"), i18n.get("about.text")));

        // Menus are created under stable ids first, then their visible names are bound to translations.
        frame.addMenu("file", changePassword, signOut, null, exit)
             .addMenu("view", dashboard, customers, profile, null, back, newWindow, null, toggleTheme, null)
             .addMenu("tools", importCustomers, null, Tools.logViewer(ctx), null, Tools.encryptFile(ctx),
                     Tools.decryptFile(ctx), null, apiExplorer, null, settingsAction)
             .addMenu("help", helpAction, shortcuts, null, systemInfo, about)
             .addToolbar(back, null, dashboard, customers, profile);
        JMenu languageMenu = languageMenu();
        frame.menu("view").add(languageMenu);

        i18n.bind(frame.menu("file")::setText, "menu.file");
        i18n.bind(frame.menu("view")::setText, "menu.view");
        i18n.bind(frame.menu("tools")::setText, "menu.tools");
        i18n.bind(frame.menu("help")::setText, "menu.help");
        i18n.bind(languageMenu::setText, "menu.language");
        i18n.bind(frame::setBaseTitle, "app.title");

        ctx.events().subscribeOnEdt(CustomerSelected.class,
                e -> frame.statusBar().setMessage(i18n.get("status.selected", e.getCustomer().getName())));
    }

    @Override
    protected void onLogin(AppContext ctx, User user) {
        DemoSettings settings = ctx.service(DemoSettings.class);
        ctx.views().show(settings.startView().get() == DemoSettings.StartView.CUSTOMERS ? "customers" : "dashboard");
        ctx.mainFrame().statusBar().setMessage(ctx.i18n().get("status.signedIn", user.getDisplayName()));
    }

    @Override
    protected void onShutdown(AppContext ctx) {
        Tools.shutdown();
    }

    @Override
    protected void onLocaleChanged(AppContext ctx) {
        ctx.mainFrame().statusBar().setMessage(ctx.i18n().get("status.ready"));
    }

    private JMenu languageMenu() {
        JMenu menu = new JMenu();
        ButtonGroup group = new ButtonGroup();
        for (Map.Entry<String, Locale> language : LANGUAGES.entrySet()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(language.getKey());
            item.addActionListener(e -> {
                if (!switchLocale(language.getValue())) {
                    selectCurrentLanguage();
                }
            });
            group.add(item);
            menu.add(item);
            languageItems.put(item, language.getValue());
        }
        selectCurrentLanguage();
        return menu;
    }

    private void selectCurrentLanguage() {
        String current = I18n.global().getLocale().getLanguage();
        for (Map.Entry<JRadioButtonMenuItem, Locale> entry : languageItems.entrySet()) {
            entry.getKey().setSelected(entry.getValue().getLanguage().equals(current));
        }
    }

    private static AppAction action(I18n i18n, String key, Runnable handler) {
        AppAction action = AppAction.of(key, handler);
        i18n.bindAction(action, key);
        return action;
    }
}
