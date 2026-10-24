package com.rapidswing.demo.view;

import com.rapidswing.app.AppConfig;
import com.rapidswing.crud.CrudScreen;
import com.rapidswing.demo.Roles;
import com.rapidswing.demo.model.Customer;
import com.rapidswing.demo.service.CustomerService;
import com.rapidswing.security.User;
import com.rapidswing.testing.AppHarness;
import com.rapidswing.testing.AppHarnessExtension;
import com.rapidswing.testing.Find;
import com.rapidswing.testing.Ui;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The real customers screen, driven like a user would, for each role. */
class CustomersScreenTest {

    /** Fast, in-memory customers: no artificial delays. */
    static final class FakeCustomerService implements CustomerService {
        final List<Customer> stored = new ArrayList<>();

        FakeCustomerService() {
            stored.add(customer(1, "Asha Rao", "Hyderabad"));
            stored.add(customer(2, "Liam Carter", "London"));
        }

        @Override
        public List<Customer> findAll() {
            List<Customer> copies = new ArrayList<>();
            stored.forEach(customer -> copies.add(new Customer(customer)));
            return copies;
        }

        @Override
        public Customer save(Customer customer) {
            Customer copy = new Customer(customer);
            if (copy.getId() == 0) {
                copy.setId(stored.size() + 1);
                stored.add(copy);
            } else {
                stored.set(stored.indexOf(copy), copy);
            }
            return new Customer(copy);
        }

        @Override
        public void delete(Customer customer) {
            stored.remove(customer);
        }

        private static Customer customer(int id, String name, String city) {
            return new Customer(id, name, name.toLowerCase(Locale.ROOT).replace(' ', '.') + "@example.com", city,
                    new BigDecimal("100.00"), LocalDate.of(2020, 1, 1), true);
        }
    }

    private final FakeCustomerService customers = new FakeCustomerService();

    @RegisterExtension
    final AppHarnessExtension harness = new AppHarnessExtension(new AppConfig().set("app.bundle", "messages"),
            app -> app.locale(Locale.ENGLISH).service(CustomerService.class, customers));

    private AppHarness app() {
        return harness.get();
    }

    private CrudScreen<Customer> openAs(String username, String... roles) {
        app().signIn(User.of(username, username, roles));
        return app().show("customers", CustomersScreen::create);
    }

    private static void select(CrudScreen<Customer> screen, int row) {
        Ui.onEdt(() -> screen.getGrid().select(screen.getGrid().getModel().getRow(row)));
    }

    @Test
    void viewerSeesCustomersReadOnly() {
        CrudScreen<Customer> screen = openAs("val", Roles.VIEWER);
        Find find = Find.in(screen);

        assertEquals(2, find.table().getRowCount());
        select(screen, 0);

        assertFalse(find.field("Name").isEditable());
        assertThrows(AssertionError.class, () -> find.button("New"));
        assertThrows(AssertionError.class, () -> find.button("Save"));
        assertThrows(AssertionError.class, () -> find.button("Export CSV"));
    }

    @Test
    void salesCanEditButNotDelete() {
        CrudScreen<Customer> screen = openAs("sam", Roles.SALES);
        Find find = Find.in(screen);
        select(screen, 0);

        Ui.type(find.field("City"), "Pune");
        Ui.click(find.button("Save"));

        assertEquals("Pune", customers.stored.get(0).getCity());
        assertEquals("Asha Rao saved", app().status());
        assertThrows(AssertionError.class, () -> find.button("Delete"));
        app().dialogs().assertNoErrors();
    }

    @Test
    void adminDeletesAfterConfirming() {
        CrudScreen<Customer> screen = openAs("ada", Roles.ADMIN, Roles.SALES);
        select(screen, 0);

        app().dialogs().answerConfirm(true);
        Ui.click(Find.in(screen).button("Delete"));

        assertEquals("confirm: Delete Asha Rao?", app().dialogs().lastMessage());
        assertEquals(1, customers.stored.size());
        assertEquals(1, Find.in(screen).table().getRowCount());
    }

    @Test
    void newCustomerIsValidatedInTheUsersLanguage() {
        app().locale(Locale.GERMAN);
        CrudScreen<Customer> screen = openAs("sam", Roles.SALES);
        Find find = Find.in(screen);

        Ui.click(find.button("Neu"));
        Ui.type(find.field("Name"), "Mei Tanaka");
        Ui.type(find.field("E-Mail"), "keine-adresse");
        Ui.click(find.button("Speichern"));

        assertEquals("Geben Sie eine gültige E-Mail-Adresse ein", screen.getForm().errorText("E-Mail"));
        assertEquals("Bitte korrigieren Sie die markierten Felder", app().status());
        assertEquals(2, customers.stored.size());
        assertTrue(find.containsText("Neu: Kunde"));
    }
}
