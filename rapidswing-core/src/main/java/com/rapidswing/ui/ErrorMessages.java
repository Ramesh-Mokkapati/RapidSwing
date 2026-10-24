package com.rapidswing.ui;

import com.rapidswing.i18n.I18n;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/**
 * Turns exceptions into messages users understand, configured per exception class. Error dialogs
 * show this message; the technical details stay available under "Show details".
 * <pre>
 * ErrorMessages.register(OptimisticLockException.class, e -&gt; "Someone else changed this record. Reload and try again.");
 * </pre>
 * Without a registration, the translation {@code error.<SimpleName>} is used if there is one (with the exception's
 * message as {@code {0}}); otherwise the exception's own message. Wrapper exceptions are looked through.
 */
public final class ErrorMessages {

    private static final Map<Class<?>, Function<Throwable, String>> MESSAGES = new ConcurrentHashMap<>();
    private static final int MAX_CAUSES = 10;

    private ErrorMessages() {
    }

    @SuppressWarnings("unchecked")
    public static <T extends Throwable> void register(Class<T> type, Function<? super T, String> message) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(message, "message");
        MESSAGES.put(type, error -> message.apply((T) error));
    }

    public static void unregister(Class<? extends Throwable> type) {
        MESSAGES.remove(type);
    }

    public static String messageFor(Throwable error) {
        if (error == null) {
            return "";
        }
        Throwable current = error;
        for (int depth = 0; current != null && depth < MAX_CAUSES; depth++) {
            if (!isWrapper(current)) {
                for (Class<?> type = current.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
                    Function<Throwable, String> message = MESSAGES.get(type);
                    if (message != null) {
                        return message.apply(current);
                    }
                    String key = "error." + type.getSimpleName();
                    if (I18n.global().has(key)) {
                        return I18n.global().get(key, current.getMessage() != null ? current.getMessage() : "");
                    }
                }
            }
            current = current.getCause();
        }
        for (current = error; current != null; current = current.getCause()) {
            if (!isWrapper(current) && current.getMessage() != null && !current.getMessage().trim().isEmpty()) {
                return current.getMessage();
            }
        }
        return error.getClass().getSimpleName();
    }

    /** Exceptions that only carry another one, e.g. from reflection or futures. */
    private static boolean isWrapper(Throwable error) {
        return error.getCause() != null && (error instanceof InvocationTargetException
                || error instanceof UndeclaredThrowableException || error instanceof ExecutionException
                || error instanceof CompletionException
                || (error instanceof RuntimeException && error.getClass() == RuntimeException.class
                && error.getMessage() != null && error.getMessage().equals(error.getCause().toString())));
    }
}
