package com.rapidswing.demo.view;

import com.rapidswing.app.AppContext;
import com.rapidswing.demo.model.Customer;
import com.rapidswing.demo.model.CustomersChanged;
import com.rapidswing.demo.service.CustomerService;
import com.rapidswing.forms.Outcome;
import com.rapidswing.forms.Wizard;
import com.rapidswing.i18n.I18n;
import com.rapidswing.table.BeanTableModel;
import com.rapidswing.table.DataGrid;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Imports customers from pasted lines in three pages: paste, review, options. Parsing and saving run in the
 * background with progress; lines with errors send the user back to the first page.
 */
public final class ImportCustomersWizard {

    static final String SAMPLE = "Ana Lima;ana.lima@example.com;Lisbon\n"
            + "Tom Becker;tom.becker@example.com;Hamburg\n"
            + "Yuki Mori;yuki.mori@example.com;Kyoto";

    private ImportCustomersWizard() {
    }

    public static Wizard create(AppContext ctx) {
        I18n i18n = ctx.i18n();
        JTextArea lines = new JTextArea(SAMPLE, 8, 50);
        JPanel pastePage = new JPanel(new BorderLayout(0, 8));
        pastePage.add(new JLabel(i18n.get("import.format")), BorderLayout.NORTH);
        pastePage.add(new JScrollPane(lines), BorderLayout.CENTER);

        List<Customer> parsed = new ArrayList<>();
        DataGrid<Customer> review = new DataGrid<>(BeanTableModel.builder(Customer.class)
                .column(i18n.get("customer.name"), String.class, Customer::getName)
                .column(i18n.get("customer.email"), String.class, Customer::getEmail)
                .column(i18n.get("customer.city"), String.class, Customer::getCity)
                .build());

        JCheckBox activate = new JCheckBox(i18n.get("import.activate"), true);
        JPanel optionsPage = new JPanel(new BorderLayout());
        optionsPage.add(activate, BorderLayout.NORTH);
        // Read by the background finish step, so the check box's state is kept where any thread may read it.
        AtomicBoolean activateImported = new AtomicBoolean(true);
        activate.addActionListener(e -> activateImported.set(activate.isSelected()));

        CustomerService service = ctx.service(CustomerService.class);
        return Wizard.builder(i18n.get("import.title"), ctx.tasks())
                .page("paste", i18n.get("import.paste"), i18n.get("import.pasteHint"), pastePage)
                    .validate(() -> lines.getText().trim().isEmpty() ? i18n.get("import.empty") : null)
                    .onNext(progress -> {
                        String[] rows = lines.getText().trim().split("\\R");
                        progress.begin(i18n.get("import.reading"), rows.length);
                        List<Customer> found = new ArrayList<>();
                        for (int i = 0; i < rows.length; i++) {
                            progress.checkCancelled();
                            String[] cells = rows[i].split(";");
                            if (cells.length != 3 || !cells[1].contains("@")) {
                                return Outcome.error(i18n.get("import.badLine", i + 1));
                            }
                            found.add(new Customer(0, cells[0].trim(), cells[1].trim().toLowerCase(Locale.ROOT),
                                    cells[2].trim(), BigDecimal.ZERO, LocalDate.now(), true));
                            progress.step(null);
                        }
                        synchronized (parsed) {
                            parsed.clear();
                            parsed.addAll(found);
                        }
                        return Outcome.next();
                    })
                .page("review", i18n.get("import.review"), i18n.get("import.reviewHint"), review)
                    .onEnter(() -> {
                        synchronized (parsed) {
                            review.setRows(new ArrayList<>(parsed));
                        }
                    })
                .page("options", i18n.get("import.options"), i18n.get("import.optionsHint"), optionsPage)
                .onFinish(progress -> {
                    progress.begin(i18n.get("import.saving"), parsed.size());
                    for (Customer customer : parsed) {
                        progress.checkCancelled();
                        customer.setActive(activateImported.get());
                        service.save(customer);
                        progress.step(customer.getName());
                    }
                    ctx.events().publish(new CustomersChanged());
                    return Outcome.next();
                })
                .build();
    }
}
