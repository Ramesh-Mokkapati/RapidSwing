package com.rapidswing.forms;

import com.rapidswing.property.Property;
import com.rapidswing.task.TaskService;
import com.rapidswing.util.Edt;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormExtrasTest {

    enum Theme { LIGHT, DARK }

    /** Runs tasks on the calling thread; callbacks still arrive on a later EDT turn. */
    static final class DirectExecutor extends AbstractExecutorService {
        private boolean shutdown;

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return new ArrayList<>();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }

    private static void onEdt(Runnable code) {
        Edt.runAndWait(code);
        Edt.runAndWait(() -> { });
        Edt.runAndWait(() -> { });
    }

    @Test
    void warningsShowButDoNotBlock() {
        Edt.runAndWait(() -> {
            Property<Integer> discount = Property.of(10);
            Form form = Form.builder()
                    .integer("Discount", discount)
                    .warn(value -> value != null && value > 30 ? "Unusually high discount" : null)
                    .validate(value -> value != null && value > 90 ? "At most 90" : null)
                    .build();
            JTextField field = (JTextField) form.component("Discount");

            field.setText("40");
            assertTrue(form.valid().get());
            assertEquals("Unusually high discount", form.warningText("Discount"));
            assertNull(form.errorText("Discount"));
            assertEquals(Collections.singletonList("Discount: Unusually high discount"), form.warnings());

            field.setText("95");
            assertFalse(form.valid().get());
            assertEquals("At most 90", form.errorText("Discount"));
            assertNull(form.warningText("Discount"));
        });
    }

    @Test
    void wizardRunsStepsAndFollowsOutcomes() {
        List<String> log = new ArrayList<>();
        boolean[] skipDetails = {false};
        Property<String> name = Property.of("");
        Wizard[] holder = new Wizard[1];
        onEdt(() -> holder[0] = Wizard.builder("New customer", new TaskService(new DirectExecutor()))
                .page("name", "Name", "Who is the customer?", Form.builder().text("Name", name).required().build())
                    .onNext(progress -> {
                        progress.begin("Checking", 1);
                        log.add("checked " + name.get());
                        if (name.get().equals("Taken")) {
                            return Outcome.error("This name is taken");
                        }
                        return skipDetails[0] ? Outcome.jump("done") : Outcome.next();
                    })
                .page("details", "Details", null, new JLabel("details"))
                    .onEnter(() -> log.add("details shown"))
                .page("done", "Done", null, new JLabel("done"))
                .onFinish(progress -> {
                    log.add("saved");
                    return Outcome.next();
                })
                .build());
        Wizard wizard = holder[0];

        onEdt(wizard::next);
        assertEquals("name", wizard.currentPageId());
        assertEquals("Please fix the highlighted fields.", wizard.getMessage());
        assertTrue(log.isEmpty());

        onEdt(() -> name.set("Taken"));
        onEdt(wizard::next);
        assertEquals("name", wizard.currentPageId());
        assertEquals("This name is taken", wizard.getMessage());

        onEdt(() -> name.set("Asha"));
        onEdt(wizard::next);
        assertEquals("details", wizard.currentPageId());
        onEdt(wizard::back);
        assertEquals("name", wizard.currentPageId());

        skipDetails[0] = true;
        onEdt(wizard::next);
        assertEquals("done", wizard.currentPageId());
        assertTrue(wizard.getFinishButton().isEnabled());
        onEdt(wizard::back);
        assertEquals("name", wizard.currentPageId(), "back returns to the page before the jump");
        onEdt(wizard::next);
        onEdt(wizard::finish);

        assertTrue(wizard.isFinished());
        assertEquals(java.util.Arrays.asList("checked Taken", "checked Asha", "details shown", "checked Asha",
                "checked Asha", "saved"), log);
    }

    @Test
    void settingsPersistValidateAndMoveBetweenComputers() throws Exception {
        Preferences store = new TestPreferences();
        Settings settings = new Settings(store);
        Property<Integer> pageSize = settings.category("Lists").integer("list.pageSize", "Rows per page", 50, 10, 500);
        Property<Theme> theme = settings.category("Look").choice("look.theme", "Theme", Theme.LIGHT);
        Property<Boolean> confirm = settings.bool("exit.confirm", "Ask before exiting", true);

        pageSize.set(100);
        theme.set(Theme.DARK);
        assertEquals("100", store.get("list.pageSize", null));
        pageSize.set(5000);
        assertEquals(100, (int) pageSize.get(), "out-of-range values are refused");

        Settings restarted = new Settings(store);
        assertEquals(100, (int) restarted.integer("list.pageSize", "Rows per page", 50, 10, 500).get());

        ByteArrayOutputStream exported = new ByteArrayOutputStream();
        settings.exportTo(exported);
        settings.reset();
        assertEquals(50, (int) pageSize.get());
        assertEquals(3, settings.importFrom(new ByteArrayInputStream(exported.toByteArray())));
        assertEquals(Theme.DARK, theme.get());

        String older = "list.pageSize=nonsense\nlook.theme=SEPIA\nexit.confirm=false\nremoved.setting=1\n";
        assertEquals(1, settings.importFrom(new ByteArrayInputStream(older.getBytes(StandardCharsets.ISO_8859_1))));
        assertFalse(confirm.get());
        assertEquals(100, (int) pageSize.get());
    }

    @Test
    void settingsDialogEditsCopiesUntilApplied() {
        Settings settings = new Settings(new TestPreferences());
        Property<Boolean> confirm = settings.bool("exit.confirm", "Ask before exiting", true);
        Property<Theme> theme = settings.category("Look").choice("look.theme", "Theme", Theme.LIGHT);
        Edt.runAndWait(() -> {
            SettingsDialog dialog = new SettingsDialog(settings);
            ((JCheckBox) dialog.component("exit.confirm")).doClick();
            ((JComboBox<?>) dialog.component("look.theme")).setSelectedItem(Theme.DARK);
            assertTrue(confirm.get());
            assertEquals(Theme.LIGHT, theme.get());

            assertTrue(dialog.apply());
            assertFalse(confirm.get());
            assertEquals(Theme.DARK, theme.get());

            dialog.resetToDefaults();
            assertEquals(Theme.LIGHT, dialog.shownValue("look.theme"));
            assertEquals(Theme.DARK, theme.get());
        });
    }
}
