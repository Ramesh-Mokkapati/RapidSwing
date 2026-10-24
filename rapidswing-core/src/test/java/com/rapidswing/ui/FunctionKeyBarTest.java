package com.rapidswing.ui;

import com.rapidswing.util.Edt;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionKeyBarTest {

    @Test
    void keysRunTheirActionsUnlessDisabled() {
        Edt.runAndWait(() -> {
            AtomicInteger saves = new AtomicInteger();
            AppAction save = AppAction.of("Save", saves::incrementAndGet);
            FunctionKeyBar bar = new FunctionKeyBar().key("F2", save).key("F12", "Back", () -> { });

            assertTrue(bar.button("F2").getText().contains("<b>F2</b><br>Save"));
            assertEquals("rapidswing.fkey.F2",
                    bar.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke("F2")));
            assertTrue(bar.press("F2"));
            assertEquals(1, saves.get());

            save.setEnabled(false);
            assertFalse(bar.press("F2"));
            assertFalse(bar.button("F2").isEnabled());

            save.putValue(AppAction.NAME, "Speichern");
            assertTrue(bar.button("F2").getText().contains("Speichern"));
        });
    }
}
