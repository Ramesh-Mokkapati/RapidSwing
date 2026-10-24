package com.rapidswing.logviewer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Reads a log file incrementally: the first {@link #read()} parses the whole file, later calls only what was
 * appended since, which is how the viewer follows growing files. A file that became shorter (rotated or
 * truncated) is read again from the start.
 * <p>
 * Lines are decoded as UTF-8. A last line without a line break is held back until it is complete.
 */
public final class LogFile {

    private static final int SAMPLE_BYTES = 64 * 1024;
    private static final int SAMPLE_LINES = 50;

    private final Path path;
    private final String name;
    private LogFormat format;
    private long position;
    private byte[] pending = new byte[0];
    private int lineNumber;
    private LogEntry last;
    private LocalDateTime lastKnownTime;

    /** What one {@link #read()} found. */
    public static final class Update {
        /** True if the file was read again from the start: drop all earlier entries of this file. */
        public final boolean reset;
        public final List<LogEntry> added;
        /** An earlier entry that got continuation lines (e.g. the rest of a stack trace), or {@code null}. */
        public final LogEntry extended;

        Update(boolean reset, List<LogEntry> added, LogEntry extended) {
            this.reset = reset;
            this.added = Collections.unmodifiableList(added);
            this.extended = extended;
        }
    }

    /** @param format the format, or {@code null} to detect it from the start of the file */
    public LogFile(Path path, LogFormat format) {
        this.path = path;
        this.name = path.getFileName().toString();
        this.format = format;
    }

    public Path getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    /** The format in use; detected on the first read if none was given. */
    public synchronized LogFormat getFormat() {
        return format;
    }

    /** Reads what is new since the last call. */
    public synchronized Update read() throws IOException {
        if (format == null) {
            format = LogFormat.detect(sampleLines());
        }
        long size = Files.size(path);
        boolean reset = false;
        if (size < position) {
            reset = true;
            position = 0;
            pending = new byte[0];
            lineNumber = 0;
            last = null;
            lastKnownTime = null;
        }
        if (size == position) {
            return new Update(reset, Collections.<LogEntry>emptyList(), null);
        }
        byte[] data = readFrom(position, size - position);
        position += data.length;
        byte[] combined = concat(pending, data);
        int end = lastIndexOf(combined, (byte) '\n');
        if (end < 0) {
            pending = combined;
            return new Update(reset, Collections.<LogEntry>emptyList(), null);
        }
        pending = Arrays.copyOfRange(combined, end + 1, combined.length);
        return parse(new String(combined, 0, end + 1, StandardCharsets.UTF_8), reset);
    }

    private Update parse(String text, boolean reset) {
        List<LogEntry> added = new ArrayList<>();
        LogEntry extended = null;
        LogEntry previous = last;
        String[] lines = text.split("\n", -1);
        // The text always ends with a line break, so the last element is the empty string after it.
        for (int i = 0; i < lines.length - 1; i++) {
            String line = lines[i];
            String content = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
            lineNumber++;
            LogEntry entry = format.parse(name, lineNumber, content);
            if (entry == null && last != null) {
                last.appendLine(content);
                if (last == previous) {
                    extended = previous;
                }
            } else {
                if (entry == null) {
                    entry = LogFormat.PLAIN.parse(name, lineNumber, content);
                }
                entry.origin = this;
                if (entry.getTime() != null) {
                    lastKnownTime = entry.getTime();
                }
                entry.sortTime = lastKnownTime;
                added.add(entry);
                last = entry;
            }
        }
        return new Update(reset, added, extended);
    }

    private List<String> sampleLines() throws IOException {
        byte[] start = new byte[SAMPLE_BYTES];
        int length;
        try (InputStream in = Files.newInputStream(path)) {
            length = Math.max(0, in.read(start));
        }
        List<String> lines = new ArrayList<>();
        for (String line : new String(start, 0, length, StandardCharsets.UTF_8).split("\r?\n")) {
            if (lines.size() == SAMPLE_LINES) {
                break;
            }
            lines.add(line);
        }
        return lines;
    }

    private byte[] readFrom(long offset, long count) throws IOException {
        if (count > Integer.MAX_VALUE - 8) {
            throw new IOException(name + " is too large to read at once");
        }
        ByteBuffer buffer = ByteBuffer.allocate((int) count);
        try (SeekableByteChannel channel = Files.newByteChannel(path)) {
            channel.position(offset);
            while (buffer.hasRemaining() && channel.read(buffer) > 0) {
                // Keep reading until the requested bytes are in.
            }
        }
        return Arrays.copyOf(buffer.array(), buffer.position());
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static int lastIndexOf(byte[] bytes, byte value) {
        for (int i = bytes.length - 1; i >= 0; i--) {
            if (bytes[i] == value) {
                return i;
            }
        }
        return -1;
    }
}
