package com.rapidswing.property;

import com.rapidswing.util.Subscription;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * An observable value. Listeners are notified only when the value actually changes.
 * <p>
 * Properties are not synchronized: like Swing components, use them from the EDT.
 */
public class Property<T> {

    private final List<PropertyListener<? super T>> listeners = new CopyOnWriteArrayList<>();
    private T value;

    public Property() {
    }

    public Property(T initialValue) {
        this.value = initialValue;
    }

    public static <T> Property<T> of(T initialValue) {
        return new Property<>(initialValue);
    }

    public T get() {
        return value;
    }

    public void set(T newValue) {
        T oldValue = value;
        if (Objects.equals(oldValue, newValue)) {
            return;
        }
        value = newValue;
        for (PropertyListener<? super T> listener : listeners) {
            listener.changed(oldValue, newValue);
        }
    }

    public Subscription addListener(PropertyListener<? super T> listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /** Shorthand for listeners that only care about the new value. */
    public Subscription onChange(Consumer<? super T> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        return addListener((oldValue, newValue) -> consumer.accept(newValue));
    }

    /** One-way binding: copies the source's value now and every time it changes. */
    public Subscription bindTo(Property<? extends T> source) {
        set(source.get());
        return source.onChange(this::set);
    }

    /**
     * Creates a derived property that recalculates whenever this one changes.
     * The mapper must handle {@code null} if this property can hold {@code null}.
     */
    public <R> Property<R> map(Function<? super T, ? extends R> mapper) {
        Property<R> derived = new Property<>(mapper.apply(value));
        onChange(newValue -> derived.set(mapper.apply(newValue)));
        return derived;
    }

    @Override
    public String toString() {
        return "Property[" + value + "]";
    }
}
