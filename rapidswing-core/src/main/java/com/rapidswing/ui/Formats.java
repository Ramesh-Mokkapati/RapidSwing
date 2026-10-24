package com.rapidswing.ui;

import com.rapidswing.i18n.I18n;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * How values are shown to users, application-wide. Grids, combo boxes, CSV
 * export and search use it for values without a format of their own.
 * <pre>
 * Formats.register(Money.class, money -&gt; money.getCurrency() + " " + money.getAmount());
 * </pre>
 * Enum constants are translated: {@code OrderState.IN_PROGRESS} looks up {@code enum.OrderState.IN_PROGRESS}, and
 * without a translation becomes "In progress". Dates follow the current language.
 */
public final class Formats {

    private static final Map<Class<?>, Function<Object, String>> FORMATTERS = new ConcurrentHashMap<>();

    private Formats() {
    }

    @SuppressWarnings("unchecked")
    public static <T> void register(Class<T> type, Function<? super T, String> formatter) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(formatter, "formatter");
        FORMATTERS.put(type, value -> formatter.apply((T) value));
    }

    public static void unregister(Class<?> type) {
        FORMATTERS.remove(type);
    }

    /** Whether values of this type get special display text (a registered format, an enum or a date). */
    public static boolean handles(Class<?> type) {
        return type.isEnum() || type == LocalDate.class || type == LocalDateTime.class || find(type) != null;
    }

    /** The display text of a value; {@code null} becomes an empty string. */
    public static String format(Object value) {
        if (value == null) {
            return "";
        }
        Function<Object, String> formatter = find(value.getClass());
        if (formatter != null) {
            return formatter.apply(value);
        }
        Locale locale = I18n.global().getLocale();
        if (value instanceof Enum) {
            return enumText((Enum<?>) value);
        }
        if (value instanceof LocalDate) {
            return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format((LocalDate) value);
        }
        if (value instanceof LocalDateTime) {
            return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
                    .format((LocalDateTime) value);
        }
        return String.valueOf(value);
    }

    /** {@code enum.<SimpleName>.<CONSTANT>} if translated, else the constant as words: "IN_PROGRESS" to "In progress". */
    public static String enumText(Enum<?> value) {
        String key = "enum." + value.getDeclaringClass().getSimpleName() + "." + value.name();
        if (I18n.global().has(key)) {
            return I18n.global().get(key);
        }
        String words = value.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return words.isEmpty() ? words : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /** The formatter for the type, its superclasses or its interfaces, nearest first. */
    private static Function<Object, String> find(Class<?> type) {
        Deque<Class<?>> pending = new ArrayDeque<>();
        Set<Class<?>> seen = new HashSet<>();
        pending.add(type);
        while (!pending.isEmpty()) {
            Class<?> candidate = pending.poll();
            if (candidate == null || !seen.add(candidate)) {
                continue;
            }
            Function<Object, String> formatter = FORMATTERS.get(candidate);
            if (formatter != null) {
                return formatter;
            }
            if (candidate.getSuperclass() != null) {
                pending.add(candidate.getSuperclass());
            }
            for (Class<?> anInterface : candidate.getInterfaces()) {
                pending.add(anInterface);
            }
        }
        return null;
    }
}
