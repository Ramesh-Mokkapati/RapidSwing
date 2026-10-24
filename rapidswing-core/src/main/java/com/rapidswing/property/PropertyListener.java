package com.rapidswing.property;

/**
 * Receives the old and new value whenever a {@link Property} changes.
 */
@FunctionalInterface
public interface PropertyListener<T> {

    void changed(T oldValue, T newValue);
}
