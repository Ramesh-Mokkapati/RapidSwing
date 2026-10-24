package com.rapidswing.testing;

import com.rapidswing.task.TaskService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The test doubles: immediate tasks, scripted dialogs and in-memory preferences. */
class DoublesTest {

    @Test
    void immediateTasksHaveFinishedWhenUiReturns() {
        TaskService tasks = TestTasks.immediate();
        List<String> events = new ArrayList<>();

        Ui.onEdt(() -> tasks.call(() -> "loaded")
                .onSuccess(events::add)
                .onFinally(() -> events.add("finally"))
                .start());

        assertEquals(Arrays.asList("loaded", "finally"), events);
        assertEquals(0, (int) Ui.onEdt(() -> tasks.runningCount().get()));
    }

    @Test
    void scriptedDialogsRecordAndAnswer() {
        ScriptedDialogs dialogs = new ScriptedDialogs().answerConfirm(true, false).answerInput("Asha", null);

        dialogs.info("Saved");
        boolean first = Ui.onEdt(() -> dialogs.confirm("Delete Asha?"));
        boolean second = Ui.onEdt(() -> dialogs.confirm("Delete Liam?"));
        Optional<String> name = Ui.onEdt(() -> dialogs.input("Name?", ""));
        Optional<String> cancelled = Ui.onEdt(() -> dialogs.input("Name?", ""));

        assertTrue(first);
        assertFalse(second);
        assertEquals(Optional.of("Asha"), name);
        assertFalse(cancelled.isPresent());
        assertEquals("info: Saved", dialogs.messages().get(0));
        assertEquals("input: Name?", dialogs.lastMessage());
        dialogs.assertNoErrors();
    }

    @Test
    void unscriptedQuestionsAndErrorsFailTheTest() {
        ScriptedDialogs dialogs = new ScriptedDialogs();

        AssertionError unexpected = assertThrows(AssertionError.class, () -> dialogs.confirm("Discard changes?"));
        assertTrue(unexpected.getMessage().contains("answerConfirm"));

        dialogs.error("Could not save", new IllegalStateException("Database down"));
        AssertionError errors = assertThrows(AssertionError.class, dialogs::assertNoErrors);
        assertTrue(errors.getMessage().contains("error: Could not save (Database down)"));
    }

    @Test
    void memoryPreferencesStayInMemory() throws Exception {
        Preferences preferences = new MemoryPreferences();
        Preferences window = preferences.node("window");

        window.putInt("width", 800);
        preferences.put("locale", "de");

        assertEquals(800, window.getInt("width", 0));
        assertEquals("de", preferences.get("locale", null));
        assertEquals(Arrays.asList("window"), Arrays.asList(preferences.childrenNames()));
        assertEquals(null, new MemoryPreferences().get("locale", null));
    }
}
