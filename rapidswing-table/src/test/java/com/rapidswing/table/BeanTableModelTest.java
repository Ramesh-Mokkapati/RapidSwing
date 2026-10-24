package com.rapidswing.table;

import org.junit.jupiter.api.Test;

import javax.swing.event.TableModelEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeanTableModelTest {

    private final Person asha = new Person(1, "Asha", "5000");
    private final Person liam = new Person(2, "Liam", "4200.50");

    @Test
    void exposesColumnsAndValues() {
        BeanTableModel<Person> model = Person.model();
        model.setRows(Arrays.asList(asha, liam));

        assertEquals(3, model.getColumnCount());
        assertEquals(2, model.getRowCount());
        assertEquals("Name", model.getColumnName(1));
        assertEquals(Integer.class, model.getColumnClass(0));
        assertEquals(BigDecimal.class, model.getColumnClass(2));
        assertEquals("Liam", model.getValueAt(1, 1));
        assertEquals("$4200.50", model.getColumn(2).format(liam));
        assertEquals("", model.getColumn(2).formatValue(null));
    }

    @Test
    void editableColumnsWriteToBean() {
        BeanTableModel<Person> model = Person.model();
        model.setRows(Arrays.asList(asha));

        assertFalse(model.isCellEditable(0, 0));
        assertTrue(model.isCellEditable(0, 1));
        model.setValueAt("Asha Rao", 0, 1);
        assertEquals("Asha Rao", asha.getName());
        assertThrows(IllegalStateException.class, () -> model.setValueAt(5, 0, 0));
    }

    @Test
    void rowChangesFireMatchingEvents() {
        BeanTableModel<Person> model = Person.model();
        List<String> events = new ArrayList<>();
        model.addTableModelListener(e -> events.add(type(e) + " " + e.getFirstRow() + "-" + e.getLastRow()));

        model.add(asha);
        model.add(liam);
        model.update(liam);
        model.remove(asha);
        assertFalse(model.remove(asha));
        assertFalse(model.update(asha));

        assertEquals(Arrays.asList("insert 0-0", "insert 1-1", "update 1-1", "delete 0-0"), events);
        assertEquals(Arrays.asList(liam), model.getRows());
    }

    @Test
    void builderRejectsMistakes() {
        TableBuilder<Person> builder = BeanTableModel.builder(Person.class)
                .column("ID", Integer.class, Person::getId);

        assertThrows(IllegalArgumentException.class, () -> builder.column("ID", Integer.class, Person::getId));
        assertThrows(IllegalArgumentException.class, () -> builder.column("Raw", int.class, Person::getId));
        assertThrows(IllegalStateException.class, () -> BeanTableModel.builder(Person.class).build());

        builder.build();
        assertThrows(IllegalStateException.class, builder::build);
    }

    private static String type(TableModelEvent e) {
        switch (e.getType()) {
            case TableModelEvent.INSERT:
                return "insert";
            case TableModelEvent.DELETE:
                return "delete";
            default:
                return "update";
        }
    }
}
