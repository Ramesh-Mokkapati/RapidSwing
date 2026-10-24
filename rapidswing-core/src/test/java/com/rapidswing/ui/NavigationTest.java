package com.rapidswing.ui;

import com.rapidswing.i18n.I18n;
import com.rapidswing.property.Property;
import com.rapidswing.util.Edt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationTest {

    @AfterEach
    void resetLocale() {
        I18n.global().setLocale(Locale.ENGLISH);
    }

    private static ViewManager views(Property<Boolean> adminOnly) {
        return new ViewManager()
                .register("customers", () -> new AbstractView("Customers") { })
                .register("orders", () -> new AbstractView("Orders") { })
                .register("users", () -> new AbstractView("Users") { })
                .restrict("users", adminOnly);
    }

    @Test
    void treeHidesViewsTheUserMayNotOpenAndEmptyFolders() {
        Edt.runAndWait(() -> {
            Property<Boolean> admin = new Property<>(false);
            ViewManager views = views(admin);
            NavigationTree navigation = new NavigationTree(views);
            navigation.root()
                    .folder("sales", "Sales").view("customers", "Customers").view("orders", "Orders").end()
                    .folder("admin", "Administration").view("users", "Users");

            assertEquals(Arrays.asList("customers", "orders"), navigation.visibleViewIds());
            Object root = navigation.getTree().getModel().getRoot();
            assertEquals(1, navigation.getTree().getModel().getChildCount(root));

            admin.set(true);
            assertEquals(Arrays.asList("customers", "orders", "users"), navigation.visibleViewIds());
        });
    }

    @Test
    void treeFollowsTheCurrentViewAndSelectingShowsTheView() {
        Edt.runAndWait(() -> {
            ViewManager views = views(new Property<>(true));
            NavigationTree navigation = new NavigationTree(views);
            navigation.root().folder("sales", "Sales").view("customers", "Customers").view("orders", "Orders");

            views.show("orders");
            assertEquals("Orders", navigation.getTree().getSelectionPath().getLastPathComponent().toString());

            navigation.getTree().setSelectionRow(1);
            assertEquals("customers", views.currentId().get());
        });
    }

    @Test
    void folderPagesAreViewsOfTheirOwn() {
        Edt.runAndWait(() -> {
            ViewManager views = views(new Property<>(true));
            NavigationTree navigation = new NavigationTree(views);
            navigation.root().folder("sales", "Sales")
                    .page("<html><body><a href=\"view:orders\">Orders</a></body></html>")
                    .view("customers", "Customers");

            assertEquals(Arrays.asList("folder:sales", "customers"), navigation.visibleViewIds());
            views.show("folder:sales");
            HtmlView page = (HtmlView) views.currentView().get();
            page.follow("view:orders");
            assertEquals("orders", views.currentId().get());
        });
    }

    @Test
    void quickOpenFindsByPrefixThenSubstringThenWildcard() {
        Edt.runAndWait(() -> {
            ViewManager views = views(new Property<>(false));
            NavigationTree navigation = new NavigationTree(views);
            navigation.root().folder("sales", "Sales").view("customers", "Customers").view("orders", "Customer orders")
                    .end().folder("admin", "Administration").view("users", "Users");
            JMenuBar menuBar = new JMenuBar();
            JMenu file = new JMenu("File");
            AtomicInteger exported = new AtomicInteger();
            JMenuItem export = new JMenuItem("Export customers");
            export.addActionListener(e -> exported.incrementAndGet());
            JMenuItem disabled = new JMenuItem("Print");
            disabled.setEnabled(false);
            file.add(export);
            file.add(disabled);
            menuBar.add(file);

            List<CommandIndex.Command> commands = CommandIndex.fromNavigation(navigation, views);
            commands.addAll(CommandIndex.fromMenuBar(menuBar));
            CommandIndex index = new CommandIndex(commands);

            assertEquals(Arrays.asList("Customers", "Customer orders", "Export customers"), titles(index.search("cust")));
            assertEquals(Arrays.asList("Customers", "Customer orders"), titles(index.search("sales")));
            assertEquals(Collections.singletonList("Customer orders"), titles(index.search("c*ord*")));
            assertTrue(index.search("users").isEmpty());
            assertTrue(index.search("print").isEmpty());

            index.search("export").get(0).run();
            assertEquals(1, exported.get());
            index.search("customer o").get(0).run();
            assertEquals("orders", views.currentId().get());
        });
    }

    @Test
    void createMakesFreshInstancesAndRespectsAccess() {
        Edt.runAndWait(() -> {
            AtomicInteger denied = new AtomicInteger();
            ViewManager views = views(new Property<>(false));
            views.onAccessDenied(id -> denied.incrementAndGet());

            assertNotSame(views.create("customers"), views.create("customers"));
            assertNull(views.create("users"));
            assertEquals(1, denied.get());
        });
    }

    @Test
    void helpPagesArePickedPerLanguage() {
        HelpService help = new HelpService(null).register("customers", "/help/customers.html");

        assertTrue(help.html("customers").contains("Customers"));
        I18n.global().setLocale(Locale.GERMAN);
        assertTrue(help.html("customers").contains("Kunden"));
        I18n.global().setLocale(new Locale("es"));
        assertTrue(help.html("customers").contains("Customers"));
        assertTrue(help.html("orders").contains("no hay ayuda"));
        help.setDefault("/help/customers.html");
        assertTrue(help.html("orders").contains("Customers"));
    }

    @Test
    void shortcutsReadLikeKeys() {
        assertEquals("Ctrl+K", Shortcut.keyText(KeyStroke.getKeyStroke("ctrl K")));
        assertEquals("F1", Shortcut.keyText(KeyStroke.getKeyStroke("F1")));
        assertEquals("Ctrl+Shift+S", Shortcut.keyText(KeyStroke.getKeyStroke("ctrl shift S")));
    }

    private static List<String> titles(List<CommandIndex.Command> commands) {
        return commands.stream().map(CommandIndex.Command::getTitle).collect(Collectors.toList());
    }
}
