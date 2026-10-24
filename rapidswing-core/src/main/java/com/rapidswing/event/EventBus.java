package com.rapidswing.event;

import com.rapidswing.util.Edt;
import com.rapidswing.util.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Publish/subscribe messaging between decoupled parts of the application.
 * <p>
 * Any object can be an event. Handlers receive events of the subscribed type and its subtypes.
 * A failing handler is logged and does not stop delivery to the other handlers.
 */
public class EventBus {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    private final List<Handler<?>> handlers = new CopyOnWriteArrayList<>();

    /** Handler runs on the thread that publishes the event. */
    public <E> Subscription subscribe(Class<E> type, Consumer<? super E> handler) {
        return add(new Handler<>(type, handler, false));
    }

    /** Handler always runs on the EDT, so it can safely update Swing components. */
    public <E> Subscription subscribeOnEdt(Class<E> type, Consumer<? super E> handler) {
        return add(new Handler<>(type, handler, true));
    }

    public void publish(Object event) {
        Objects.requireNonNull(event, "event");
        for (Handler<?> handler : handlers) {
            if (handler.type.isInstance(event)) {
                handler.deliver(event);
            }
        }
    }

    private Subscription add(Handler<?> handler) {
        handlers.add(handler);
        return () -> handlers.remove(handler);
    }

    private static final class Handler<E> {
        private final Class<E> type;
        private final Consumer<? super E> consumer;
        private final boolean onEdt;

        Handler(Class<E> type, Consumer<? super E> consumer, boolean onEdt) {
            this.type = Objects.requireNonNull(type, "type");
            this.consumer = Objects.requireNonNull(consumer, "handler");
            this.onEdt = onEdt;
        }

        void deliver(Object event) {
            E typed = type.cast(event);
            Runnable call = () -> {
                try {
                    consumer.accept(typed);
                } catch (RuntimeException e) {
                    log.error("Event handler failed for {}", event, e);
                }
            };
            if (onEdt) {
                Edt.run(call);
            } else {
                call.run();
            }
        }
    }
}
