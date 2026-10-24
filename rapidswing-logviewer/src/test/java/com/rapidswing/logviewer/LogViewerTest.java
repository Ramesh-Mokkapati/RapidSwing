package com.rapidswing.logviewer;

import com.formdev.flatlaf.FlatClientProperties;
import com.rapidswing.testing.Find;
import com.rapidswing.testing.TestTasks;
import com.rapidswing.testing.Ui;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogViewerTest {

    @TempDir
    Path dir;

    private Path web;
    private Path worker;
    private LogViewer viewer;

    @BeforeEach
    void setUp() throws IOException {
        web = dir.resolve("web.log");
        worker = dir.resolve("worker.log");
        Files.write(web, ("2026-10-23 10:00:00,000 [http-1] INFO  web.Api - GET /orders\n"
                + "2026-10-23 10:00:03,000 [http-2] ERROR web.Api - Order 42 failed\n"
                + "java.lang.IllegalStateException: Payment declined for order 42\n"
                + "\tat web.Api.pay(Api.java:9)\n").getBytes(StandardCharsets.UTF_8));
        Files.write(worker, ("2026-10-23 10:00:01,000 [job-1] WARN  jobs.Billing - Slow response\n"
                + "2026-10-23 10:00:02,000 [job-1] DEBUG jobs.Billing - Charging order 42\n").getBytes(StandardCharsets.UTF_8));
        Ui.onEdt(() -> viewer = new LogViewer(TestTasks.immediate()));
        Ui.onEdt(() -> viewer.open(web, worker));
    }

    private List<String> visibleMessages() {
        return Ui.onEdt(() -> {
            List<String> messages = new ArrayList<>();
            for (LogEntry entry : viewer.visibleEntries()) {
                messages.add(entry.getSource() + ": " + entry.getSummary());
            }
            return messages;
        });
    }

    @Test
    void mergesFilesInTimeOrder() {
        List<String> expected = new ArrayList<>();
        expected.add("web.log: GET /orders");
        expected.add("worker.log: Slow response");
        expected.add("worker.log: Charging order 42");
        expected.add("web.log: Order 42 failed ...");

        assertEquals(expected, visibleMessages());
        assertEquals("4 of 4 entries in 2 files · 1 error · 1 warning", Ui.onEdt(viewer::getStatusText));
    }

    @Test
    void filtersByMinimumLevel() {
        Ui.onEdt(() -> viewer.setMinimumLevel(LogLevel.WARN));

        assertEquals(2, visibleMessages().size());
        assertTrue(Ui.onEdt(viewer::getStatusText).startsWith("2 of 4 entries"));
    }

    @Test
    void searchesWithRegexIncludingStackTraces() {
        Ui.onEdt(() -> viewer.setSearch("declined for order \\d+", true, false));

        assertEquals(1, visibleMessages().size());

        Ui.onEdt(() -> viewer.getTable().setRowSelectionInterval(0, 0));
        String detail = Ui.onEdt(viewer::getDetailText);
        assertTrue(detail.contains("(web.log:2)"));
        assertTrue(detail.contains("\tat web.Api.pay(Api.java:9)"));
        JTextArea detailArea = Find.in(viewer).all(JTextArea.class).get(0);
        assertEquals(1, (int) Ui.onEdt(() -> detailArea.getHighlighter().getHighlights().length));
    }

    @Test
    void plainSearchIsLiteralAndInvalidRegexIsFlagged() {
        Ui.onEdt(() -> viewer.setSearch("order 42", false, false));
        assertEquals(2, visibleMessages().size());

        Ui.onEdt(() -> viewer.setSearch("order (", true, false));
        assertEquals(2, visibleMessages().size(), "an invalid pattern keeps the previous filter");
        JTextField field = Find.in(viewer).all(JTextField.class).get(0);
        assertEquals(FlatClientProperties.OUTLINE_ERROR, field.getClientProperty(FlatClientProperties.OUTLINE));
        assertNotNull(field.getToolTipText());
    }

    @Test
    void refreshPicksUpAppendedEntries() throws IOException {
        Files.write(worker, "2026-10-23 10:00:04,000 [job-2] INFO  jobs.Billing - Done\n".getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.APPEND);

        Ui.onEdt(viewer::refresh);

        List<String> messages = visibleMessages();
        assertEquals(5, messages.size());
        assertEquals("worker.log: Done", messages.get(4));
    }

    @Test
    void customFormatReReadsTheFiles() {
        Ui.onEdt(() -> viewer.setFormat(LogFormat.custom("Words", "^(?<timestamp>\\S+ \\S+) \\[(?<thread>[^\\]]+)\\] (?<message>.*)$")));

        List<String> messages = visibleMessages();
        assertEquals(4, messages.size());
        assertTrue(messages.contains("worker.log: WARN  jobs.Billing - Slow response"));
    }
}
