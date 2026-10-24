package com.rapidswing.binding;

import com.formdev.flatlaf.FlatClientProperties;
import com.rapidswing.property.Property;
import com.rapidswing.util.Edt;
import com.rapidswing.util.Subscription;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemListener;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Connects Swing components to {@link Property} values.
 * <p>
 * Two-way bindings copy the property's value into the component immediately, then keep both in sync.
 * Text bindings update the property on every keystroke. Input that cannot be converted leaves the
 * property unchanged, marks the binding invalid and outlines the field in red with the error as tooltip.
 * <pre>
 * Bindings.text(nameField, person.name());
 * Bindings.text(ageField, person.age(), Converters.integer());
 * Bindings.selected(activeCheckBox, person.active());
 * </pre>
 * All methods must be called on the EDT, and bound properties must only be changed on the EDT.
 */
public final class Bindings {

    private Bindings() {
    }

    // ---- Two-way bindings -------------------------------------------------------------------------------

    public static Binding text(JTextComponent field, Property<String> property) {
        return text(field, property, Converters.string());
    }

    public static <T> Binding text(JTextComponent field, Property<T> property, Converter<T> converter) {
        return text(field, property, converter, true);
    }

    /**
     * @param decorate false when the caller displays {@link Binding#error()} itself, as the forms module does
     *                 to combine conversion errors with validation errors
     */
    public static <T> Binding text(JTextComponent field, Property<T> property, Converter<T> converter,
                                   boolean decorate) {
        Edt.checkEdt();
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(property, "property");
        Objects.requireNonNull(converter, "converter");
        BindingImpl binding = new BindingImpl();

        Runnable propertyToField = () -> binding.guarded(() -> {
            String text = converter.toString(property.get());
            if (!text.equals(field.getText())) {
                field.setText(text);
            }
            binding.markValid();
        });
        // Replacing text (setText, or typing over a selection) fires a remove and then an insert. The remove
        // alone can leave a transient value, such as an empty field: remember the value from before it, so an
        // invalid insert in the same edit restores it instead of leaving the transient value in the property.
        PendingRemoval<T> pendingRemoval = new PendingRemoval<>();
        Runnable fieldToProperty = () -> binding.guarded(() -> {
            try {
                property.set(converter.fromString(field.getText()));
                binding.markValid();
            } catch (ConversionException e) {
                if (pendingRemoval.active) {
                    property.set(pendingRemoval.previousValue);
                }
                binding.markInvalid(e.getMessage());
            }
        });

        binding.setRefresher(propertyToField);
        propertyToField.run();

        DocumentListener documentListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                fieldToProperty.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                if (!binding.isUpdating() && !pendingRemoval.active) {
                    pendingRemoval.active = true;
                    pendingRemoval.previousValue = property.get();
                    // The insert of a replace arrives before this runs, so it ends the edit.
                    Edt.later(pendingRemoval::clear);
                }
                fieldToProperty.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                fieldToProperty.run();
            }
        };
        // On focus loss, show valid input in its canonical form, e.g. " 042" becomes "42".
        FocusListener focusListener = new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (Boolean.TRUE.equals(binding.valid().get())) {
                    propertyToField.run();
                }
            }
        };
        field.getDocument().addDocumentListener(documentListener);
        field.addFocusListener(focusListener);
        Subscription subscription = property.onChange(value -> propertyToField.run());

        binding.onUnbind(() -> {
            field.getDocument().removeDocumentListener(documentListener);
            field.removeFocusListener(focusListener);
            subscription.unsubscribe();
        });
        if (decorate) {
            decorateErrors(field, binding);
        }
        return binding;
    }

    /** Check boxes, radio buttons and toggle buttons. A {@code null} value shows as unselected. */
    public static Binding selected(AbstractButton button, Property<Boolean> property) {
        Edt.checkEdt();
        BindingImpl binding = new BindingImpl();

        Runnable propertyToButton = () -> binding.guarded(
                () -> button.setSelected(Boolean.TRUE.equals(property.get())));
        binding.setRefresher(propertyToButton);
        propertyToButton.run();

        ItemListener listener = e -> binding.guarded(() -> property.set(button.isSelected()));
        button.addItemListener(listener);
        Subscription subscription = property.onChange(value -> propertyToButton.run());

        binding.onUnbind(() -> {
            button.removeItemListener(listener);
            subscription.unsubscribe();
        });
        return binding;
    }

    public static <T> Binding selectedItem(JComboBox<T> combo, Property<T> property) {
        Edt.checkEdt();
        BindingImpl binding = new BindingImpl();

        Runnable propertyToCombo = () -> binding.guarded(() -> combo.setSelectedItem(property.get()));
        binding.setRefresher(propertyToCombo);
        propertyToCombo.run();

        ActionListener listener = e -> binding.guarded(() -> property.set(selectedItem(combo)));
        combo.addActionListener(listener);
        Subscription subscription = property.onChange(value -> propertyToCombo.run());

        binding.onUnbind(() -> {
            combo.removeActionListener(listener);
            subscription.unsubscribe();
        });
        return binding;
    }

    /**
     * The property's type must match the spinner model, e.g. {@code Property<Integer>} for an integer
     * {@code SpinnerNumberModel}. A spinner cannot be empty: a {@code null} property takes the spinner's
     * current value, and setting the property to {@code null} later is ignored.
     */
    public static <T> Binding value(JSpinner spinner, Property<T> property) {
        Edt.checkEdt();
        BindingImpl binding = new BindingImpl();

        Runnable propertyToSpinner = () -> binding.guarded(() -> {
            if (property.get() != null) {
                spinner.setValue(property.get());
            }
        });
        binding.setRefresher(propertyToSpinner);
        if (property.get() == null) {
            property.set(spinnerValue(spinner));
        } else {
            propertyToSpinner.run();
        }

        ChangeListener listener = e -> binding.guarded(() -> property.set(spinnerValue(spinner)));
        spinner.addChangeListener(listener);
        Subscription subscription = property.onChange(value -> propertyToSpinner.run());

        binding.onUnbind(() -> {
            spinner.removeChangeListener(listener);
            subscription.unsubscribe();
        });
        return binding;
    }

    /** Same {@code null} handling as {@link #value(JSpinner, Property)}. */
    public static Binding value(JSlider slider, Property<Integer> property) {
        Edt.checkEdt();
        BindingImpl binding = new BindingImpl();

        Runnable propertyToSlider = () -> binding.guarded(() -> {
            if (property.get() != null) {
                slider.setValue(property.get());
            }
        });
        binding.setRefresher(propertyToSlider);
        if (property.get() == null) {
            property.set(slider.getValue());
        } else {
            propertyToSlider.run();
        }

        ChangeListener listener = e -> binding.guarded(() -> property.set(slider.getValue()));
        slider.addChangeListener(listener);
        Subscription subscription = property.onChange(value -> propertyToSlider.run());

        binding.onUnbind(() -> {
            slider.removeChangeListener(listener);
            subscription.unsubscribe();
        });
        return binding;
    }

    // ---- One-way bindings (property to component) ------------------------------------------------------

    public static Binding enabled(Component component, Property<Boolean> property) {
        return oneWay(property, value -> component.setEnabled(Boolean.TRUE.equals(value)));
    }

    public static Binding visible(Component component, Property<Boolean> property) {
        return oneWay(property, value -> component.setVisible(Boolean.TRUE.equals(value)));
    }

    public static <T> Binding label(JLabel label, Property<T> property) {
        return label(label, property, value -> value == null ? "" : String.valueOf(value));
    }

    public static <T> Binding label(JLabel label, Property<T> property, Function<? super T, String> formatter) {
        return oneWay(property, value -> label.setText(formatter.apply(value)));
    }

    // ---- Helpers -------------------------------------------------------------------------------------

    private static <T> Binding oneWay(Property<T> property, Consumer<? super T> apply) {
        Edt.checkEdt();
        BindingImpl binding = new BindingImpl();
        Runnable refresh = () -> apply.accept(property.get());
        binding.setRefresher(refresh);
        refresh.run();
        Subscription subscription = property.onChange(apply);
        binding.onUnbind(subscription::unsubscribe);
        return binding;
    }

    /** Red FlatLaf outline plus the error as tooltip while invalid; restores the original tooltip after. */
    private static void decorateErrors(JComponent component, BindingImpl binding) {
        String originalTooltip = component.getToolTipText();
        Subscription subscription = binding.error().onChange(message -> {
            component.putClientProperty(FlatClientProperties.OUTLINE,
                    message == null ? null : FlatClientProperties.OUTLINE_ERROR);
            component.setToolTipText(message == null ? originalTooltip : message);
        });
        binding.onUnbind(() -> {
            component.putClientProperty(FlatClientProperties.OUTLINE, null);
            component.setToolTipText(originalTooltip);
            subscription.unsubscribe();
        });
    }

    private static final class PendingRemoval<T> {
        boolean active;
        T previousValue;

        void clear() {
            active = false;
            previousValue = null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T selectedItem(JComboBox<T> combo) {
        return (T) combo.getSelectedItem();
    }

    @SuppressWarnings("unchecked")
    private static <T> T spinnerValue(JSpinner spinner) {
        return (T) spinner.getValue();
    }
}
