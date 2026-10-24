package com.rapidswing.generator;

import com.rapidswing.rest.sample.SampleApiServer;
import com.rapidswing.testing.TestTasks;
import com.rapidswing.testing.Ui;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The command line and the wizard panel, driven like a user would. */
class GeneratorUiTest {

    @TempDir
    Path dir;

    private int runCli(ByteArrayOutputStream out, ByteArrayOutputStream err, String... args) {
        return GeneratorCli.run(args, new PrintStream(out, true), new PrintStream(err, true));
    }

    @Test
    void cliGeneratesAProject() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int code = runCli(out, err, "--type", "outlook", "--name", "Team Mail", "--features", "login,i18n",
                "--dir", dir.toString());

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertEquals(0, code, new String(err.toByteArray(), StandardCharsets.UTF_8));
        assertTrue(output.startsWith("Generated Outlook style application \"Team Mail\""));
        assertTrue(output.contains("Sign-in account: admin / "));
        assertTrue(Files.exists(dir.resolve("team-mail/src/main/java/com/example/teammail/OutlookView.java")));
    }

    @Test
    void cliReportsMistakes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        assertEquals(2, runCli(out, err, "--type", "wizard", "--name", "X"));
        assertTrue(new String(err.toByteArray(), StandardCharsets.UTF_8).contains("use sdi, mdi, dialog or outlook"));
        assertEquals(2, runCli(out, err, "--name"));
        assertEquals(2, runCli(out, err, "--colour", "blue"));
        assertEquals(2, runCli(out, err, "--type", "sdi"));
        assertEquals(0, runCli(out, err, "--help"));
    }

    @Test
    void wizardWalksThroughTheStepsAndGenerates() throws Exception {
        try (SampleApiServer api = SampleApiServer.start(0)) {
            List<ProjectGenerator.Result> generated = new ArrayList<>();
            WizardPanel wizard = Ui.onEdt(() -> new WizardPanel(TestTasks.immediate(), generated::add, () -> { }));

            Ui.onEdt(() -> wizard.selectType(AppType.MDI));
            Ui.onEdt(wizard::next);
            assertEquals(1, wizard.getStep());

            Ui.onEdt(wizard::next);
            assertEquals(1, wizard.getStep(), "a name is required");
            assertEquals("Enter an application name.", wizard.getProblemText());

            Ui.onEdt(() -> {
                wizard.nameProperty().set("Order Desk");
                wizard.directoryProperty().set(dir.toString());
            });
            Ui.onEdt(wizard::next);
            assertEquals(2, wizard.getStep());

            Ui.onEdt(() -> {
                wizard.featureProperty(ProjectSpec.Feature.LOG_VIEWER).set(true);
                wizard.restProperty().set(true);
                wizard.openApiProperty().set(api.getBaseUrl());
            });
            Ui.onEdt(wizard::checkApi);
            assertEquals("Found Customers, Products, Orders in Sample Shop API", wizard.getApiStatus());
            Ui.onEdt(wizard::next);

            assertEquals(3, wizard.getStep());
            String review = wizard.getSummaryText();
            assertTrue(review.contains("Multiple documents (MDI)"));
            assertTrue(review.contains("src/main/java/com/example/orderdesk/rest/ui/CustomerScreen.java"));

            Ui.onEdt(wizard::next);
            ProjectGenerator.Result result = wizard.getResult();
            assertNotNull(result, wizard.getProblemText());
            assertEquals(1, generated.size());
            assertTrue(Files.exists(dir.resolve("order-desk/src/main/java/com/example/orderdesk/WorkspaceView.java")));
            assertTrue(wizard.getSummaryText().contains("REST screens: Customers, Products, Orders"));
        }
    }
}
