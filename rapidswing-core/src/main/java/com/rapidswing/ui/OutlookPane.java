package com.rapidswing.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.rapidswing.property.Property;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An Outlook-style layout: a navigation pane on the left with section buttons at the bottom (Mail, Calendar,
 * Contacts...) and the selected section's folders on top, and the section's content on the right.
 * <pre>
 * OutlookPane outlook = new OutlookPane();
 * outlook.addSection("mail", "Mail", mailIcon, folderTree,
 *         OutlookPane.withReadingPane(messageList, messagePreview, true));
 * outlook.addSection("contacts", "Contacts", null, contactGroups, contactGrid);
 * outlook.showSection("mail");
 * </pre>
 */
public class OutlookPane extends JPanel {

    private final CardLayout navigatorCards = new CardLayout();
    private final JPanel navigators = new JPanel(navigatorCards);
    private final CardLayout contentCards = new CardLayout();
    private final JPanel contents = new JPanel(contentCards);
    private final JPanel sectionButtons = new JPanel();
    private final ButtonGroup buttonGroup = new ButtonGroup();
    private final Map<String, JToggleButton> buttons = new LinkedHashMap<>();
    private final Property<String> currentSection = new Property<>();
    private final JSplitPane split;

    public OutlookPane() {
        super(new BorderLayout());
        sectionButtons.setLayout(new BoxLayout(sectionButtons, BoxLayout.Y_AXIS));

        JPanel navigation = new JPanel(new BorderLayout());
        navigation.add(navigators, BorderLayout.CENTER);
        navigation.add(sectionButtons, BorderLayout.SOUTH);
        navigation.setMinimumSize(new Dimension(160, 0));
        navigation.setPreferredSize(new Dimension(220, 0));

        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navigation, contents);
        split.setBorder(null);
        split.setResizeWeight(0);
        add(split, BorderLayout.CENTER);
    }

    /**
     * Adds a section.
     *
     * @param navigator what appears in the navigation pane for this section, e.g. a folder tree; may be {@code null}
     * @param content   the section's main area
     */
    public OutlookPane addSection(String id, String title, Icon icon, JComponent navigator, JComponent content) {
        Objects.requireNonNull(id, "id");
        if (buttons.containsKey(id)) {
            throw new IllegalArgumentException("A section with id '" + id + "' already exists");
        }
        JPanel navigatorPanel = new JPanel(new BorderLayout());
        JLabel heading = new JLabel(title);
        heading.putClientProperty(FlatClientProperties.STYLE_CLASS, "h3");
        heading.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        navigatorPanel.add(heading, BorderLayout.NORTH);
        if (navigator != null) {
            JScrollPane scroll = new JScrollPane(navigator);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            navigatorPanel.add(scroll, BorderLayout.CENTER);
        }
        navigators.add(navigatorPanel, id);
        contents.add(content, id);

        JToggleButton button = new JToggleButton(title, icon);
        button.setHorizontalAlignment(SwingConstants.LEADING);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height + 10));
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TAB);
        button.setFocusable(false);
        button.addActionListener(e -> showSection(id));
        Color line = UIManager.getColor("Separator.foreground");
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, line != null ? line : Color.GRAY),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        buttonGroup.add(button);
        sectionButtons.add(button);
        buttons.put(id, button);

        if (currentSection.get() == null) {
            showSection(id);
        }
        return this;
    }

    public void showSection(String id) {
        JToggleButton button = buttons.get(id);
        if (button == null) {
            throw new IllegalArgumentException("No section with id '" + id + "'");
        }
        button.setSelected(true);
        navigatorCards.show(navigators, id);
        contentCards.show(contents, id);
        currentSection.set(id);
    }

    public Property<String> currentSection() {
        return currentSection;
    }

    /** Width of the navigation pane in pixels. */
    public void setNavigationWidth(int width) {
        split.setDividerLocation(width);
    }

    /**
     * A list with a reading pane, like Outlook's message list and preview.
     *
     * @param right true for the reading pane on the right, false for below the list
     */
    public static JSplitPane withReadingPane(JComponent list, JComponent readingPane, boolean right) {
        JSplitPane pane = new JSplitPane(right ? JSplitPane.HORIZONTAL_SPLIT : JSplitPane.VERTICAL_SPLIT,
                list, readingPane);
        pane.setResizeWeight(right ? 0.45 : 0.5);
        pane.setBorder(null);
        return pane;
    }
}
