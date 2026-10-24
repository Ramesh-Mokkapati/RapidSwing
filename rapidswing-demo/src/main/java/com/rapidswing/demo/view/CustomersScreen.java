package com.rapidswing.demo.view;

import com.rapidswing.app.AppContext;
import com.rapidswing.crud.CrudScreen;
import com.rapidswing.demo.DemoSettings;
import com.rapidswing.demo.Roles;
import com.rapidswing.demo.model.Customer;
import com.rapidswing.demo.model.CustomerSelected;
import com.rapidswing.demo.model.CustomersChanged;
import com.rapidswing.demo.model.Order;
import com.rapidswing.demo.service.CustomerService;
import com.rapidswing.forms.Validators;
import com.rapidswing.i18n.I18n;
import com.rapidswing.security.Session;
import com.rapidswing.table.CellStyle;
import com.rapidswing.table.RowAction;
import com.rapidswing.ui.AppAction;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.print.PrinterException;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * The whole customer screen, defined declaratively with {@link CrudScreen}: search with operators, paging,
 * row actions, styled cells, comparing, printing and the customer's orders as a detail table.
 */
public final class CustomersScreen {

    private CustomersScreen() {
    }

    public static CrudScreen<Customer> create(AppContext ctx) {
        I18n i18n = ctx.i18n();
        // Views are rebuilt for each sign-in, so permissions can be decided once here.
        Session session = ctx.session();
        boolean canChange = session.hasAnyRole(Roles.ADMIN, Roles.SALES);
        // Balances are in US dollars whatever the language; dates follow the user's language (via Formats).
        NumberFormat money = NumberFormat.getCurrencyInstance(Locale.US);
        DemoSettings settings = ctx.services().find(DemoSettings.class).orElse(null);
        int pageSize = settings != null ? settings.pageSize().get() : 10;
        BigDecimal large = new BigDecimal(settings != null ? settings.largeBalance().get() : 20_000);
        CustomerService service = ctx.service(CustomerService.class);

        CrudScreen<Customer> screen = CrudScreen.builder(Customer.class)
                .context(ctx)
                .names(i18n.get("customer.singular"), i18n.get("customer.plural"))
                .repository(service)
                .newItem(Customer::new)
                .copy(Customer::new)
                .itemName(Customer::getName)
                .allowCreate(canChange)
                .allowEdit(canChange)
                .allowDelete(session.hasRole(Roles.ADMIN))
                .search(panel -> panel
                        .field("name", i18n.get("customer.name"), String.class)
                        .field("city", i18n.get("customer.city"), String.class)
                        .field("balance", i18n.get("customer.balance"), BigDecimal.class)
                        .field("active", i18n.get("customer.active"), Boolean.class))
                .pageSize(pageSize)
                .refreshOn(ctx.events(), CustomersChanged.class)
                .columns(table -> table
                        .column(i18n.get("customer.id"), Integer.class, Customer::getId).field("id").width(50)
                        .column(i18n.get("customer.name"), String.class, Customer::getName).field("name").width(150)
                        .column(i18n.get("customer.email"), String.class, Customer::getEmail).field("email").width(210)
                        .column(i18n.get("customer.city"), String.class, Customer::getCity).field("city").width(100)
                        .column(i18n.get("customer.balance"), BigDecimal.class, Customer::getBalance).field("balance")
                            .format(money::format).width(110)
                            .style(balance -> balance.compareTo(large) >= 0 ? CellStyle.SUCCESS.textOnly().bold() : null)
                        .column(i18n.get("customer.since"), LocalDate.class, Customer::getSince).field("since").hidden()
                        .column(i18n.get("customer.active"), Boolean.class, Customer::isActive).field("active").width(60))
                .form((form, edit) -> form
                        .text(i18n.get("customer.name"), edit.property(Customer::getName, Customer::setName))
                            .required().validate(Validators.maxLength(80))
                        .text(i18n.get("customer.email"), edit.property(Customer::getEmail, Customer::setEmail))
                            .required().validate(Validators.email())
                        .text(i18n.get("customer.city"), edit.property(Customer::getCity, Customer::setCity))
                        .decimal(i18n.get("customer.balance"), edit.property(Customer::getBalance, Customer::setBalance))
                            .required()
                            .warn(balance -> balance != null && balance.signum() < 0 ? i18n.get("customer.negative") : null)
                        .date(i18n.get("customer.since"), edit.property(Customer::getSince, Customer::setSince))
                            .required()
                            .validate(Validators.of(d -> !d.isAfter(LocalDate.now()), i18n.get("customer.notInFuture")))
                        .checkbox(i18n.get("customer.active"), edit.property(Customer::isActive, Customer::setActive)))
                .details(i18n.get("order.plural"), Order.class, table -> table
                        .column(i18n.get("order.number"), String.class, Order::getNumber).width(110)
                        .column(i18n.get("order.date"), LocalDate.class, Order::getDate).width(100)
                        .column(i18n.get("order.amount"), BigDecimal.class, Order::getAmount).format(money::format)
                        .column(i18n.get("order.status"), Order.Status.class, Order::getStatus)
                            .style(status -> status == Order.Status.OVERDUE ? CellStyle.ERROR.textOnly().bold() : null),
                        service::ordersOf)
                .build();

        // Inactive customers recede; column styles (large balances) are drawn on top.
        screen.getGrid().rowStyle(customer -> customer.isActive() ? null : CellStyle.MUTED);
        if (canChange) {
            screen.getGrid().setConfirmHandler((question, title) -> ctx.dialogs().confirm(question));
            screen.getGrid().rowAction(i18n.get("action.deactivate"), rows -> deactivate(ctx, screen, rows))
                    .when(Customer::isActive)
                    .confirm(rows -> i18n.get("customer.confirmDeactivate", rows.size()));
            screen.getGrid().addAction(AppAction.of(i18n.get("action.exportCsv"), () -> exportCsv(ctx, screen)));
        }
        screen.getGrid().addCompareAction();
        screen.getGrid().rowAction(i18n.get("action.print"), rows -> print(ctx, screen))
                .selection(RowAction.Selection.ANY).menuOnly();
        screen.getGrid().selectedItem().onChange(customer -> {
            if (customer != null) {
                ctx.events().publish(new CustomerSelected(customer));
            }
        });
        return screen;
    }

    /** Saves each selected customer as inactive in the background, then reloads the page. */
    private static void deactivate(AppContext ctx, CrudScreen<Customer> screen, List<Customer> rows) {
        CustomerService service = ctx.service(CustomerService.class);
        ctx.tasks().run(() -> {
            for (Customer customer : rows) {
                Customer changed = new Customer(customer);
                changed.setActive(false);
                try {
                    service.save(changed);
                } catch (Exception e) {
                    throw new IllegalStateException(e.getMessage(), e);
                }
            }
        }).onSuccess(ignored -> {
            ctx.mainFrame().statusBar().setMessage(ctx.i18n().get("status.deactivated", rows.size()));
            screen.refresh();
        }).start();
    }

    private static void print(AppContext ctx, CrudScreen<Customer> screen) {
        try {
            screen.getGrid().print(ctx.i18n().get("customer.plural"));
        } catch (PrinterException e) {
            ctx.dialogs().error(ctx.i18n().get("grid.printFailed", e.getMessage()), e);
        }
    }

    private static void exportCsv(AppContext ctx, CrudScreen<Customer> screen) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("CSV", "csv"));
        chooser.setSelectedFile(new File("customers.csv"));
        if (chooser.showSaveDialog(screen) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try {
            screen.getGrid().exportCsv(file.toPath());
            ctx.mainFrame().statusBar().setMessage(
                    ctx.i18n().get("status.exported", screen.getGrid().visibleRows().size(), file));
        } catch (IOException e) {
            ctx.dialogs().error(ctx.i18n().get("error.export", file), e);
        }
    }
}
