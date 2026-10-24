package com.rapidswing.forms;

import com.rapidswing.property.Property;
import com.rapidswing.util.Edt;
import com.rapidswing.util.Subscription;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.text.JTextComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A built form: a panel of bound, validated fields. Create one with {@link #builder()}.
 * <pre>
 * AppAction save = AppAction.of("Save", () -&gt; {
 *     if (form.validate()) {
 *         service.save(person);
 *     }
 * });
 * </pre>
 */
public class Form {

    private final JPanel panel;
    private final List<FormField<?>> fields;
    private final Map<String, JComponent> components;
    private final Property<Boolean> valid = new Property<>(true);
    private final List<Subscription> subscriptions = new ArrayList<>();

    Form(JPanel panel, List<FormField<?>> fields, Map<String, JComponent> components) {
        this.panel = panel;
        this.fields = fields;
        this.components = components;
        for (FormField<?> field : fields) {
            field.install();
            subscriptions.add(field.error.onChange(error -> recompute()));
        }
        recompute();
    }

    public static FormBuilder builder() {
        Edt.checkEdt();
        return new FormBuilder(new FormBuilder.State());
    }

    public JPanel getComponent() {
        return panel;
    }

    /**
     * True while every field passes its rules, whether or not errors are shown yet.
     * Use it to enable actions; use {@link #validate()} when the user submits.
     */
    public Property<Boolean> valid() {
        return valid;
    }

    /** Shows every field's error, focuses the first invalid field, and returns whether the form is valid. */
    public boolean validate() {
        FormField<?> firstInvalid = null;
        for (FormField<?> field : fields) {
            field.reveal();
            if (firstInvalid == null && field.error.get() != null) {
                firstInvalid = field;
            }
        }
        if (firstInvalid != null) {
            firstInvalid.focusComponent.requestFocusInWindow();
        }
        return firstInvalid == null;
    }

    /** Current errors as "Label: message", including ones not shown yet. */
    public List<String> errors() {
        List<String> errors = new ArrayList<>();
        for (FormField<?> field : fields) {
            if (field.error.get() != null) {
                errors.add(field.name + ": " + field.error.get());
            }
        }
        return Collections.unmodifiableList(errors);
    }

    /** Current warnings as "Label: message"; they do not make the form invalid. */
    public List<String> warnings() {
        List<String> warnings = new ArrayList<>();
        for (FormField<?> field : fields) {
            if (field.warning.get() != null) {
                warnings.add(field.name + ": " + field.warning.get());
            }
        }
        return Collections.unmodifiableList(warnings);
    }

    /** The warning currently displayed under a field, or {@code null}. Useful in tests. */
    public String warningText(String label) {
        FormField<?> field = field(label);
        return field.showsWarning() ? field.errorLabel.getText() : null;
    }

    /**
     * Shows the model's current values again, discarding invalid input, and hides errors until fields
     * are touched. Call it after loading or resetting the model.
     */
    public void refresh() {
        for (FormField<?> field : fields) {
            field.refresh();
        }
    }

    /**
     * Shows the values without allowing changes, e.g. for users who may only view a record.
     * Text stays selectable and copyable; other inputs are disabled.
     */
    public void setReadOnly(boolean readOnly) {
        for (FormField<?> field : fields) {
            if (field.focusComponent instanceof JTextComponent) {
                ((JTextComponent) field.focusComponent).setEditable(!readOnly);
            } else {
                field.focusComponent.setEnabled(!readOnly);
            }
        }
    }

    /** The input component of a field, by its label, e.g. to request focus or customize it. */
    public JComponent component(String label) {
        JComponent component = components.get(label);
        if (component == null) {
            throw new IllegalArgumentException("The form has no field labelled '" + label + "'");
        }
        return component;
    }

    /**
     * The error message currently displayed under a field, or {@code null} if none is shown
     * (an untouched field may have an error that is not displayed yet). Useful in tests.
     */
    public String errorText(String label) {
        FormField<?> field = field(label);
        return field.errorLabel.isVisible() && !field.showsWarning() ? field.errorLabel.getText() : null;
    }

    FormField<?> field(String label) {
        for (FormField<?> field : fields) {
            if (field.name.equals(label)) {
                return field;
            }
        }
        throw new IllegalArgumentException("The form has no field labelled '" + label + "'");
    }

    /** Disconnects all fields from their properties. */
    public void unbind() {
        for (Subscription subscription : subscriptions) {
            subscription.unsubscribe();
        }
        subscriptions.clear();
        for (FormField<?> field : fields) {
            field.uninstall();
        }
        valid.set(true);
    }

    private void recompute() {
        for (FormField<?> field : fields) {
            if (field.error.get() != null) {
                valid.set(false);
                return;
            }
        }
        valid.set(true);
    }
}
