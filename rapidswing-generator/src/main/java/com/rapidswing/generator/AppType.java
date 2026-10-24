package com.rapidswing.generator;

import java.util.Locale;

/** The application styles the generator offers, after MFC's application wizard plus an Outlook-style shell. */
public enum AppType {

    SDI("Single document (SDI)",
            "One document per window, like Notepad: a text editor with New, Open, Save and Save As."),
    MDI("Multiple documents (MDI)",
            "Several documents as windows inside the main window, with a Window menu to cascade, tile and switch."),
    DIALOG("Dialog based",
            "A compact window built around a single form, like a utility or calculator. No document handling."),
    OUTLOOK("Outlook style",
            "A navigation pane with sections: Mail with folders, a message list and a reading pane, Contacts and Tasks.");

    private final String title;
    private final String description;

    AppType(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    /** Accepts {@code sdi}, {@code mdi}, {@code dialog} and {@code outlook} in any case. */
    public static AppType parse(String text) {
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown application type '" + text + "': use sdi, mdi, dialog or outlook");
        }
    }

    @Override
    public String toString() {
        return title;
    }
}
