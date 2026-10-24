package com.rapidswing.logviewer;

import java.util.Locale;

/** Log levels of the common Java logging frameworks, in order of severity. */
public enum LogLevel {
    TRACE, DEBUG, INFO, WARN, ERROR, FATAL,
    /** The line did not name a level, e.g. plain text. Treated like {@link #INFO} when filtering. */
    UNKNOWN;

    /** Understands Log4j/Logback/SLF4J names as well as java.util.logging ones (FINE, WARNING, SEVERE...). */
    public static LogLevel parse(String text) {
        if (text == null) {
            return UNKNOWN;
        }
        switch (text.trim().toUpperCase(Locale.ROOT)) {
            case "TRACE":
            case "FINEST":
            case "FINER":
                return TRACE;
            case "DEBUG":
            case "FINE":
            case "CONFIG":
                return DEBUG;
            case "INFO":
                return INFO;
            case "WARN":
            case "WARNING":
                return WARN;
            case "ERROR":
            case "SEVERE":
                return ERROR;
            case "FATAL":
                return FATAL;
            default:
                return UNKNOWN;
        }
    }

    /** Whether entries of this level pass a "minimum level" filter. */
    public boolean isAtLeast(LogLevel minimum) {
        return effective().ordinal() >= minimum.effective().ordinal();
    }

    private LogLevel effective() {
        return this == UNKNOWN ? INFO : this;
    }
}
