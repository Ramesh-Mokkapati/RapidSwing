package com.rapidswing.forms;

import com.formdev.flatlaf.FlatClientProperties;
import com.rapidswing.property.Property;
import com.rapidswing.util.Edt;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormTest {

    private static String shownError(Form form, String label) {
        return form.errorText(label);
    }

    @Test
    void errorsAreHiddenUntilTouchedButFormIsInvalid() {
        Edt.runAndWait(() -> {
            Property<String> name = Property.of("");
            Form form = Form.builder().text("Name", name).required().build();

            assertFalse(form.valid().get());
            assertNull(shownError(form, "Name"));

            JTextField field = (JTextField) form.component("Name");
            field.setText("Asha");
            assertTrue(form.valid().get());

            field.setText("");
            assertEquals("This field is required", shownError(form, "Name"));
            assertEquals(FlatClientProperties.OUTLINE_ERROR, field.getClientProperty(FlatClientProperties.OUTLINE));
            assertEquals("This field is required", field.getToolTipText());
        });
    }

    @Test
    void validateRevealsAllErrors() {
        Edt.runAndWait(() -> {
            Form form = Form.builder()
                    .text("Name", Property.of("")).required()
                    .text("Email", Property.of("not-an-email")).validate(Validators.email())
                    .text("City", Property.of("Pune"))
                    .build();

            assertFalse(form.validate());
            assertEquals("This field is required", shownError(form, "Name"));
            assertEquals("Enter a valid email address", shownError(form, "Email"));
            assertNull(shownError(form, "City"));
            assertEquals(2, form.errors().size());
            assertEquals("Name: This field is required", form.errors().get(0));
        });
    }

    @Test
    void conversionErrorsShowImmediatelyAndTakePriority() {
        Edt.runAndWait(() -> {
            Property<Integer> age = new Property<>();
            Form form = Form.builder().integer("Age", age).required().validate(Validators.range(18, 120)).build();
            JTextField field = (JTextField) form.component("Age");

            field.setText("abc");
            assertEquals("Enter a whole number", shownError(form, "Age"));

            field.setText("12");
            assertEquals(12, age.get());
            assertEquals("Must be between 18 and 120", shownError(form, "Age"));

            field.setText("30");
            assertNull(shownError(form, "Age"));
            assertTrue(form.valid().get());
        });
    }

    @Test
    void crossFieldCheckReactsToDependency() {
        Edt.runAndWait(() -> {
            Property<String> password = Property.of("");
            Property<String> confirm = Property.of("");
            Form form = Form.builder()
                    .password("Password", password)
                    .password("Confirm", confirm)
                        .check(() -> Objects.equals(password.get(), confirm.get()) ? null : "Passwords do not match",
                               password)
                    .build();

            ((JTextField) form.component("Confirm")).setText("secret");
            assertEquals("Passwords do not match", shownError(form, "Confirm"));

            ((JTextField) form.component("Password")).setText("secret");
            assertNull(shownError(form, "Confirm"));
            assertTrue(form.valid().get());
        });
    }

    @Test
    void refreshDiscardsInputAndHidesErrors() {
        Edt.runAndWait(() -> {
            Property<Integer> age = new Property<>();
            Property<String> name = Property.of("");
            Form form = Form.builder()
                    .text("Name", name).required()
                    .integer("Age", age)
                    .build();
            form.validate();
            ((JTextField) form.component("Age")).setText("x");

            form.refresh();

            assertEquals("", ((JTextField) form.component("Age")).getText());
            assertNull(shownError(form, "Age"));
            assertNull(shownError(form, "Name"));
            assertFalse(form.valid().get());
        });
    }

    @Test
    void requiredMarksLabelAndChoiceFieldsBind() {
        Edt.runAndWait(() -> {
            Property<String> country = Property.of("Japan");
            Property<Boolean> newsletter = Property.of(false);
            Form form = Form.builder()
                    .text("Name", Property.of("")).required()
                    .combo("Country", country, "India", "Japan")
                    .checkbox("Newsletter", newsletter)
                    .build();

            JLabel nameLabel = form.field("Name").label;
            assertEquals("Name *", nameLabel.getText());

            ((JComboBox<?>) form.component("Country")).setSelectedItem("India");
            ((JCheckBox) form.component("Newsletter")).setSelected(true);
            assertEquals("India", country.get());
            assertTrue(newsletter.get());
        });
    }

    @Test
    void enabledWhenFollowsCondition() {
        Edt.runAndWait(() -> {
            Property<Boolean> hasCompany = Property.of(false);
            Form form = Form.builder()
                    .text("Company", Property.of("")).enabledWhen(hasCompany)
                    .build();

            assertFalse(form.component("Company").isEnabled());
            hasCompany.set(true);
            assertTrue(form.component("Company").isEnabled());
        });
    }

    @Test
    void unbindDisconnectsFields() {
        Edt.runAndWait(() -> {
            Property<String> name = Property.of("a");
            Form form = Form.builder().text("Name", name).required().build();

            form.unbind();
            ((JTextField) form.component("Name")).setText("changed");

            assertEquals("a", name.get());
            assertNull(shownError(form, "Name"));
        });
    }

    @Test
    void builderMisuseFailsFast() {
        Edt.runAndWait(() -> {
            FormBuilder builder = Form.builder().text("Name", Property.of(""));
            assertThrows(IllegalArgumentException.class, () -> builder.text("Name", Property.of("")));

            builder.build();
            assertThrows(IllegalStateException.class, builder::build);
        });
        assertThrows(IllegalStateException.class, Form::builder);
    }
}
