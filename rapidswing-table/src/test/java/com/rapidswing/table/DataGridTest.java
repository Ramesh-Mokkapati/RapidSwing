package com.rapidswing.table;

import com.formdev.flatlaf.FlatClientProperties;
import com.rapidswing.i18n.I18n;
import com.rapidswing.util.Edt;
import org.junit.jupiter.api.Test;

import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataGridTest {

    private final Person asha = new Person(1, "Asha", "5000");
    private final Person liam = new Person(2, "Liam", "900");
    private final Person mei = new Person(3, "Mei", "12000");

    private DataGrid<Person> grid() {
        DataGrid<Person> grid = new DataGrid<>(Person.model());
        grid.setRows(Arrays.asList(asha, liam, mei));
        return grid;
    }

    @Test
    void searchMatchesDisplayedTextOfVisibleColumns() {
        Edt.runAndWait(() -> {
            DataGrid<Person> grid = grid();

            grid.setSearchText("li");
            assertEquals(Arrays.asList(liam), grid.visibleRows());

            grid.setSearchText("$12000");
            assertEquals(Arrays.asList(mei), grid.visibleRows());

            grid.setColumnVisible("Salary", false);
            assertTrue(grid.visibleRows().isEmpty());

            grid.setSearchText("");
            assertEquals(3, grid.visibleRows().size());
        });
    }

    @Test
    void numericColumnsSortNumerically() {
        Edt.runAndWait(() -> {
            DataGrid<Person> grid = grid();
            JTable table = grid.getTable();

            table.getRowSorter().setSortKeys(Collections.singletonList(new RowSorter.SortKey(2, SortOrder.ASCENDING)));

            // As text, "12000" would sort before "5000" and "900".
            assertEquals(Arrays.asList(liam, asha, mei), grid.visibleRows());
        });
    }

    @Test
    void selectionPropertiesFollowTableAndSurviveRefresh() {
        Edt.runAndWait(() -> {
            DataGrid<Person> grid = grid();
            List<Person> changes = new ArrayList<>();
            grid.selectedItem().onChange(changes::add);

            assertTrue(grid.select(liam));
            assertEquals(liam, grid.selectedItem().get());
            assertEquals(Arrays.asList(liam), grid.selectedItems().get());

            grid.setRows(Arrays.asList(new Person(2, "Liam (reloaded)", "900"), mei));
            assertEquals(liam, grid.selectedItem().get());
            assertEquals(Collections.singletonList(liam), changes);

            grid.setRows(Collections.singletonList(mei));
            assertNull(grid.selectedItem().get());
            assertFalse(grid.select(asha));
        });
    }

    @Test
    void enterOpensSelectedRow() {
        Edt.runAndWait(() -> {
            List<Person> opened = new ArrayList<>();
            DataGrid<Person> grid = grid().onOpen(opened::add);
            grid.select(mei);

            grid.getTable().getActionMap().get("rapidswing.open").actionPerformed(null);

            assertEquals(Arrays.asList(mei), opened);
        });
    }

    @Test
    void columnsCanBeHiddenAndShownInOriginalPosition() {
        Edt.runAndWait(() -> {
            DataGrid<Person> grid = grid();
            JTable table = grid.getTable();

            grid.setColumnVisible("Name", false);
            assertFalse(grid.isColumnVisible("Name"));
            assertEquals(2, table.getColumnCount());

            grid.setColumnVisible("Name", true);
            assertEquals("Name", table.getColumnName(1));

            grid.setColumnVisible("ID", false);
            grid.setColumnVisible("Salary", false);
            assertThrows(IllegalStateException.class, () -> grid.setColumnVisible("Name", false));
            assertThrows(IllegalArgumentException.class, () -> grid.setColumnVisible("Nope", true));
        });
    }

    @Test
    void hiddenColumnsStartHidden() {
        Edt.runAndWait(() -> {
            BeanTableModel<Person> model = BeanTableModel.builder(Person.class)
                    .column("ID", Integer.class, Person::getId)
                    .column("Name", String.class, Person::getName).hidden()
                    .build();
            DataGrid<Person> grid = new DataGrid<>(model);

            assertFalse(grid.isColumnVisible("Name"));
            assertEquals(1, grid.getTable().getColumnCount());
        });
    }

    @Test
    void csvExportUsesVisibleRowsColumnsAndQuoting() {
        Edt.runAndWait(() -> {
            DataGrid<Person> grid = grid();
            grid.setRows(Arrays.asList(asha, new Person(4, "Rao, \"AJ\"", "1")));
            grid.setColumnVisible("ID", false);

            assertEquals("Name,Salary\r\nAsha,$5000\r\n\"Rao, \"\"AJ\"\"\",$1\r\n", csv(grid));

            grid.setSearchText("asha");
            assertEquals("Name,Salary\r\nAsha,$5000\r\n", csv(grid));
        });
    }

    @Test
    void textsComeFromTheGlobalLocale() {
        I18n i18n = I18n.global();
        Locale saved = i18n.getLocale();
        try {
            Edt.runAndWait(() -> {
                i18n.setLocale(Locale.GERMAN);
                DataGrid<Person> grid = grid();
                assertEquals("Suchen", grid.getSearchField().getClientProperty(FlatClientProperties.PLACEHOLDER_TEXT));
            });
        } finally {
            Edt.runAndWait(() -> i18n.setLocale(saved));
        }
    }

    private static String csv(DataGrid<?> grid) {
        StringWriter out = new StringWriter();
        try {
            grid.exportCsv(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString();
    }
}
