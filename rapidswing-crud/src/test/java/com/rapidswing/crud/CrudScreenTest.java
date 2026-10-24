package com.rapidswing.crud;

import com.rapidswing.forms.Validators;
import com.rapidswing.testing.TestTasks;
import com.rapidswing.testing.Ui;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrudScreenTest {

    static final class Item {
        int id;
        String name;

        Item(int id, String name) {
            this.id = id;
            this.name = name;
        }

        Item(Item other) {
            this(other.id, other.name);
        }

        String getName() {
            return name;
        }

        void setName(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Item && ((Item) other).id == id;
        }

        @Override
        public int hashCode() {
            return id;
        }
    }

    static final class FakeRepository implements CrudRepository<Item> {
        final List<Item> stored = new ArrayList<>(Arrays.asList(new Item(1, "Asha"), new Item(2, "Liam")));
        int nextId = 3;
        int saves;
        boolean failSave;

        @Override
        public List<Item> findAll() {
            List<Item> copies = new ArrayList<>();
            stored.forEach(item -> copies.add(new Item(item)));
            return copies;
        }

        @Override
        public Item save(Item item) {
            if (failSave) {
                throw new IllegalStateException("Database down");
            }
            saves++;
            Item copy = new Item(item);
            if (copy.id == 0) {
                copy.id = nextId++;
                stored.add(copy);
            } else {
                stored.set(stored.indexOf(copy), copy);
            }
            return new Item(copy);
        }

        @Override
        public void delete(Item item) {
            stored.remove(item);
        }
    }

    private FakeRepository repository;
    private Deque<Boolean> answers;
    private List<String> confirmations;
    private List<String> statuses;
    private List<String> errors;
    private CrudScreen<Item> screen;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        answers = new ArrayDeque<>();
        confirmations = new ArrayList<>();
        statuses = new ArrayList<>();
        errors = new ArrayList<>();
        onEdt(() -> {
            screen = CrudScreen.builder(Item.class)
                    .names("Item", "Items")
                    .repository(repository)
                    .newItem(() -> new Item(0, ""))
                    .copy(Item::new)
                    .itemName(Item::getName)
                    .columns(table -> table.column("Name", String.class, Item::getName))
                    .form((form, edit) -> form
                            .text("Name", edit.property(Item::getName, Item::setName))
                                .required().validate(Validators.maxLength(10)))
                    .tasks(TestTasks.immediate())
                    .confirmWith(message -> {
                        confirmations.add(message);
                        return answers.isEmpty() || answers.pop();
                    })
                    .statusWith(statuses::add)
                    .errorsWith((message, error) -> errors.add(message + ": " + error.getMessage()))
                    .build();
            screen.onShow();
        });
    }

    private static void onEdt(Runnable code) {
        Ui.onEdt(code);
    }

    private JTextField nameField() {
        return (JTextField) screen.getForm().component("Name");
    }

    private Item row(int index) {
        return screen.getGrid().getModel().getRow(index);
    }

    @Test
    void loadsOnShowAndEditsACopyOfTheSelection() {
        onEdt(() -> {
            assertEquals(2, screen.getGrid().getModel().getRowCount());
            assertEquals("2 entries loaded", statuses.get(0));
            assertNull(screen.getForm());

            screen.getGrid().select(row(0));
            assertEquals(row(0), screen.getEditedItem());
            assertNotSame(row(0), screen.getEditedItem());
            assertEquals("Asha", nameField().getText());
            assertFalse(screen.dirty().get());
        });
    }

    @Test
    void savingUpdatesListAndRepositoryOnlyAfterSave() {
        onEdt(() -> {
            screen.getGrid().select(row(0));
            nameField().setText("Asha R");
            assertTrue(screen.dirty().get());
            assertEquals("Asha", row(0).getName());
        });
        onEdt(() -> screen.save());
        onEdt(() -> {
            assertEquals("Asha R", row(0).getName());
            assertEquals("Asha R", repository.stored.get(0).getName());
            assertFalse(screen.dirty().get());
            assertEquals("Asha R saved", statuses.get(statuses.size() - 1));
        });
    }

    @Test
    void cancelRestoresSavedValues() {
        onEdt(() -> {
            screen.getGrid().select(row(1));
            nameField().setText("Changed");
            screen.cancel();

            assertEquals("Liam", nameField().getText());
            assertFalse(screen.dirty().get());
            assertEquals("Liam", row(1).getName());
        });
    }

    @Test
    void newItemIsAddedWithItsGeneratedId() {
        onEdt(() -> {
            screen.newItem();
            assertTrue(screen.isEditingNew());
            nameField().setText("Mei");
        });
        onEdt(() -> screen.save());
        onEdt(() -> {
            assertEquals(3, screen.getGrid().getModel().getRowCount());
            assertEquals(3, row(2).id);
            assertFalse(screen.isEditingNew());
            assertEquals(row(2), screen.getGrid().selectedItem().get());
        });
    }

    @Test
    void invalidFormIsNotSaved() {
        onEdt(() -> {
            screen.newItem();
            screen.save();
        });
        onEdt(() -> {
            assertEquals(0, repository.saves);
            assertEquals("Please fix the highlighted fields", statuses.get(statuses.size() - 1));
            assertEquals(2, screen.getGrid().getModel().getRowCount());
        });
    }

    @Test
    void deleteAsksFirst() {
        onEdt(() -> {
            screen.getGrid().select(row(0));
            answers.push(false);
            screen.deleteCurrent();
        });
        onEdt(() -> {
            assertEquals(Arrays.asList("Delete Asha?"), confirmations);
            assertEquals(2, repository.stored.size());
            screen.deleteCurrent();
        });
        onEdt(() -> {
            assertEquals(1, repository.stored.size());
            assertEquals(1, screen.getGrid().getModel().getRowCount());
            assertNull(screen.getEditedItem());
        });
    }

    @Test
    void unsavedChangesBlockSelectionChangeUnlessDiscarded() {
        onEdt(() -> {
            Item asha = row(0);
            Item liam = row(1);
            screen.getGrid().select(asha);
            nameField().setText("Asha R");

            answers.push(false);
            screen.getGrid().select(liam);
            assertEquals(asha, screen.getGrid().selectedItem().get());
            assertEquals(asha, screen.getEditedItem());
            assertEquals("Asha R", nameField().getText());

            answers.push(true);
            screen.getGrid().select(liam);
            assertEquals(liam, screen.getEditedItem());
            assertFalse(screen.dirty().get());
            assertEquals(Arrays.asList("Discard unsaved changes to Asha?", "Discard unsaved changes to Asha?"),
                    confirmations);
        });
    }

    @Test
    void failedSaveKeepsTheEdits() {
        repository.failSave = true;
        onEdt(() -> {
            screen.getGrid().select(row(0));
            nameField().setText("Asha R");
            screen.save();
        });
        onEdt(() -> {
            assertEquals(Arrays.asList("Could not save Asha: Database down"), errors);
            assertTrue(screen.dirty().get());
            assertEquals("Asha R", nameField().getText());
            assertEquals("Asha", row(0).getName());
            assertFalse(screen.busy().get());
        });
    }

    @Test
    void canLeaveAsksOnlyWhenDirty() {
        onEdt(() -> {
            assertTrue(screen.canLeave());
            screen.getGrid().select(row(0));
            nameField().setText("x");

            answers.push(false);
            assertFalse(screen.canLeave());
            assertEquals(1, confirmations.size());
        });
    }

    @Test
    void withoutPermissionsItemsAreReadOnly() {
        List<CrudScreen<Item>> holder = new ArrayList<>();
        onEdt(() -> {
            CrudScreen<Item> readOnly = CrudScreen.builder(Item.class)
                    .repository(repository)
                    .newItem(() -> new Item(0, ""))
                    .copy(Item::new)
                    .itemName(Item::getName)
                    .columns(table -> table.column("Name", String.class, Item::getName))
                    .form((form, edit) -> form.text("Name", edit.property(Item::getName, Item::setName)))
                    .tasks(TestTasks.immediate())
                    .confirmWith(message -> true)
                    .allowCreate(false)
                    .allowEdit(false)
                    .allowDelete(false)
                    .build();
            readOnly.onShow();
            holder.add(readOnly);
        });
        CrudScreen<Item> readOnly = holder.get(0);
        onEdt(() -> {
            readOnly.getGrid().select(readOnly.getGrid().getModel().getRow(0));
            JTextField name = (JTextField) readOnly.getForm().component("Name");
            assertFalse(name.isEditable());
            assertEquals("Asha", name.getText());

            readOnly.newItem();
            readOnly.deleteCurrent();
            readOnly.save();
        });
        onEdt(() -> {
            assertFalse(readOnly.isEditingNew());
            assertEquals(2, repository.stored.size());
            assertEquals(0, repository.saves);
            assertEquals(Arrays.asList("Refresh"), toolbarButtonTexts(readOnly));
        });
    }

    private static List<String> toolbarButtonTexts(CrudScreen<?> screen) {
        List<String> texts = new ArrayList<>();
        collectToolbarButtons(screen.getGrid(), texts);
        return texts;
    }

    private static void collectToolbarButtons(Container container, List<String> texts) {
        for (Component child : container.getComponents()) {
            if (child instanceof JButton && child.getParent() instanceof JToolBar) {
                texts.add(((JButton) child).getText());
            }
            if (child instanceof Container) {
                collectToolbarButtons((Container) child, texts);
            }
        }
    }

    @Test
    void builderRequiresTheEssentials() {
        onEdt(() -> {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> CrudScreen.builder(Item.class).repository(repository).build());
            assertEquals("CrudScreen.newItem(...) is required", e.getMessage());
        });
    }
}
