package com.rapidswing.logviewer;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How to read a log line: a regular expression with named groups {@code timestamp}, {@code level},
 * {@code thread}, {@code logger} and {@code message}. Only {@code message} is required. Lines that do not
 * match are continuation lines (e.g. stack traces) of the previous entry.
 * <pre>
 * LogFormat access = LogFormat.custom("Access log",
 *         "^(?&lt;thread&gt;\\S+) \\S+ \\S+ \\[(?&lt;timestamp&gt;[^\\]]+)\\] (?&lt;message&gt;.*)$");
 * </pre>
 */
public final class LogFormat {

    private static final String DATE_TIME = "\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{1,9})?";

    /** Spring Boot's default console and file format, e.g. {@code 2026-10-23 17:29:01.123  INFO 4711 --- [main] c.e.App : Started}. */
    public static final LogFormat SPRING_BOOT = new LogFormat("Spring Boot",
            "^(?<timestamp>" + DATE_TIME + "(?:Z|[+-]\\d{2}:?\\d{2})?)\\s+(?<level>[A-Z]+)\\s+\\d+\\s+---\\s+"
                    + "(?:\\[[^\\]]*\\]\\s+)?\\[\\s*(?<thread>[^\\]]*?)\\]\\s+(?<logger>\\S+)\\s*:\\s?(?<message>.*)$");

    /** The common Logback/Log4j pattern {@code %d [%thread] %-5level %logger - %msg}. */
    public static final LogFormat LOGBACK = new LogFormat("Logback / Log4j",
            "^(?<timestamp>" + DATE_TIME + ")\\s+\\[(?<thread>[^\\]]*)\\]\\s+(?<level>[A-Z]+)\\s+(?<logger>\\S+)"
                    + "\\s+-\\s?(?<message>.*)$");

    /** SLF4J Simple, e.g. {@code [main] INFO com.example.App - Started}, optionally with a time in front. */
    public static final LogFormat SLF4J_SIMPLE = new LogFormat("SLF4J Simple",
            "^(?:(?<timestamp>" + DATE_TIME + "|\\d+)\\s+)?\\[(?<thread>[^\\]]*)\\]\\s+"
                    + "(?<level>TRACE|DEBUG|INFO|WARN|ERROR)\\s+(?<logger>\\S+)\\s+-\\s?(?<message>.*)$");

    /** Every line is an entry. */
    public static final LogFormat PLAIN = new LogFormat("Plain text", "^(?<message>.*)$");

    private static final List<LogFormat> PRESETS =
            Collections.unmodifiableList(Arrays.asList(SPRING_BOOT, LOGBACK, SLF4J_SIMPLE, PLAIN));
    private static final Pattern TIME_ZONE = Pattern.compile("(?:Z|[+-]\\d{2}:?\\d{2})$");

    private final String name;
    private final Pattern pattern;

    private LogFormat(String name, String regex) {
        this.name = Objects.requireNonNull(name, "name");
        this.pattern = Pattern.compile(regex);
    }

    public static List<LogFormat> presets() {
        return PRESETS;
    }

    /**
     * A format from your own regular expression.
     *
     * @throws IllegalArgumentException if the expression is invalid or has no group named {@code message}
     */
    public static LogFormat custom(String name, String regex) {
        LogFormat format = new LogFormat(name, regex);
        if (!regex.contains("(?<message>")) {
            throw new IllegalArgumentException("The expression needs a group named 'message': (?<message>...)");
        }
        return format;
    }

    /** The preset matching most of the sample lines; {@link #PLAIN} if none matches. */
    public static LogFormat detect(List<String> sampleLines) {
        LogFormat best = PLAIN;
        int bestMatches = 0;
        for (LogFormat format : PRESETS) {
            if (format == PLAIN) {
                continue;
            }
            int matches = 0;
            for (String line : sampleLines) {
                if (format.pattern.matcher(line).matches()) {
                    matches++;
                }
            }
            if (matches > bestMatches) {
                best = format;
                bestMatches = matches;
            }
        }
        return best;
    }

    public String getName() {
        return name;
    }

    public String getRegex() {
        return pattern.pattern();
    }

    /** Parses a line that starts an entry, or returns {@code null} for a continuation line. */
    LogEntry parse(String source, int lineNumber, String line) {
        Matcher matcher = pattern.matcher(line);
        if (!matcher.matches()) {
            return null;
        }
        String timestamp = group(matcher, "timestamp");
        return new LogEntry(source, lineNumber, timestamp, parseTime(timestamp),
                LogLevel.parse(group(matcher, "level")), group(matcher, "thread"), group(matcher, "logger"),
                group(matcher, "message"));
    }

    /** ISO-like timestamps with a space or {@code T}, a dot or comma before the fraction, and an optional zone. */
    static LocalDateTime parseTime(String timestamp) {
        if (timestamp == null || timestamp.length() < 19) {
            return null;
        }
        String normalized = TIME_ZONE.matcher(timestamp.trim()).replaceFirst("").replace(',', '.').replace(' ', 'T');
        try {
            return LocalDateTime.parse(normalized);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String group(Matcher matcher, String name) {
        try {
            return matcher.group(name);
        } catch (IllegalArgumentException noSuchGroup) {
            return null;
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
