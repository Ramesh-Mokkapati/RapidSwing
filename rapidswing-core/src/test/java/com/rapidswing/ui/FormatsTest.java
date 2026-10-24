package com.rapidswing.ui;

import com.rapidswing.i18n.I18n;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormatsTest {

    enum OrderState { NEW, IN_PROGRESS }

    interface Labelled {
        String label();
    }

    static final class Thing implements Labelled {
        @Override
        public String label() {
            return "a thing";
        }
    }

    static final class StaleDataException extends RuntimeException {
        StaleDataException(String message) {
            super(message);
        }
    }

    @AfterEach
    void cleanUp() {
        Formats.unregister(Labelled.class);
        ErrorMessages.unregister(StaleDataException.class);
        I18n.global().setLocale(Locale.ENGLISH);
    }

    @Test
    void enumsBecomeWordsUnlessTranslated() {
        assertEquals("In progress", Formats.format(OrderState.IN_PROGRESS));
        assertEquals("New", Formats.enumText(OrderState.NEW));
        assertTrue(Formats.handles(OrderState.class));
    }

    @Test
    void registeredFormatsApplyToSubtypes() {
        assertFalse(Formats.handles(Thing.class));
        Formats.register(Labelled.class, Labelled::label);

        assertTrue(Formats.handles(Thing.class));
        assertEquals("a thing", Formats.format(new Thing()));
        assertEquals("", Formats.format(null));
        assertEquals("42", Formats.format(42));
    }

    @Test
    void datesFollowTheLanguage() {
        LocalDate date = LocalDate.of(2026, 3, 9);
        assertEquals("Mar 9, 2026", Formats.format(date));
        I18n.global().setLocale(Locale.GERMAN);
        assertEquals("09.03.2026", Formats.format(date));
    }

    @Test
    void errorMessagesLookThroughWrappers() {
        StaleDataException stale = new StaleDataException("version 3 is not 4");
        Exception wrapped = new ExecutionException(new InvocationTargetException(stale));

        assertEquals("version 3 is not 4", ErrorMessages.messageFor(wrapped));
        ErrorMessages.register(StaleDataException.class, e -> "Someone else changed this record.");
        assertEquals("Someone else changed this record.", ErrorMessages.messageFor(wrapped));
        assertEquals("NullPointerException", ErrorMessages.messageFor(new NullPointerException()));
        assertEquals("", ErrorMessages.messageFor(null));
    }
}
