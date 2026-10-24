package com.rapidswing.demo.view;

import com.formdev.flatlaf.FlatClientProperties;
import com.rapidswing.app.AppContext;
import com.rapidswing.chart.AbstractChart;
import com.rapidswing.chart.BarChart;
import com.rapidswing.chart.LineChart;
import com.rapidswing.chart.PieChart;
import com.rapidswing.demo.model.Customer;
import com.rapidswing.demo.model.CustomerSelected;
import com.rapidswing.demo.model.CustomersChanged;
import com.rapidswing.demo.service.CustomerService;
import com.rapidswing.i18n.I18n;
import com.rapidswing.security.User;
import com.rapidswing.ui.AbstractView;
import com.rapidswing.ui.AppAction;
import com.rapidswing.util.Subscription;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The start page: a greeting and three charts about the customers, each also available as a table. */
public class DashboardView extends AbstractView {

    /** Where the demo's cities are, for the share-by-region chart. */
    private static final Map<String, String> REGIONS = new LinkedHashMap<>();

    static {
        for (String city : Arrays.asList("Hyderabad", "Bengaluru", "Mumbai", "Osaka", "Tokyo", "Kyoto")) {
            REGIONS.put(city, "dashboard.region.asia");
        }
        for (String city : Arrays.asList("London", "Madrid", "Berlin", "Milan", "Paris", "Lisbon", "Hamburg")) {
            REGIONS.put(city, "dashboard.region.europe");
        }
        REGIONS.put("Toronto", "dashboard.region.americas");
        REGIONS.put("Dubai", "dashboard.region.middleEast");
    }

    private final AppContext ctx;
    private final JLabel lastSelected;
    private final BarChart balances = new BarChart();
    private final LineChart growth = new LineChart();
    private final PieChart regions = new PieChart();
    private final List<Runnable> tableRefreshers = new ArrayList<>();
    private Subscription selectionSubscription;
    private Subscription changeSubscription;

    public DashboardView(AppContext ctx) {
        super(ctx.i18n().get("dashboard.title"));
        this.ctx = ctx;
        I18n i18n = ctx.i18n();

        JLabel heading = new JLabel(i18n.get("dashboard.welcome"));
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, heading.getFont().getSize2D() * 1.8f));
        lastSelected = new JLabel(i18n.get("dashboard.noSelection"));

        JButton openCustomers = new JButton(AppAction.of(i18n.get("dashboard.openCustomers"),
                () -> ctx.views().show("customers")));

        JPanel intro = new JPanel();
        intro.setLayout(new BoxLayout(intro, BoxLayout.Y_AXIS));
        intro.add(heading);
        User user = ctx.session().getUser();
        if (user != null) {
            intro.add(Box.createVerticalStrut(4));
            intro.add(new JLabel(i18n.get("dashboard.signedIn", user.getDisplayName(),
                    String.join(", ", user.getRoles()))));
        }
        intro.add(Box.createVerticalStrut(12));
        intro.add(new JLabel(i18n.get("dashboard.hint")));
        intro.add(Box.createVerticalStrut(4));
        intro.add(lastSelected);
        intro.add(Box.createVerticalStrut(12));
        intro.add(openCustomers);
        intro.setAlignmentX(LEFT_ALIGNMENT);

        regions.otherLabel(i18n.get("dashboard.other"));
        JPanel charts = new JPanel(new GridLayout(1, 3, 16, 0));
        charts.add(card(i18n.get("dashboard.balanceByCity"), balances));
        charts.add(card(i18n.get("dashboard.growth"), growth));
        charts.add(card(i18n.get("dashboard.byRegion"), regions));

        JPanel content = new JPanel(new BorderLayout(0, 20));
        content.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        content.add(intro, BorderLayout.NORTH);
        content.add(charts, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
    }

    @Override
    public void onShow() {
        load();
    }

    /** Loads the customers in the background and fills the charts. */
    public void load() {
        CustomerService service = ctx.services().find(CustomerService.class).orElse(null);
        if (service == null) {
            return;
        }
        ctx.tasks().call(service::findAll).onSuccess(this::show).start();
    }

    /** Fills the charts from the customers. */
    void show(List<Customer> customers) {
        I18n i18n = ctx.i18n();
        Map<String, Double> byCity = new TreeMap<>();
        Map<Integer, int[]> byYear = new TreeMap<>();
        Map<String, Double> byRegion = new LinkedHashMap<>();
        for (String region : Arrays.asList("dashboard.region.asia", "dashboard.region.europe",
                "dashboard.region.americas", "dashboard.region.middleEast")) {
            byRegion.put(i18n.get(region), 0.0);
        }
        for (Customer customer : customers) {
            byCity.merge(customer.getCity(), customer.getBalance().doubleValue(), Double::sum);
            int[] counts = byYear.computeIfAbsent(customer.getSince().getYear(), year -> new int[2]);
            counts[0]++;
            if (customer.isActive()) {
                counts[1]++;
            }
            String region = REGIONS.get(customer.getCity());
            byRegion.merge(region != null ? i18n.get(region) : i18n.get("dashboard.other"),
                    customer.getBalance().doubleValue(), Double::sum);
        }

        // Largest balances first; the cities beyond eight are left to the table view of the customers screen.
        List<Map.Entry<String, Double>> cities = new ArrayList<>(byCity.entrySet());
        cities.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        cities = cities.subList(0, Math.min(8, cities.size()));
        balances.clearSeries();
        balances.categories(names(cities)).series(i18n.get("customer.balance"), values(cities));

        List<String> years = new ArrayList<>();
        double[] total = new double[byYear.size()];
        double[] active = new double[byYear.size()];
        int index = 0;
        int runningTotal = 0;
        int runningActive = 0;
        for (Map.Entry<Integer, int[]> year : byYear.entrySet()) {
            runningTotal += year.getValue()[0];
            runningActive += year.getValue()[1];
            years.add(String.valueOf(year.getKey()));
            total[index] = runningTotal;
            active[index] = runningActive;
            index++;
        }
        growth.clearSeries();
        growth.categories(years).series(i18n.get("dashboard.allCustomers"), total)
                .series(i18n.get("dashboard.activeCustomers"), active);

        List<Map.Entry<String, Double>> shares = new ArrayList<>(byRegion.entrySet());
        shares.removeIf(entry -> entry.getValue() == 0);
        regions.clearSeries();
        regions.categories(names(shares)).series(i18n.get("customer.balance"), values(shares));
        tableRefreshers.forEach(Runnable::run);
    }

    public BarChart getBalanceChart() {
        return balances;
    }

    public LineChart getGrowthChart() {
        return growth;
    }

    public PieChart getRegionChart() {
        return regions;
    }

    /** A titled chart with a toggle to see its numbers as a table. */
    private JPanel card(String title, AbstractChart chart) {
        JLabel label = new JLabel(title);
        label.putClientProperty(FlatClientProperties.STYLE_CLASS, "h4");
        JToggleButton asTable = new JToggleButton(ctx.i18n().get("dashboard.asTable"));
        asTable.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        CardLayout cards = new CardLayout();
        JPanel body = new JPanel(cards);
        JScrollPane table = new JScrollPane();
        body.add(chart, "chart");
        body.add(table, "table");
        Runnable refreshTable = () -> table.setViewportView(chart.toTable());
        tableRefreshers.add(refreshTable);
        asTable.addActionListener(e -> {
            refreshTable.run();
            cards.show(body, asTable.isSelected() ? "table" : "chart");
        });
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(label, BorderLayout.WEST);
        header.add(asTable, BorderLayout.EAST);
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        card.add(header, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private static List<String> names(List<Map.Entry<String, Double>> entries) {
        List<String> names = new ArrayList<>();
        entries.forEach(entry -> names.add(entry.getKey()));
        return names;
    }

    private static double[] values(List<Map.Entry<String, Double>> entries) {
        double[] values = new double[entries.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = entries.get(i).getValue();
        }
        return values;
    }

    /** Listens only while part of the window, so views discarded by a language switch are not kept alive. */
    @Override
    public void addNotify() {
        super.addNotify();
        if (selectionSubscription == null) {
            selectionSubscription = ctx.events().subscribeOnEdt(CustomerSelected.class, e -> lastSelected.setText(
                    ctx.i18n().get("dashboard.lastSelected", e.getCustomer().getName())));
            changeSubscription = ctx.events().subscribeOnEdt(CustomersChanged.class, e -> load());
        }
    }

    @Override
    public void removeNotify() {
        if (selectionSubscription != null) {
            selectionSubscription.unsubscribe();
            selectionSubscription = null;
            changeSubscription.unsubscribe();
            changeSubscription = null;
        }
        super.removeNotify();
    }
}
