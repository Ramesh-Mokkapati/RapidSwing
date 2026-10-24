package com.rapidswing.testing;

import com.rapidswing.util.Edt;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Finds components the way a user sees them: by button text and by the label next to a field, not by
 * field names or positions. Only components visible to the user are found (see {@link Ui#isVisibleToUser}).
 * <pre>
 * Find find = Find.in(view);
 * Ui.type(find.field("Email"), "asha@example.com");
 * Ui.click(find.button("Save"));
 * </pre>
 * Lookups fail with an {@link AssertionError} that lists what is there, so a failing test explains itself.
 */
public final class Find {

    private final Component root;

    private Find(Component root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    public static Find in(Component root) {
        return new Find(root);
    }

    /** All visible components of a type, in layout order. */
    public <T extends Component> List<T> all(Class<T> type) {
        List<T> found = new ArrayList<>();
        Edt.runAndWait(() -> collect(root, type, found));
        return found;
    }

    /** Exactly one visible component of the type that matches the condition. */
    public <T extends Component> T one(Class<T> type, Predicate<? super T> condition, String description) {
        List<T> matches = new ArrayList<>();
        for (T candidate : all(type)) {
            if (condition.test(candidate)) {
                matches.add(candidate);
            }
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }
        String problem = matches.isEmpty() ? "No " + description + " found." : "Several (" + matches.size()
                + ") components match " + description + ".";
        throw new AssertionError(problem + " Visible: " + inventory());
    }

    public JButton button(String text) {
        return one(JButton.class, button -> text.equals(button.getText()), "button '" + text + "'");
    }

    public JCheckBox checkBox(String text) {
        return one(JCheckBox.class, box -> text.equals(box.getText()), "check box '" + text + "'");
    }

    /** A label with exactly this text. */
    public JLabel label(String text) {
        return one(JLabel.class, label -> text.equals(label.getText()), "label '" + text + "'");
    }

    /**
     * The text field, password field or text area labelled with this text. A trailing required-field
     * marker ({@code " *"}) on the label is ignored, so {@code field("Name")} finds "Name *".
     */
    public JTextComponent field(String label) {
        return labeled(label, JTextComponent.class);
    }

    public JComboBox<?> comboBox(String label) {
        return labeled(label, JComboBox.class);
    }

    /** The component a label points to with {@code setLabelFor}, as forms and the login screen do. */
    public <T extends Component> T labeled(String label, Class<T> type) {
        JLabel match = one(JLabel.class, candidate -> label.equals(withoutMarker(candidate.getText()))
                        && type.isInstance(candidate.getLabelFor()),
                type.getSimpleName() + " labelled '" + label + "'");
        return type.cast(match.getLabelFor());
    }

    /** The only visible table, e.g. of a {@code DataGrid}. */
    public JTable table() {
        return one(JTable.class, table -> true, "table");
    }

    /** Whether any visible label, button or text component shows text containing this text. */
    public boolean containsText(String text) {
        for (Component component : all(Component.class)) {
            String shown = textOf(component);
            if (shown != null && shown.contains(text)) {
                return true;
            }
        }
        return false;
    }

    private static String withoutMarker(String text) {
        return text != null && text.endsWith(" *") ? text.substring(0, text.length() - 2) : text;
    }

    private String inventory() {
        List<String> texts = new ArrayList<>();
        for (Component component : all(Component.class)) {
            String text = textOf(component);
            if (text != null && !text.trim().isEmpty()) {
                texts.add(component.getClass().getSimpleName() + " '" + text.trim() + "'");
            }
        }
        return texts.isEmpty() ? "(no components with text)" : String.join(", ", texts);
    }

    private static String textOf(Component component) {
        if (component instanceof JLabel) {
            return ((JLabel) component).getText();
        }
        if (component instanceof AbstractButton) {
            return ((AbstractButton) component).getText();
        }
        if (component instanceof JTextComponent) {
            return ((JTextComponent) component).getText();
        }
        return null;
    }

    private static <T extends Component> void collect(Component component, Class<T> type, List<T> found) {
        // Windows are skipped in the check, so tests can use frames that are never shown on screen.
        if (!(component instanceof Window) && !component.isVisible()) {
            return;
        }
        if (type.isInstance(component)) {
            found.add(type.cast(component));
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collect(child, type, found);
            }
        }
    }
}
