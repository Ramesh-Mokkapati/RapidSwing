package com.rapidswing.service;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A minimal service locator. Register services by type, eagerly or lazily, and look them up anywhere
 * through the {@link com.rapidswing.app.AppContext}. Lazy services are created once, on first lookup.
 */
public class ServiceRegistry {

    private final Map<Class<?>, Object> instances = new ConcurrentHashMap<>();
    private final Map<Class<?>, Supplier<?>> factories = new ConcurrentHashMap<>();

    public <T> ServiceRegistry register(Class<T> type, T instance) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(instance, "instance");
        factories.remove(type);
        instances.put(type, instance);
        return this;
    }

    public <T> ServiceRegistry registerLazy(Class<T> type, Supplier<? extends T> factory) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(factory, "factory");
        instances.remove(type);
        factories.put(type, factory);
        return this;
    }

    /** Returns the service, or throws {@link IllegalStateException} if none is registered. */
    public <T> T get(Class<T> type) {
        return find(type).orElseThrow(() -> new IllegalStateException("No service registered for " + type.getName()));
    }

    public <T> Optional<T> find(Class<T> type) {
        Object instance = instances.get(type);
        if (instance == null) {
            Supplier<?> factory = factories.get(type);
            if (factory == null) {
                return Optional.empty();
            }
            synchronized (factory) {
                instance = instances.get(type);
                if (instance == null) {
                    instance = Objects.requireNonNull(factory.get(), "Factory returned null for " + type.getName());
                    instances.put(type, instance);
                }
            }
        }
        return Optional.of(type.cast(instance));
    }

    public boolean contains(Class<?> type) {
        return instances.containsKey(type) || factories.containsKey(type);
    }
}
