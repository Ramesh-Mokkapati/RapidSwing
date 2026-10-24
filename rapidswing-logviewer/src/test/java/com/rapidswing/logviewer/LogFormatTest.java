package com.rapidswing.logviewer;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFormatTest {

    @Test
    void parsesSpringBoot2And3Lines() {
        LogEntry boot2 = LogFormat.SPRING_BOOT.parse("app.log", 1,
                "2026-10-23 17:29:01.123  INFO 4711 --- [           main] c.e.demo.DemoApplication : Started in 2.1 s");
        LogEntry boot3 = LogFormat.SPRING_BOOT.parse("app.log", 2,
                "2026-10-23T17:29:01.123+02:00 ERROR 4711 --- [demo] [nio-8080-exec-1] o.a.c.c.C.[dispatcherServlet] : Failed");

        assertEquals(LogLevel.INFO, boot2.getLevel());
        assertEquals("main", boot2.getThread());
        assertEquals("c.e.demo.DemoApplication", boot2.getLogger());
        assertEquals("Started in 2.1 s", boot2.getMessage());
        assertEquals(LocalDateTime.of(2026, 10, 23, 17, 29, 1, 123_000_000), boot2.getTime());
        assertEquals(LogLevel.ERROR, boot3.getLevel());
        assertEquals("nio-8080-exec-1", boot3.getThread());
        assertEquals("Failed", boot3.getMessage());
    }

    @Test
    void parsesLogbackAndSlf4jSimpleLines() {
        LogEntry logback = LogFormat.LOGBACK.parse("a.log", 1,
                "2026-10-23 17:29:01,500 [worker-2] WARN  com.acme.Billing - Retry 2 of 3");
        LogEntry simple = LogFormat.SLF4J_SIMPLE.parse("b.log", 1, "[main] INFO com.rapidswing.demo.DemoApp - Ready");

        assertEquals(LogLevel.WARN, logback.getLevel());
        assertEquals("worker-2", logback.getThread());
        assertEquals("Retry 2 of 3", logback.getMessage());
        assertEquals(LocalDateTime.of(2026, 10, 23, 17, 29, 1, 500_000_000), logback.getTime());
        assertEquals(LogLevel.INFO, simple.getLevel());
        assertEquals("", simple.getTimestamp());
        assertNull(simple.getTime());
        assertNull(LogFormat.LOGBACK.parse("a.log", 2, "\tat com.acme.Billing.charge(Billing.java:42)"));
    }

    @Test
    void detectsTheFormatFromSampleLines() {
        assertSame(LogFormat.LOGBACK, LogFormat.detect(Arrays.asList(
                "2026-10-23 17:29:01,500 [main] INFO  com.acme.App - Starting",
                "java.lang.IllegalStateException: boom",
                "2026-10-23 17:29:02,000 [main] ERROR com.acme.App - Failed")));
        assertSame(LogFormat.SLF4J_SIMPLE, LogFormat.detect(Arrays.asList("[main] INFO a.B - x", "[main] WARN a.B - y")));
        assertSame(LogFormat.PLAIN, LogFormat.detect(Arrays.asList("hello", "world")));
    }

    @Test
    void customFormatsNeedAMessageGroup() {
        LogFormat access = LogFormat.custom("Access", "^(?<thread>\\S+) - - \\[(?<timestamp>[^\\]]+)\\] (?<message>.*)$");
        LogEntry entry = access.parse("access.log", 1, "10.0.0.1 - - [23/Oct/2026:17:29:01] \"GET / HTTP/1.1\" 200");

        assertEquals("10.0.0.1", entry.getThread());
        assertEquals("\"GET / HTTP/1.1\" 200", entry.getMessage());
        assertEquals(LogLevel.UNKNOWN, entry.getLevel());
        assertThrows(IllegalArgumentException.class, () -> LogFormat.custom("x", "^(?<text>.*)$"));
        assertThrows(IllegalArgumentException.class, () -> LogFormat.custom("x", "^(?<message>.*$"));
    }

    @Test
    void levelsIncludeJavaUtilLoggingNames() {
        assertEquals(LogLevel.WARN, LogLevel.parse("WARNING"));
        assertEquals(LogLevel.ERROR, LogLevel.parse("severe"));
        assertEquals(LogLevel.DEBUG, LogLevel.parse("FINE"));
        assertEquals(LogLevel.UNKNOWN, LogLevel.parse("NOTICE"));
        assertTrue(LogLevel.UNKNOWN.isAtLeast(LogLevel.INFO));
        assertTrue(!LogLevel.INFO.isAtLeast(LogLevel.WARN));
    }
}
