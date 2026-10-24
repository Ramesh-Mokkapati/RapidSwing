package com.rapidswing.binding;

import com.formdev.flatlaf.FlatClientProperties;
import com.rapidswing.property.Property;
import com.rapidswing.util.Edt;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindingsTest {

    @Test
    void textBindsBothWays() {
        Edt.runAndWait(() -> {
            Property<String> name = Property.of("Asha");
            JTextField field = new JTextField();
            Bindings.text(field, name);

            assertEquals("Asha", field.getText());

            field.setText("Liam");
            assertEquals("Liam", name.get());

            name.set("Mei");
            assertEquals("Mei", field.getText());
        });
    }

    @Test
    void invalidTextKeepsLastValidValueAndDecoratesField() {
        Edt.runAndWait(() -> {
            Property<Integer> age = Property.of(30);
            JTextField field = new JTextField();
            field.setToolTipText("Your age");
            Binding binding = Bindings.text(field, age, Converters.integer());

            field.setText("3x");
            assertEquals(30, age.get());
            assertFalse(binding.valid().get());
            assertEquals("Enter a whole number", binding.error().get());
            assertEquals(FlatClientProperties.OUTLINE_ERROR, field.getClientProperty(FlatClientProperties.OUTLINE));
            assertEquals("Enter a whole number", field.getToolTipText());

            field.setText("31");
            assertEquals(31, age.get());
            assertTrue(binding.valid().get());
            assertNull(field.getClientProperty(FlatClientProperties.OUTLINE));
            assertEquals("Your age", field.getToolTipText());
        });
    }

    @Test
    void blankTextConvertsToNull() {
        Edt.runAndWait(() -> {
            Property<Integer> age = Property.of(30);
            JTextField field = new JTextField();
            Bindings.text(field, age, Converters.integer());

            field.setText("");
            assertNull(age.get());
        });
    }

    @Test
    void focusLostShowsCanonicalText() {
        Edt.runAndWait(() -> {
            Property<Integer> age = new Property<>();
            JTextField field = new JTextField();
            Bindings.text(field, age, Converters.integer());

            field.setText(" 042");
            assertEquals(42, age.get());
            assertEquals(" 042", field.getText());

            FocusEvent focusLost = new FocusEvent(field, FocusEvent.FOCUS_LOST);
            for (FocusListener listener : field.getFocusListeners()) {
                listener.focusLost(focusLost);
            }
            assertEquals("42", field.getText());
        });
    }

    @Test
    void refreshDiscardsInvalidInput() {
        Edt.runAndWait(() -> {
            Property<Integer> age = new Property<>();
            JTextField field = new JTextField();
            Binding binding = Bindings.text(field, age, Converters.integer());

            field.setText("abc");
            age.set(null);
            assertEquals("abc", field.getText());

            binding.refresh();
            assertEquals("", field.getText());
            assertTrue(binding.valid().get());
        });
    }

    @Test
    void unbindStopsSyncAndClearsDecoration() {
        Edt.runAndWait(() -> {
            Property<Integer> age = Property.of(1);
            JTextField field = new JTextField();
            Binding binding = Bindings.text(field, age, Converters.integer());
            field.setText("bad");

            binding.unbind();
            assertNull(field.getClientProperty(FlatClientProperties.OUTLINE));
            assertTrue(binding.valid().get());

            field.setText("5");
            age.set(9);
            assertEquals(9, age.get());
            assertEquals("5", field.getText());
        });
    }

    @Test
    void checkBoxBindsBothWays() {
        Edt.runAndWait(() -> {
            Property<Boolean> active = Property.of(true);
            JCheckBox box = new JCheckBox();
            Bindings.selected(box, active);

            assertTrue(box.isSelected());
            box.setSelected(false);
            assertEquals(Boolean.FALSE, active.get());
            active.set(null);
            assertFalse(box.isSelected());
            active.set(true);
            assertTrue(box.isSelected());
        });
    }

    @Test
    void comboBindsBothWays() {
        Edt.runAndWait(() -> {
            Property<String> country = Property.of("Japan");
            JComboBox<String> combo = new JComboBox<>(new String[]{"India", "Japan", "Spain"});
            Bindings.selectedItem(combo, country);

            assertEquals("Japan", combo.getSelectedItem());
            combo.setSelectedItem("Spain");
            assertEquals("Spain", country.get());
            country.set("India");
            assertEquals("India", combo.getSelectedItem());
        });
    }

    @Test
    void spinnerAndSliderBindBothWays() {
        Edt.runAndWait(() -> {
            Property<Integer> quantity = new Property<>();
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
            Bindings.value(spinner, quantity);
            assertEquals(5, quantity.get());
            spinner.setValue(7);
            assertEquals(7, quantity.get());
            quantity.set(2);
            assertEquals(2, spinner.getValue());
            quantity.set(null);
            assertEquals(2, spinner.getValue());

            Property<Integer> level = Property.of(8);
            JSlider slider = new JSlider(0, 10);
            Bindings.value(slider, level);
            assertEquals(8, slider.getValue());
            slider.setValue(3);
            assertEquals(3, level.get());
        });
    }

    @Test
    void oneWayBindingsFollowProperty() {
        Edt.runAndWait(() -> {
            Property<Boolean> canSave = Property.of(false);
            Property<Integer> count = Property.of(3);
            JLabel button = new JLabel();
            JLabel label = new JLabel();

            Bindings.enabled(button, canSave);
            Bindings.label(label, count, n -> n + " items");
            assertFalse(button.isEnabled());
            assertEquals("3 items", label.getText());

            canSave.set(true);
            count.set(4);
            assertTrue(button.isEnabled());
            assertEquals("4 items", label.getText());
        });
    }

    @Test
    void bindingOffEdtFails() {
        assertThrows(IllegalStateException.class, () -> Bindings.text(new JTextField(), Property.of("")));
    }
}
