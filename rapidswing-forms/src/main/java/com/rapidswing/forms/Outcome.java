package com.rapidswing.forms;

import java.util.Objects;

/**
 * What a {@link Wizard} step decides: go on, go to another page, stay with an
 * error, or finish.
 */
public final class Outcome {

    enum Kind { NEXT, JUMP, ERROR, FINISH }

    private static final Outcome NEXT = new Outcome(Kind.NEXT, null);
    private static final Outcome FINISH = new Outcome(Kind.FINISH, null);

    final Kind kind;
    final String detail;

    private Outcome(Kind kind, String detail) {
        this.kind = kind;
        this.detail = detail;
    }

    /** Continue with the next page (or finish, after the last one). */
    public static Outcome next() {
        return NEXT;
    }

    /** Continue with the page of this id, e.g. to skip pages that do not apply. */
    public static Outcome jump(String pageId) {
        return new Outcome(Kind.JUMP, Objects.requireNonNull(pageId, "pageId"));
    }

    /** Stay on the page and show the message. */
    public static Outcome error(String message) {
        return new Outcome(Kind.ERROR, Objects.requireNonNull(message, "message"));
    }

    /** Close the wizard as finished, skipping the remaining pages. */
    public static Outcome finish() {
        return FINISH;
    }

    @Override
    public String toString() {
        return detail == null ? kind.toString() : kind + "(" + detail + ")";
    }
}
