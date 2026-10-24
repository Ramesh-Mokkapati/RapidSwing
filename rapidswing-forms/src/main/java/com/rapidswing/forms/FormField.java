package com.rapidswing.forms;

import com.formdev.flatlaf.FlatClientProperties;
import com.rapidswing.binding.Binding;
import com.rapidswing.property.Property;
import com.rapidswing.util.Subscription;

import javax.swing.JComponent;
import javax.swing.JLabel;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * One field of a {@link Form}: its components, binding, rules and error state.
 * <p>
 * The error is always tracked, but only shown once the field is "touched" (edited, or left after
 * focusing it) or {@link Form#validate()} is called, so a fresh form does not open full of red.
 * Conversion errors, such as letters in a number field, are shown immediately.
 */
final class FormField<T> {

    final String name;
    final JLabel label;
    final JComponent layoutComponent;
    final JComponent focusComponent;
    final Property<T> property;
    final Binding binding;
    final ErrorLabel errorLabel = new ErrorLabel();
    final Property<String> error = new Property<>();
    final Property<String> warning = new Property<>();

    final List<Validator<? super T>> validators = new ArrayList<>();
    final List<Validator<? super T>> warnings = new ArrayList<>();
    final List<Supplier<String>> checks = new ArrayList<>();
    final List<Property<?>> dependencies = new ArrayList<>();
    final List<Binding> extraBindings = new ArrayList<>();
    String tooltip;

    private final List<Subscription> subscriptions = new ArrayList<>();
    private FocusListener focusListener;
    private boolean touched;

    /**
     * @param label           the label component, or {@code null} when the component shows its own text (check boxes)
     * @param layoutComponent what is placed in the grid and outlined on error, e.g. the scroll pane of a text area
     * @param focusComponent  what receives focus and the tooltip
     */
    FormField(String name, JLabel label, JComponent layoutComponent, JComponent focusComponent,
              Property<T> property, Binding binding) {
        this.name = name;
        this.label = label;
        this.layoutComponent = layoutComponent;
        this.focusComponent = focusComponent;
        this.property = property;
        this.binding = binding;
        this.tooltip = focusComponent.getToolTipText();
    }

    void markRequired() {
        if (label != null && !label.getText().endsWith(" *")) {
            label.setText(label.getText() + " *");
        }
    }

    void install() {
        subscriptions.add(property.onChange(value -> {
            touched = true;
            evaluate();
        }));
        subscriptions.add(binding.error().onChange(conversionError -> evaluate()));
        for (Property<?> dependency : dependencies) {
            subscriptions.add(dependency.onChange(value -> evaluate()));
        }
        focusListener = new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                touched = true;
                updateDisplay();
            }
        };
        focusComponent.addFocusListener(focusListener);
        evaluate();
    }

    void evaluate() {
        String conversionError = binding.error().get();
        error.set(conversionError != null ? conversionError : ruleError());
        warning.set(error.get() != null ? null : warningText());
        updateDisplay();
    }

    /** Shows the current error, if any, even if the field was never touched. */
    void reveal() {
        touched = true;
        updateDisplay();
    }

    /** Discards invalid input, re-checks the rules and hides errors until the field is touched again. */
    void refresh() {
        binding.refresh();
        touched = false;
        evaluate();
    }

    void uninstall() {
        for (Subscription subscription : subscriptions) {
            subscription.unsubscribe();
        }
        subscriptions.clear();
        if (focusListener != null) {
            focusComponent.removeFocusListener(focusListener);
        }
        binding.unbind();
        for (Binding extra : extraBindings) {
            extra.unbind();
        }
        touched = false;
        updateDisplay();
    }

    private String ruleError() {
        T value = property.get();
        for (Validator<? super T> validator : validators) {
            String message = validator.validate(value);
            if (message != null) {
                return message;
            }
        }
        for (Supplier<String> check : checks) {
            String message = check.get();
            if (message != null) {
                return message;
            }
        }
        return null;
    }

    private String warningText() {
        T value = property.get();
        for (Validator<? super T> rule : warnings) {
            String message = rule.validate(value);
            if (message != null) {
                return message;
            }
        }
        return null;
    }

    /** Whether the message under the field is a warning rather than an error. */
    boolean showsWarning() {
        return errorLabel.isVisible() && errorLabel.isWarning();
    }

    private void updateDisplay() {
        boolean conversionError = binding.error().get() != null;
        String shownError = touched || conversionError ? error.get() : null;
        String shownWarning = shownError == null && touched ? warning.get() : null;
        String shown = shownError != null ? shownError : shownWarning;
        layoutComponent.putClientProperty(FlatClientProperties.OUTLINE, shownError != null
                ? FlatClientProperties.OUTLINE_ERROR : shownWarning != null ? FlatClientProperties.OUTLINE_WARNING : null);
        focusComponent.setToolTipText(shown != null ? shown : tooltip);
        errorLabel.setWarning(shownError == null && shownWarning != null);
        errorLabel.setText(shown);
        errorLabel.setVisible(shown != null);
    }
}
