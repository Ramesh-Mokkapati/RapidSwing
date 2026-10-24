package com.rapidswing.logviewer;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One log event: usually one line, plus any continuation lines such as a stack trace.
 */
public final class LogEntry {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private final String source;
    private final int lineNumber;
    private final String timestamp;
    private final LocalDateTime time;
    private final LogLevel level;
    private final String thread;
    private final String logger;
    private final StringBuilder message;
    private final long sequence = SEQUENCE.incrementAndGet();
    /** The file this entry was read from; set by {@link LogFile}. */
    LogFile origin;
    /** The entry's time, or the time of the closest earlier entry in its file, for merging files in order. */
    LocalDateTime sortTime;

    LogEntry(String source, int lineNumber, String timestamp, LocalDateTime time, LogLevel level, String thread,
             String logger, String message) {
        this.source = source;
        this.lineNumber = lineNumber;
        this.timestamp = timestamp != null ? timestamp : "";
        this.time = time;
        this.level = level;
        this.thread = thread != null ? thread : "";
        this.logger = logger != null ? logger : "";
        this.message = new StringBuilder(message != null ? message : "");
    }

    /** The file the entry came from. */
    public String getSource() {
        return source;
    }

    /** Line number of the entry's first line in its file, starting at 1. */
    public int getLineNumber() {
        return lineNumber;
    }

    /** The timestamp as written in the file. */
    public String getTimestamp() {
        return timestamp;
    }

    /** The parsed timestamp, used to merge files in time order; {@code null} if it could not be parsed. */
    public LocalDateTime getTime() {
        return time;
    }

    public LogLevel getLevel() {
        return level;
    }

    public String getThread() {
        return thread;
    }

    public String getLogger() {
        return logger;
    }

    /** The full message, including continuation lines. */
    public synchronized String getMessage() {
        return message.toString();
    }

    /** The first line of the message, for one-line display. */
    public synchronized String getSummary() {
        int newline = message.indexOf("\n");
        return newline < 0 ? message.toString() : message.substring(0, newline) + " ...";
    }

    /** Order of reading, used to keep the file order for entries with equal or missing timestamps. */
    long getSequence() {
        return sequence;
    }

    synchronized void appendLine(String line) {
        message.append('\n').append(line);
    }

    @Override
    public String toString() {
        return timestamp + " " + level + " " + logger + " - " + getSummary();
    }
}
