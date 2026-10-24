package com.rapidswing.testing;

import com.rapidswing.ui.Dialogs;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * {@link Dialogs} that never open a window: messages are recorded, and questions are answered from a script.
 * <pre>
 * dialogs.answerConfirm(true);               // the next "Delete Asha?" is answered Yes
 * Ui.click(find.button("Delete"));
 * assertEquals("confirm: Delete Asha?", dialogs.messages().get(0));
 * </pre>
 * A question without a scripted answer fails the test, so unexpected dialogs cannot go unnoticed.
 * Recorded entries look like {@code "info: Saved"}, {@code "warn: ..."}, {@code "error: message (cause)"},
 * {@code "confirm: ..."} and {@code "input: ..."}.
 */
public class ScriptedDialogs extends Dialogs {

    private final List<String> messages = Collections.synchronizedList(new ArrayList<>());
    private final Deque<Boolean> confirmAnswers = new ArrayDeque<>();
    private final Deque<Optional<String>> inputAnswers = new ArrayDeque<>();

    public ScriptedDialogs() {
        super(() -> null);
    }

    /** Answers the next confirmations, in order: {@code true} is Yes. */
    public synchronized ScriptedDialogs answerConfirm(boolean... answers) {
        for (boolean answer : answers) {
            confirmAnswers.add(answer);
        }
        return this;
    }

    /** Answers the next input prompts, in order; {@code null} means the user cancelled. */
    public synchronized ScriptedDialogs answerInput(String... answers) {
        for (String answer : answers) {
            inputAnswers.add(Optional.ofNullable(answer));
        }
        return this;
    }

    /** Everything shown so far, oldest first. */
    public List<String> messages() {
        synchronized (messages) {
            return new ArrayList<>(messages);
        }
    }

    /** The most recent entry, or {@code null}. */
    public String lastMessage() {
        synchronized (messages) {
            return messages.isEmpty() ? null : messages.get(messages.size() - 1);
        }
    }

    public void clear() {
        messages.clear();
    }

    /** Fails if any error dialog was shown, listing them. */
    public void assertNoErrors() {
        List<String> errors = new ArrayList<>();
        for (String message : messages()) {
            if (message.startsWith("error: ")) {
                errors.add(message);
            }
        }
        if (!errors.isEmpty()) {
            throw new AssertionError("Unexpected error dialogs: " + errors);
        }
    }

    @Override
    public void info(String title, String message) {
        messages.add("info: " + message);
    }

    @Override
    public void warn(String message) {
        messages.add("warn: " + message);
    }

    @Override
    public void error(String message) {
        messages.add("error: " + message);
    }

    @Override
    public void error(String message, Throwable error) {
        messages.add("error: " + message + " (" + error.getMessage() + ")");
    }

    @Override
    public synchronized boolean confirm(String title, String message) {
        messages.add("confirm: " + message);
        Boolean answer = confirmAnswers.poll();
        if (answer == null) {
            throw new AssertionError("Unexpected confirmation '" + message + "': script it with answerConfirm(...)");
        }
        return answer;
    }

    @Override
    public synchronized Optional<String> input(String message, String initialValue) {
        messages.add("input: " + message);
        Optional<String> answer = inputAnswers.poll();
        if (answer == null) {
            throw new AssertionError("Unexpected input prompt '" + message + "': script it with answerInput(...)");
        }
        return answer;
    }
}
