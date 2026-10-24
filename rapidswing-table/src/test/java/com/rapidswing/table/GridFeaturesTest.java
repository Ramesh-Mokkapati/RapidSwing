package com.rapidswing.table;

import com.rapidswing.data.Filter;
import com.rapidswing.data.Operator;
import com.rapidswing.data.Page;
import com.rapidswing.data.Query;
import com.rapidswing.i18n.I18n;
import com.rapidswing.property.Property;
import com.rapidswing.util.Edt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridFeaturesTest {

    enum Level { JUNIOR, SENIOR_STAFF }

    private final Person asha = new Person(1, "Asha", "5000");
    private final Person liam = new Person(2, "Liam", "900");
    private final Person mei = new Person(3, "Mei", "12000");

    @AfterEach
    void cleanUp() {
        SearchPanel.clearAllHistory();
        I18n.global().setLocale(Locale.ENGLISH);
    }

    private DataGrid<Person> grid() {
        DataGrid<Person> grid = new DataGrid<>(Person.model());
        grid.setRows(Arrays.asList(asha, liam, mei));
        return grid;
    }

    @Test
    void rowActionsFollowSelectionRulesConditionsAndConfirmation() {
        Edt.runAndWait(() -> {
            DataGrid<Person> grid = grid();
            List<List<Person>> raised = new ArrayList<>();
            List<String> questions = new ArrayList<>();
            boolean[] answer = {false};
            grid.setConfirmHandler((question, title) -> {
                questions.add(question);
                return answer[0];
            });
            RowAction<Person> raise = grid.rowAction("Raise", raised::add)
                    .when(person -> person.getSalary().compareTo(new BigDecimal("10000")) < 0)
                    .confirm(rows -> "Raise " + rows.size() + "?");
            RowAction<Person> edit = grid.rowAction("Edit", rows -> { }).selection(RowAction.Selection.ONE);

            assertFalse(raise.isEnabled());
            grid.getTable().setRowSelectionInterval(0, 1);
            assertTrue(raise.isEnabled());
            assertFalse(edit.isEnabled());

            assertFalse(raise.run());
            assertEquals(Collections.singletonList("Raise 2?"), questions);
            answer[0] = true;
            assertTrue(raise.run());
            assertEquals(Arrays.asList(asha, liam), raised.get(0));

            grid.getTable().setRowSelectionInterval(0, 2);
            assertFalse(raise.isEnabled(), "Mei earns too much for a raise");
            grid.select(mei);
            assertTrue(edit.isEnabled());
        });
    }

    @Test
    void rowActionsCanRequireAPermission() {
        Edt.runAndWait(() -> {
            DataGrid<Person> grid = grid();
            Property<Boolean> admin = new Property<>(false);
            RowAction<Person> delete = grid.rowAction("Delete", rows -> { }).requires(admin);
            grid.select(asha);

            assertFalse(delete.isEnabled());
            assertFalse(delete.isPermitted());
            admin.set(true);
            assertTrue(delete.isEnabled());
        });
    }

    @Test
    void rowAndColumnStylesCombineButNotOnSelectedRows() {
        Edt.runAndWait(() -> {
            BeanTableModel<Person> model = BeanTableModel.builder(Person.class)
                    .column("Name", String.class, Person::getName)
                    .column("Salary", BigDecimal.class, Person::getSalary)
                    .style(salary -> salary.compareTo(new BigDecimal("1000")) < 0 ? CellStyle.ERROR.textOnly() : null)
                    .build();
            DataGrid<Person> grid = new DataGrid<>(model);
            grid.setRows(Arrays.asList(asha, liam));
            grid.rowStyle(person -> person.getId() == 2 ? CellStyle.WARNING.bold() : null);

            assertNull(grid.styleAt(0, 1));
            CellStyle liamName = grid.styleAt(1, 0);
            CellStyle liamSalary = grid.styleAt(1, 1);
            assertEquals(CellStyle.WARNING.getForeground(), liamName.getForeground());
            assertEquals(CellStyle.ERROR.getForeground(), liamSalary.getForeground());
            assertEquals(CellStyle.WARNING.getBackground(), liamSalary.getBackground());
            assertTrue(liamSalary.isBold());

            grid.select(liam);
            assertNull(grid.styleAt(1, 1));
        });
    }

    @Test
    void enumColumnsShowTranslatedOrReadableText() {
        BeanTableModel<Level> model = BeanTableModel.builder(Level.class)
                .column("Level", Level.class, level -> level).build();

        assertEquals("Senior staff", model.getColumn(0).format(Level.SENIOR_STAFF));
    }

    @Test
    void compareFindsDifferingColumns() {
        Person ashaToo = new Person(4, "Asha", "5000");

        assertEquals(Arrays.asList("ID", "Name", "Salary"), CompareDialog.differences(Person.model(), Arrays.asList(asha, liam)));
        assertEquals(Collections.singletonList("ID"), CompareDialog.differences(Person.model(), Arrays.asList(asha, ashaToo)));
        Edt.runAndWait(() -> {
            javax.swing.JTable onlyDifferences = CompareDialog.table(Person.model(), Arrays.asList(asha, liam), true);
            assertEquals(3, onlyDifferences.getColumnCount());
            assertEquals(3, onlyDifferences.getRowCount());
            assertEquals("Salary", onlyDifferences.getValueAt(2, 0));
            assertEquals("$900", onlyDifferences.getValueAt(2, 2));
        });
    }

    @Test
    void sortOrderUsesFieldNamesForServerSorting() {
        Edt.runAndWait(() -> {
            BeanTableModel<Person> model = BeanTableModel.builder(Person.class)
                    .column("Name", String.class, Person::getName).field("name")
                    .column("Salary", BigDecimal.class, Person::getSalary)
                    .build();
            DataGrid<Person> grid = new DataGrid<>(model);
            AtomicInteger changes = new AtomicInteger();
            grid.onSortChanged(changes::incrementAndGet);

            grid.getTable().getRowSorter().setSortKeys(Arrays.asList(
                    new RowSorter.SortKey(0, SortOrder.DESCENDING), new RowSorter.SortKey(1, SortOrder.ASCENDING)));

            assertEquals(Collections.singletonList(new Query.Sort("name", false)), grid.sortOrder());
            assertEquals(1, changes.get());
        });
    }

    @Test
    void printHeaderIsTakenLiterally() {
        assertEquals("Bob's {report}", DataGrid.headerFormat("Bob's {report}").format(new Object[0]));
    }

    @Test
    void searchPanelBuildsFiltersFromOperatorsAndValues() {
        Edt.runAndWait(() -> {
            List<List<Filter>> searches = new ArrayList<>();
            SearchPanel panel = new SearchPanel("people")
                    .field("name", "Name", String.class)
                    .field("salary", "Salary", BigDecimal.class)
                    .field("level", "Level", Level.class)
                    .field("since", "Since", LocalDate.class)
                    .onSearch(searches::add);

            ((JTextField) panel.valueOf("name")).setText("li");
            panel.operatorOf("salary").setSelectedItem(Operator.BETWEEN);
            ((JTextField) panel.valueOf("salary")).setText("1,000.5");
            ((JTextField) panel.secondValueOf("salary")).setText("6000");
            ((JComboBox<?>) panel.valueOf("level")).setSelectedItem(Level.JUNIOR);
            panel.operatorOf("since").setSelectedItem(Operator.LESS);
            ((JTextField) panel.valueOf("since")).setText("2026-01-31");

            assertTrue(panel.search());
            assertEquals(Arrays.asList(
                    Filter.of("name", Operator.CONTAINS, "li"),
                    Filter.between("salary", new BigDecimal("1000.5"), new BigDecimal("6000")),
                    Filter.of("level", Operator.EQUALS, Level.JUNIOR),
                    Filter.of("since", Operator.LESS, LocalDate.of(2026, 1, 31))), searches.get(0));
            assertEquals("Name contains li, Salary between 1000.5 and 6000, Level is Junior, Since less than "
                    + "Jan 31, 2026", panel.describe(searches.get(0)));
        });
    }

    @Test
    void searchPanelRejectsInvalidValuesAndRemembersSearches() {
        Edt.runAndWait(() -> {
            List<List<Filter>> searches = new ArrayList<>();
            SearchPanel panel = new SearchPanel("people")
                    .field("name", "Name", String.class)
                    .field("salary", "Salary", BigDecimal.class)
                    .onSearch(searches::add);
            ((JTextField) panel.valueOf("salary")).setText("lots");
            assertFalse(panel.search());
            assertNotNull(panel.valueOf("salary").getToolTipText());
            assertTrue(searches.isEmpty());

            ((JTextField) panel.valueOf("salary")).setText("");
            ((JTextField) panel.valueOf("name")).setText("Mei");
            assertTrue(panel.search());
            panel.clear();
            assertTrue(panel.search());
            assertEquals(Collections.emptyList(), searches.get(1));

            SearchPanel again = new SearchPanel("people").field("name", "Name", String.class)
                    .field("salary", "Salary", BigDecimal.class);
            assertEquals(Collections.singletonList(Collections.singletonList(Filter.of("name", Operator.CONTAINS, "Mei"))),
                    again.history());
            again.setFilters(again.history().get(0));
            assertEquals("Mei", ((JTextField) again.valueOf("name")).getText());
        });
    }

    @Test
    void germanNumbersAreParsedWithTheirSeparators() {
        Edt.runAndWait(() -> {
            I18n.global().setLocale(Locale.GERMAN);
            SearchPanel panel = new SearchPanel("de").field("salary", "Gehalt", BigDecimal.class);
            ((JTextField) panel.valueOf("salary")).setText("1.000,5");
            assertEquals(Collections.singletonList(Filter.of("salary", Operator.EQUALS, new BigDecimal("1000.5"))),
                    panel.getFilters());
        });
    }

    @Test
    void pagingBarNavigatesWithinThePages() {
        Edt.runAndWait(() -> {
            AtomicInteger loads = new AtomicInteger();
            PagingBar paging = new PagingBar(25).onChange(loads::incrementAndGet);
            paging.show(new Page<>(Collections.emptyList(), 0, 25, 60));

            assertEquals("Page 1 of 3", paging.getPositionText());
            assertFalse(paging.goTo(-1));
            assertTrue(paging.goTo(2));
            assertEquals("Page 3 of 3", paging.getPositionText());
            assertEquals(new Query.Sort("x", true), new Query.Sort("x", true));
            assertEquals(50, paging.apply(Query.all()).getOffset());
            assertFalse(paging.goTo(3));
            assertEquals(1, loads.get());
        });
    }
}
