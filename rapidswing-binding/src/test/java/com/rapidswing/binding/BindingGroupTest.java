package com.rapidswing.binding;

import com.rapidswing.property.Property;
import com.rapidswing.util.Edt;
import org.junit.jupiter.api.Test;

import javax.swing.JTextField;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindingGroupTest {

    @Test
    void validOnlyWhileAllBindingsAreValid() {
        Edt.runAndWait(() -> {
            JTextField age = new JTextField();
            JTextField birthday = new JTextField();
            BindingGroup form = new BindingGroup();
            form.text(age, new Property<Integer>(), Converters.integer());
            form.text(birthday, new Property<LocalDate>(), Converters.localDate());
            assertTrue(form.valid().get());

            age.setText("x");
            birthday.setText("tomorrow");
            assertFalse(form.valid().get());
            assertEquals(2, form.errors().size());

            age.setText("40");
            assertFalse(form.valid().get());
            assertEquals(1, form.errors().size());

            form.refresh();
            assertTrue(form.valid().get());
            assertTrue(form.errors().isEmpty());
        });
    }

    @Test
    void unbindAllDisconnectsEverything() {
        Edt.runAndWait(() -> {
            Property<String> name = Property.of("a");
            JTextField field = new JTextField();
            BindingGroup form = new BindingGroup();
            form.text(field, name);

            form.unbindAll();
            field.setText("changed");

            assertEquals("a", name.get());
            assertTrue(form.valid().get());
        });
    }
}
