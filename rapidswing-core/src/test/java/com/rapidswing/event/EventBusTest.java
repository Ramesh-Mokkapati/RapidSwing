package com.rapidswing.event;

import com.rapidswing.util.Subscription;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventBusTest {

    @Test
    void deliversToSubscribersOfTypeAndSupertypes() {
        EventBus bus = new EventBus();
        List<String> received = new ArrayList<>();
        bus.subscribe(String.class, s -> received.add("string:" + s));
        bus.subscribe(CharSequence.class, s -> received.add("chars:" + s));
        bus.subscribe(Integer.class, i -> received.add("int:" + i));

        bus.publish("hi");

        assertEquals(Arrays.asList("string:hi", "chars:hi"), received);
    }

    @Test
    void failingHandlerDoesNotStopOthers() {
        EventBus bus = new EventBus();
        List<String> received = new ArrayList<>();
        bus.subscribe(String.class, s -> {
            throw new IllegalStateException("boom");
        });
        bus.subscribe(String.class, received::add);

        bus.publish("event");

        assertEquals(Arrays.asList("event"), received);
    }

    @Test
    void unsubscribeStopsDelivery() {
        EventBus bus = new EventBus();
        List<String> received = new ArrayList<>();
        Subscription subscription = bus.subscribe(String.class, received::add);

        bus.publish("one");
        subscription.unsubscribe();
        bus.publish("two");

        assertEquals(Arrays.asList("one"), received);
    }

    @Test
    void edtSubscribersRunOnEdt() throws InterruptedException {
        EventBus bus = new EventBus();
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicBoolean onEdt = new AtomicBoolean();
        bus.subscribeOnEdt(String.class, s -> {
            onEdt.set(SwingUtilities.isEventDispatchThread());
            delivered.countDown();
        });

        bus.publish("from test thread");

        assertTrue(delivered.await(5, TimeUnit.SECONDS));
        assertTrue(onEdt.get());
    }
}
