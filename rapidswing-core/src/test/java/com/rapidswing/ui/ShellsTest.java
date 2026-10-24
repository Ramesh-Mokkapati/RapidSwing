package com.rapidswing.ui;

import com.rapidswing.util.Edt;
import org.junit.jupiter.api.Test;

import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The MDI workspace and the Outlook-style pane. */
class ShellsTest {

    private static final class Document extends AbstractView {
        boolean dirty;

        Document(String title) {
            super(title);
        }

        @Override
        public boolean canLeave() {
            return !dirty;
        }
    }

    @Test
    void mdiOpensActivatesAndClosesDocuments() {
        Edt.runAndWait(() -> {
            MdiWorkspace workspace = new MdiWorkspace();
            workspace.setSize(1000, 700);
            workspace.doLayout();

            JInternalFrame first = workspace.open(new Document("Report.txt"));
            JInternalFrame second = workspace.open("Notes", new JPanel());

            assertEquals(2, workspace.documents().size());
            assertSame(second, workspace.activeDocument().get());
            assertEquals("Report.txt", first.getTitle());

            workspace.tile();
            assertEquals(0, first.getX());
            assertEquals(500, second.getX());
            workspace.cascade();
            assertEquals(28, second.getY());

            assertTrue(workspace.close(second));
            assertSame(first, workspace.activeDocument().get());
        });
    }

    @Test
    void documentsWithUnsavedChangesRefuseToClose() {
        Edt.runAndWait(() -> {
            MdiWorkspace workspace = new MdiWorkspace();
            Document dirty = new Document("Draft");
            dirty.dirty = true;
            workspace.open(new Document("Clean"));
            workspace.open(dirty);

            assertFalse(workspace.closeAll());
            assertEquals(Arrays.asList("Draft"), titles(workspace));

            dirty.dirty = false;
            assertTrue(workspace.closeAll());
            assertTrue(workspace.documents().isEmpty());
        });
    }

    @Test
    void windowMenuListsOpenDocuments() {
        Edt.runAndWait(() -> {
            MdiWorkspace workspace = new MdiWorkspace();
            JMenu menu = workspace.windowMenu();
            assertEquals(Arrays.asList("Cascade", "Tile", "Close all", null, "No open windows"), items(menu));

            workspace.open("Report", new JPanel());
            workspace.open("Notes", new JPanel());
            workspace.fillWindowMenu(menu);

            assertEquals(Arrays.asList("Cascade", "Tile", "Close all", null, "1 Report", "2 Notes"), items(menu));
            assertTrue(menu.getItem(5).isSelected());
        });
    }

    @Test
    void outlookPaneSwitchesSections() {
        Edt.runAndWait(() -> {
            OutlookPane outlook = new OutlookPane()
                    .addSection("mail", "Mail", null, new JLabel("Inbox"), new JLabel("Messages"))
                    .addSection("contacts", "Contacts", null, null, new JLabel("People"));

            assertEquals("mail", outlook.currentSection().get());
            outlook.showSection("contacts");
            assertEquals("contacts", outlook.currentSection().get());
            assertThrows(IllegalArgumentException.class, () -> outlook.showSection("calendar"));
            assertThrows(IllegalArgumentException.class,
                    () -> outlook.addSection("mail", "Mail", null, null, new JLabel()));
        });
    }

    private static List<String> titles(MdiWorkspace workspace) {
        List<String> titles = new ArrayList<>();
        for (JInternalFrame frame : workspace.documents()) {
            titles.add(frame.getTitle());
        }
        return titles;
    }

    private static List<String> items(JMenu menu) {
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem item = menu.getItem(i);
            texts.add(item == null ? null : item.getText());
        }
        return texts;
    }
}
