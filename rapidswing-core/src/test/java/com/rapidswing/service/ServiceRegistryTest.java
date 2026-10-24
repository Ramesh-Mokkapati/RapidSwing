package com.rapidswing.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceRegistryTest {

    interface Greeter {
        String greet();
    }

    @Test
    void returnsRegisteredInstance() {
        ServiceRegistry registry = new ServiceRegistry();
        Greeter greeter = () -> "hello";
        registry.register(Greeter.class, greeter);

        assertSame(greeter, registry.get(Greeter.class));
        assertTrue(registry.contains(Greeter.class));
    }

    @Test
    void lazyServiceIsCreatedOnceOnFirstLookup() {
        ServiceRegistry registry = new ServiceRegistry();
        AtomicInteger created = new AtomicInteger();
        registry.registerLazy(Greeter.class, () -> {
            created.incrementAndGet();
            return () -> "lazy";
        });

        assertEquals(0, created.get());
        Greeter first = registry.get(Greeter.class);
        Greeter second = registry.get(Greeter.class);

        assertEquals(1, created.get());
        assertSame(first, second);
        assertEquals("lazy", first.greet());
    }

    @Test
    void missingServiceFailsWithClearMessage() {
        ServiceRegistry registry = new ServiceRegistry();

        assertFalse(registry.find(Greeter.class).isPresent());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> registry.get(Greeter.class));
        assertTrue(e.getMessage().contains(Greeter.class.getName()));
    }
}
