package com.rapidswing.property;

import com.rapidswing.util.Subscription;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyTest {

    @Test
    void notifiesListenersOnlyWhenValueChanges() {
        Property<String> name = Property.of("a");
        List<String> changes = new ArrayList<>();
        name.addListener((oldValue, newValue) -> changes.add(oldValue + "->" + newValue));

        name.set("b");
        name.set("b");
        name.set(null);

        assertEquals(Arrays.asList("a->b", "b->null"), changes);
    }

    @Test
    void unsubscribeStopsNotifications() {
        Property<Integer> count = Property.of(0);
        List<Integer> seen = new ArrayList<>();
        Subscription subscription = count.onChange(seen::add);

        count.set(1);
        subscription.unsubscribe();
        count.set(2);

        assertEquals(Arrays.asList(1), seen);
    }

    @Test
    void bindToFollowsSource() {
        Property<String> source = Property.of("x");
        Property<String> target = new Property<>();

        Subscription binding = target.bindTo(source);
        assertEquals("x", target.get());

        source.set("y");
        assertEquals("y", target.get());

        binding.unsubscribe();
        source.set("z");
        assertEquals("y", target.get());
    }

    @Test
    void mapDerivesValue() {
        Property<String> text = Property.of("");
        Property<Boolean> empty = text.map(String::isEmpty);
        assertTrue(empty.get());

        text.set("hello");
        assertEquals(Boolean.FALSE, empty.get());
    }
}
