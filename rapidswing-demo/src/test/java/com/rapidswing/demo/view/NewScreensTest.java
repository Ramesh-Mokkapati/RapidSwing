package com.rapidswing.demo.view;

import com.rapidswing.app.AppConfig;
import com.rapidswing.demo.Roles;
import com.rapidswing.demo.model.Customer;
import com.rapidswing.demo.service.CustomerService;
import com.rapidswing.forms.Wizard;
import com.rapidswing.security.User;
import com.rapidswing.testing.AppHarnessExtension;
import com.rapidswing.testing.Ui;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The terminal-style picking view, the import wizard and the dashboard. */
class NewScreensTest {

    private final CustomersScreenTest.FakeCustomerService customers = new CustomersScreenTest.FakeCustomerService();

    @RegisterExtension
    final AppHarnessExtension harness = new AppHarnessExtension(new AppConfig().set("app.bundle", "messages"),
            app -> app.locale(Locale.ENGLISH).service(CustomerService.class, customers));

    @Test
    void pickingWorksWithFunctionKeysOnly() {
        harness.get().signIn(User.of("sam", "Sam", Roles.SALES));
        PickingView picking = harness.get().show("picking", PickingView::new);

        assertFalse(Ui.onEdt(() -> picking.getKeys().press("F2")), "nothing to confirm yet");
        Ui.onEdt(() -> {
            picking.getScanField().setText("TL-1001");
            picking.getScanField().postActionEvent();
            picking.getScanField().setText("EL-0927");
            picking.getScanField().postActionEvent();
        });
        assertEquals(2, picking.pickedCount());
        Ui.onEdt(() -> picking.getKeys().press("F4"));
        assertEquals(1, picking.pickedCount());
        assertTrue(Ui.onEdt(() -> picking.getKeys().press("F2")));
        assertEquals("1 item confirmed", picking.getStatus());
    }

    @Test
    void importWizardSavesTheReviewedCustomers() {
        harness.get().signIn(User.of("ada", "Ada", Roles.ADMIN));
        Wizard wizard = Ui.onEdt(() -> ImportCustomersWizard.create(harness.get().context()));

        Ui.onEdt(wizard::next);
        assertEquals("review", wizard.currentPageId());
        Ui.onEdt(wizard::next);
        Ui.onEdt(wizard::finish);

        assertTrue(wizard.isFinished());
        assertEquals(5, customers.stored.size());
        Customer last = customers.stored.get(4);
        assertEquals("Yuki Mori", last.getName());
        assertTrue(last.isActive());
    }

    @Test
    void dashboardChartsSummarizeTheCustomers() {
        harness.get().signIn(User.of("val", "Val", Roles.VIEWER));
        DashboardView dashboard = harness.get().show("dashboard", DashboardView::new);
        Ui.flush();

        assertEquals(2, dashboard.getBalanceChart().getCategories().size());
        assertEquals(2, dashboard.getGrowthChart().getSeries().size());
        assertEquals(2, dashboard.getRegionChart().segments().size());
        // Asha in Hyderabad (Asia) and Liam in London (Europe) have 100 each.
        assertEquals("Asia", dashboard.getRegionChart().toTable().getValueAt(0, 0));
        assertEquals("100", dashboard.getRegionChart().toTable().getValueAt(0, 1));
    }
}
