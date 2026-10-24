package com.rapidswing.binding;

import com.rapidswing.property.Property;

/**
 * A live connection between a component and a {@link Property}, created by {@link Bindings}.
 */
public interface Binding {

    /** Disconnects the component from the property and removes any error decoration. */
    void unbind();

    /**
     * Shows the property's current value in the component again, discarding any invalid input.
     * Useful after resetting a model, because setting a property to the value it already has does not notify.
     */
    void refresh();

    /** False while the component holds input that cannot be converted. Always true for one-way bindings. */
    Property<Boolean> valid();

    /** The conversion error shown to the user, or {@code null} when valid. */
    Property<String> error();
}
