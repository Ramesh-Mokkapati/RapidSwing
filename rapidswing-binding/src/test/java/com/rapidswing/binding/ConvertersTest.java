package com.rapidswing.binding;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvertersTest {

    @Test
    void integerParsesTrimmedTextAndBlankAsNull() throws ConversionException {
        Converter<Integer> converter = Converters.integer();

        assertEquals(42, converter.fromString(" 42 "));
        assertNull(converter.fromString("   "));
        assertEquals("", converter.toString(null));
        assertEquals("7", converter.toString(7));
    }

    @Test
    void invalidInputGivesUserFacingMessage() {
        ConversionException e = assertThrows(ConversionException.class,
                () -> Converters.integer().fromString("12a"));

        assertEquals("Enter a whole number", e.getMessage());
    }

    @Test
    void decimalKeepsPrecision() throws ConversionException {
        Converter<BigDecimal> converter = Converters.decimal();

        assertEquals(new BigDecimal("1234.50"), converter.fromString("1234.50"));
        assertEquals("1234.50", converter.toString(new BigDecimal("1234.50")));
    }

    @Test
    void localDateIsStrictIso() throws ConversionException {
        Converter<LocalDate> converter = Converters.localDate();

        assertEquals(LocalDate.of(2026, 10, 23), converter.fromString("2026-10-23"));
        ConversionException e = assertThrows(ConversionException.class, () -> converter.fromString("2026-02-30"));
        assertTrue(e.getMessage().startsWith("Enter a date like"));
    }

    @Test
    void stringConvertersHandleNull() throws ConversionException {
        assertEquals("", Converters.string().toString(null));
        assertEquals(" a ", Converters.string().fromString(" a "));
        assertEquals("a", Converters.trimmed().fromString(" a "));
    }
}
