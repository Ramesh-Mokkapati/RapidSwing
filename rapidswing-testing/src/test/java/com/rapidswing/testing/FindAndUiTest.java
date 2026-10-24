package com.rapidswing.testing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FindAndUiTest {

    private JPanel panel;
    private JTextField name;
    private JTextField readOnly;
    private JButton save;
    private JButton disabled;
    private JCheckBox active;
    private JComboBox<String> country;
    private final List<String> clicks = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Ui.onEdt(() -> {
            panel = new JPanel();
            name = new JTextField();
            readOnly = new JTextField("fixed");
            readOnly.setEditable(false);
            save = new JButton("Save");
            save.addActionListener(e -> clicks.add("save"));
            disabled = new JButton("Delete");
            disabled.setEnabled(false);
            active = new JCheckBox("Active");
            country = new JComboBox<>(new String[]{"India", "Japan"});
            JButton hidden = new JButton("Hidden");
            hidden.setVisible(false);

            panel.add(labelFor("Name *", name));
            panel.add(name);
            panel.add(labelFor("Code", readOnly));
            panel.add(readOnly);
            panel.add(labelFor("Country", country));
            panel.add(country);
            panel.add(save);
            panel.add(disabled);
            panel.add(active);
            panel.add(hidden);
        });
    }

    private static JLabel labelFor(String text, java.awt.Component component) {
        JLabel label = new JLabel(text);
        label.setLabelFor(component);
        return label;
    }

    @Test
    void findsComponentsLikeAUserDescribesThem() {
        Find find = Find.in(panel);

        assertSame(name, find.field("Name"));
        assertSame(save, find.button("Save"));
        assertSame(active, find.checkBox("Active"));
        assertSame(country, find.comboBox("Country"));
        assertTrue(find.containsText("fixed"));
        assertFalse(find.containsText("Hidden"));
    }

    @Test
    void missingComponentsFailWithAnInventory() {
        AssertionError error = assertThrows(AssertionError.class, () -> Find.in(panel).button("Cancel"));

        assertTrue(error.getMessage().startsWith("No button 'Cancel' found. Visible: "));
        assertTrue(error.getMessage().contains("JButton 'Save'"));
        assertFalse(error.getMessage().contains("Hidden"));
    }

    @Test
    void interactionsActLikeAUser() {
        Ui.type(name, "Asha");
        Ui.click(save);
        Ui.check(active, true);
        Ui.check(active, true);
        Ui.select(country, "Japan");

        assertEquals("Asha", name.getText());
        assertEquals(1, clicks.size());
        assertTrue(active.isSelected());
        assertEquals("Japan", country.getSelectedItem());
    }

    @Test
    void impossibleInteractionsFail() {
        assertThrows(AssertionError.class, () -> Ui.click(disabled));
        assertThrows(AssertionError.class, () -> Ui.type(readOnly, "changed"));
        assertThrows(AssertionError.class, () -> Ui.select(country, "Mars"));
    }

    @Test
    void waitUntilTimesOutWithTheDescription() {
        AssertionError error = assertThrows(AssertionError.class,
                () -> Ui.waitUntil("the sky turns green", () -> false, 100));

        assertEquals("Timed out after 100 ms waiting until the sky turns green", error.getMessage());
    }
}
