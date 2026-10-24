package com.rapidswing.crud;

import com.rapidswing.binding.BeanProperty;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Passed to a {@link CrudScreen}'s form definition: the copy being edited, and properties for its fields.
 * <pre>
 * .form((form, edit) -&gt; form
 *         .text("Name", edit.property(Customer::getName, Customer::setName)).required())
 * </pre>
 * Changes made through {@link #property} mark the editor as having unsaved changes.
 */
public final class EditContext<T> {

    private final T item;
    private final boolean isNew;
    private final Runnable onChange;

    EditContext(T item, boolean isNew, Runnable onChange) {
        this.item = item;
        this.isNew = isNew;
        this.onChange = onChange;
    }

    /** The working copy. Writing to it directly does not mark the editor dirty: prefer {@link #property}. */
    public T item() {
        return item;
    }

    public boolean isNew() {
        return isNew;
    }

    public <V> BeanProperty<T, V> property(Function<? super T, ? extends V> getter,
                                           BiConsumer<? super T, ? super V> setter) {
        BeanProperty<T, V> property = BeanProperty.of(item, getter, setter);
        property.onChange(value -> onChange.run());
        return property;
    }
}
