package com.rapidswing.logviewer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileTest {

    @TempDir
    Path dir;

    private Path write(String name, String text) throws IOException {
        Path file = dir.resolve(name);
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static void append(Path file, String text) throws IOException {
        Files.write(file, text.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
    }

    @Test
    void stackTracesBelongToTheirEntry() throws IOException {
        Path file = write("app.log", "2026-10-23 10:00:00,000 [main] INFO  a.App - Starting\n"
                + "2026-10-23 10:00:01,000 [main] ERROR a.App - Failed\r\n"
                + "java.lang.IllegalStateException: boom\n"
                + "\n"
                + "\tat a.App.main(App.java:7)\n"
                + "2026-10-23 10:00:02,000 [main] INFO  a.App - Recovered\n");
        LogFile log = new LogFile(file, null);

        LogFile.Update update = log.read();

        assertSame(LogFormat.LOGBACK, log.getFormat());
        assertEquals(3, update.added.size());
        LogEntry failure = update.added.get(1);
        assertEquals("Failed\njava.lang.IllegalStateException: boom\n\n\tat a.App.main(App.java:7)", failure.getMessage());
        assertEquals("Failed ...", failure.getSummary());
        assertEquals(2, failure.getLineNumber());
        assertEquals(6, update.added.get(2).getLineNumber());
    }

    @Test
    void followsAppendedLinesIncludingPartialOnes() throws IOException {
        Path file = write("app.log", "[main] INFO a.App - one\n[main] ERROR a.App - two\n");
        LogFile log = new LogFile(file, LogFormat.SLF4J_SIMPLE);
        assertEquals(2, log.read().added.size());

        append(file, "java.lang.RuntimeException\n[main] INFO a.App - thr");
        LogFile.Update update = log.read();
        assertTrue(update.added.isEmpty());
        assertEquals("two\njava.lang.RuntimeException", update.extended.getMessage());

        append(file, "ee\n");
        update = log.read();
        assertEquals(1, update.added.size());
        assertEquals("three", update.added.get(0).getMessage());
        assertNull(update.extended);
        assertTrue(log.read().added.isEmpty());
    }

    @Test
    void multiByteCharactersSplitAcrossReadsStayIntact() throws IOException {
        Path file = dir.resolve("utf8.log");
        byte[] line = "[main] INFO a.App - Größe €\n".getBytes(StandardCharsets.UTF_8);
        int split = 0;
        while (line[split] >= 0) {
            split++;
        }
        split++; // Right after the first byte of the two-byte 'ö'.
        Files.write(file, Arrays.copyOfRange(line, 0, split));
        LogFile log = new LogFile(file, LogFormat.SLF4J_SIMPLE);
        assertTrue(log.read().added.isEmpty());

        Files.write(file, Arrays.copyOfRange(line, split, line.length), StandardOpenOption.APPEND);
        assertEquals("Größe €", log.read().added.get(0).getMessage());
    }

    @Test
    void rotatedFilesAreReadAgain() throws IOException {
        Path file = write("app.log", "[main] INFO a.App - old 1\n[main] INFO a.App - old 2\n");
        LogFile log = new LogFile(file, LogFormat.SLF4J_SIMPLE);
        log.read();

        write("app.log", "[main] INFO a.App - new\n");
        LogFile.Update update = log.read();

        assertTrue(update.reset);
        assertEquals(1, update.added.size());
        assertEquals(1, update.added.get(0).getLineNumber());
        assertFalse(log.read().reset);
    }

    @Test
    void entriesWithoutTimeSortAfterTheirPredecessor() throws IOException {
        Path file = write("app.log", "2026-10-23 10:00:00.000 [main] INFO  a.App - timed\nplain line\n");
        LogFile log = new LogFile(file, LogFormat.LOGBACK);

        LogEntry plain = log.read().added.get(0);

        assertEquals("timed\nplain line", plain.getMessage());
    }
}
