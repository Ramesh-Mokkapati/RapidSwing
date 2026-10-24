package com.rapidswing.binding;

import com.rapidswing.property.Property;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Adapts a getter/setter pair of a plain Java bean to a {@link Property}, so POJOs can be bound
 * without changing them. Changes are written to the bean immediately.
 * <pre>
 * BeanProperty&lt;Customer, String&gt; name = BeanProperty.of(customer, Customer::getName, Customer::setName);
 * Bindings.text(nameField, name);
 * </pre>
 * The bean does not notify about its own changes: call {@link #reload()} after changing it directly.
 */
public class BeanProperty<B, V> extends Property<V> {

    private final B bean;
    private final Function<? super B, ? extends V> getter;

    private BeanProperty(B bean, Function<? super B, ? extends V> getter, BiConsumer<? super B, ? super V> setter) {
        super(getter.apply(bean));
        this.bean = bean;
        this.getter = getter;
        addListener((oldValue, newValue) -> setter.accept(bean, newValue));
    }

    public static <B, V> BeanProperty<B, V> of(B bean, Function<? super B, ? extends V> getter,
                                               BiConsumer<? super B, ? super V> setter) {
        Objects.requireNonNull(bean, "bean");
        Objects.requireNonNull(getter, "getter");
        Objects.requireNonNull(setter, "setter");
        return new BeanProperty<>(bean, getter, setter);
    }

    /** Re-reads the value from the bean, updating any bound components. */
    public void reload() {
        set(getter.apply(bean));
    }

    public B getBean() {
        return bean;
    }
}
