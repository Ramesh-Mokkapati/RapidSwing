package com.rapidswing.forms;

import com.formdev.flatlaf.FlatClientProperties;
import com.rapidswing.binding.Bindings;
import com.rapidswing.property.Property;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Configures the field just added to a {@link FormBuilder}. Inherits all the builder's methods,
 * so the chain can continue with the next field.
 */
public final class FieldBuilder<T> extends FormBuilder {

    private final FormField<T> field;

    FieldBuilder(State state, FormField<T> field) {
        super(state);
        this.field = field;
    }

    /** Rejects empty values and marks the label with an asterisk. */
    public FieldBuilder<T> required() {
        return required(Validators.required());
    }

    public FieldBuilder<T> required(String message) {
        return required(Validators.required(message));
    }

    private FieldBuilder<T> required(Validator<? super T> rule) {
        checkNotBuilt();
        field.validators.add(0, rule);
        field.markRequired();
        return this;
    }

    /** Adds a rule. Rules run in the order added, and the first failure is shown. */
    public FieldBuilder<T> validate(Validator<? super T> rule) {
        checkNotBuilt();
        field.validators.add(Objects.requireNonNull(rule, "rule"));
        return this;
    }

    /**
     * Adds a warning: shown in amber under the field, but the form stays
     * valid and can be saved. E.g. an unusual discount, or a delivery date on a weekend.
     * <pre>
     * .number("Discount %", order.discount()).warn(d -&gt; d != null &amp;&amp; d &gt; 30 ? "Unusually high discount" : null)
     * </pre>
     */
    public FieldBuilder<T> warn(Validator<? super T> rule) {
        checkNotBuilt();
        field.warnings.add(Objects.requireNonNull(rule, "rule"));
        return this;
    }

    /**
     * Adds a cross-field rule, re-checked whenever this field or any dependency changes.
     * <pre>
     * .password("Confirm password", account.confirm())
     *     .check(() -&gt; Objects.equals(account.password().get(), account.confirm().get())
     *             ? null : "Passwords do not match", account.password())
     * </pre>
     */
    public FieldBuilder<T> check(Supplier<String> rule, Property<?>... dependsOn) {
        checkNotBuilt();
        field.checks.add(Objects.requireNonNull(rule, "rule"));
        field.dependencies.addAll(Arrays.asList(dependsOn));
        return this;
    }

    public FieldBuilder<T> tooltip(String text) {
        checkNotBuilt();
        field.tooltip = text;
        field.focusComponent.setToolTipText(text);
        return this;
    }

    /** Grey hint text shown while a text field is empty. */
    public FieldBuilder<T> placeholder(String text) {
        checkNotBuilt();
        field.focusComponent.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, text);
        return this;
    }

    /** Enables the field and its label only while the condition is {@code true}. */
    public FieldBuilder<T> enabledWhen(Property<Boolean> condition) {
        checkNotBuilt();
        field.extraBindings.add(Bindings.enabled(field.focusComponent, condition));
        if (field.label != null) {
            field.extraBindings.add(Bindings.enabled(field.label, condition));
        }
        return this;
    }
}
