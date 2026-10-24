package com.rapidswing.binding;

import com.rapidswing.property.Property;
import com.rapidswing.util.Subscription;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.text.JTextComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * A set of bindings, typically one form, with a combined validity state.
 * <pre>
 * BindingGroup form = new BindingGroup();
 * form.text(nameField, person.name());
 * form.text(ageField, person.age(), Converters.integer());
 * saveAction.enabledWhen(form.valid());
 * </pre>
 */
public class BindingGroup {

    private final List<Binding> bindings = new ArrayList<>();
    private final List<Subscription> subscriptions = new ArrayList<>();
    private final Property<Boolean> valid = new Property<>(true);

    public <B extends Binding> B add(B binding) {
        bindings.add(binding);
        subscriptions.add(binding.valid().onChange(value -> recompute()));
        recompute();
        return binding;
    }

    public Binding text(JTextComponent field, Property<String> property) {
        return add(Bindings.text(field, property));
    }

    public <T> Binding text(JTextComponent field, Property<T> property, Converter<T> converter) {
        return add(Bindings.text(field, property, converter));
    }

    public Binding selected(AbstractButton button, Property<Boolean> property) {
        return add(Bindings.selected(button, property));
    }

    public <T> Binding selectedItem(JComboBox<T> combo, Property<T> property) {
        return add(Bindings.selectedItem(combo, property));
    }

    public <T> Binding value(JSpinner spinner, Property<T> property) {
        return add(Bindings.value(spinner, property));
    }

    public Binding value(JSlider slider, Property<Integer> property) {
        return add(Bindings.value(slider, property));
    }

    /** True while every binding in the group is valid. */
    public Property<Boolean> valid() {
        return valid;
    }

    /** The current error message of every invalid binding. */
    public List<String> errors() {
        List<String> errors = new ArrayList<>();
        for (Binding binding : bindings) {
            String error = binding.error().get();
            if (error != null) {
                errors.add(error);
            }
        }
        return errors;
    }

    /** Refreshes every binding, discarding invalid input. See {@link Binding#refresh()}. */
    public void refresh() {
        for (Binding binding : bindings) {
            binding.refresh();
        }
    }

    public void unbindAll() {
        for (Subscription subscription : subscriptions) {
            subscription.unsubscribe();
        }
        for (Binding binding : bindings) {
            binding.unbind();
        }
        subscriptions.clear();
        bindings.clear();
        valid.set(true);
    }

    private void recompute() {
        for (Binding binding : bindings) {
            if (!Boolean.TRUE.equals(binding.valid().get())) {
                valid.set(false);
                return;
            }
        }
        valid.set(true);
    }
}
