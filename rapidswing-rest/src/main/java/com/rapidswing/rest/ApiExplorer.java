package com.rapidswing.rest;

import com.formdev.flatlaf.FlatClientProperties;
import com.rapidswing.app.AppContext;
import com.rapidswing.crud.CrudScreen;
import com.rapidswing.ui.AbstractView;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Browse and edit a whole REST API without writing code: the API's resources on the left, a CRUD screen for
 * the selected one on the right, all built at runtime from its OpenAPI document.
 * <pre>
 * RestClient client = new RestClient("http://localhost:8080");
 * ctx.tasks().call(() -&gt; OpenApiSpec.fetch(client))
 *         .onSuccess(spec -&gt; ctx.views().register("api", () -&gt; new ApiExplorer(ctx, spec, client)))
 *         .start();
 * </pre>
 */
public class ApiExplorer extends AbstractView {

    private final AppContext ctx;
    private final OpenApiSpec spec;
    private final RestClient client;
    private final JList<ApiResource> resourceList;
    private final CardLayout cards = new CardLayout();
    private final JPanel screens = new JPanel(cards);
    private final Map<ApiResource, CrudScreen<ApiRecord>> created = new HashMap<>();
    private CrudScreen<ApiRecord> current;
    private ApiResource selected;

    public ApiExplorer(AppContext ctx, OpenApiSpec spec, RestClient client) {
        super(spec.getTitle());
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.spec = spec;
        this.client = Objects.requireNonNull(client, "client");

        DefaultListModel<ApiResource> resources = new DefaultListModel<>();
        spec.getResources().forEach(resources::addElement);
        resourceList = new JList<>(resources);
        resourceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resourceList.setCellRenderer((list, resource, index, isSelected, hasFocus) -> {
            JLabel label = new JLabel(resource.getTitle());
            label.setToolTipText(resource.getPath());
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            label.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });
        resourceList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                resourceSelected();
            }
        });

        JLabel heading = new JLabel(spec.getTitle() + (spec.getVersion().isEmpty() ? "" : " " + spec.getVersion()));
        heading.putClientProperty(FlatClientProperties.STYLE_CLASS, "h3");
        heading.setBorder(BorderFactory.createEmptyBorder(8, 10, 2, 10));
        JLabel server = new JLabel(client.getBaseUrl());
        server.setEnabled(false);
        server.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 10));
        JPanel header = new JPanel(new BorderLayout());
        header.add(heading, BorderLayout.NORTH);
        header.add(server, BorderLayout.SOUTH);

        JPanel navigation = new JPanel(new BorderLayout());
        navigation.add(header, BorderLayout.NORTH);
        navigation.add(new JScrollPane(resourceList), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navigation, screens);
        split.setDividerLocation(200);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        if (!resources.isEmpty()) {
            resourceList.setSelectedIndex(0);
        }
    }

    public OpenApiSpec getSpec() {
        return spec;
    }

    /** The screen of the selected resource, or {@code null}. */
    public CrudScreen<ApiRecord> getCurrentScreen() {
        return current;
    }

    /** Selects a resource by name, e.g. {@code customers}. Returns false if its screen refused to be left. */
    public boolean show(String resourceName) {
        resourceList.setSelectedValue(spec.resource(resourceName), true);
        return selected == spec.resource(resourceName);
    }

    @Override
    public boolean canLeave() {
        return current == null || current.canLeave();
    }

    private void resourceSelected() {
        ApiResource resource = resourceList.getSelectedValue();
        if (resource == null || resource == selected) {
            return;
        }
        if (current != null && !current.canLeave()) {
            resourceList.setSelectedValue(selected, true);
            return;
        }
        if (current != null) {
            current.onHide();
        }
        selected = resource;
        current = created.computeIfAbsent(resource, key -> {
            CrudScreen<ApiRecord> screen = ApiScreens.create(ctx, key, client);
            screens.add(screen, key.getName());
            return screen;
        });
        cards.show(screens, resource.getName());
        current.onShow();
    }
}
