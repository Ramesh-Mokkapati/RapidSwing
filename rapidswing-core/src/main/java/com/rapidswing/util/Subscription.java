package com.rapidswing.util;

/**
 * Handle returned when registering a listener or handler. Call {@link #unsubscribe()} to remove it.
 */
@FunctionalInterface
public interface Subscription {

    void unsubscribe();
}
