package com.rapidswing.data;

import com.rapidswing.task.ProgressModel;
import com.rapidswing.util.Edt;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryTest {

    public static class City {
        private final String name;
        private final int population;
        private final LocalDate founded;

        public City(String name, int population, LocalDate founded) {
            this.name = name;
            this.population = population;
            this.founded = founded;
        }

        public String getName() {
            return name;
        }

        public int getPopulation() {
            return population;
        }

        public LocalDate getFounded() {
            return founded;
        }
    }

    private final List<City> cities = Arrays.asList(
            new City("Berlin", 3_700_000, LocalDate.of(1237, 1, 1)),
            new City("Bern", 134_000, LocalDate.of(1191, 1, 1)),
            new City("Munich", 1_500_000, LocalDate.of(1158, 6, 14)),
            new City("Hamburg", 1_800_000, null));

    private static List<String> names(Page<City> page) {
        return page.getItems().stream().map(City::getName).collect(Collectors.toList());
    }

    @Test
    void operatorsCompareTextIgnoringCaseAndNumbersAcrossTypes() {
        assertTrue(Operator.CONTAINS.matches("Berlin", "ERL", null));
        assertTrue(Operator.LIKE.matches("Berlin", "b*n", null));
        assertFalse(Operator.LIKE.matches("Berlin", "b?n", null));
        assertTrue(Operator.EQUALS.matches(5, new BigDecimal("5.00"), null));
        assertTrue(Operator.BETWEEN.matches(5L, 1, 5.0));
        assertFalse(Operator.BETWEEN.matches(6L, 1, 5.0));
        assertTrue(Operator.IS_EMPTY.matches("  ", null, null));
        assertFalse(Operator.GREATER.matches(null, 1, null));
        assertEquals(Operator.forType(String.class)[0], Operator.CONTAINS);
        assertTrue(Arrays.asList(Operator.forType(int.class)).contains(Operator.BETWEEN));
    }

    @Test
    void queriesFilterSortAndPageInMemory() {
        Query query = Query.all()
                .where(Filter.of("name", Operator.STARTS_WITH, "b"), Filter.of("population", Operator.GREATER, 100_000))
                .sortBy("population", true);

        assertEquals(Arrays.asList("Bern", "Berlin"), names(query.apply(cities, BeanProperties::get)));

        Query paged = Query.all().sortBy("name", true).page(1, 3);
        Page<City> page = paged.apply(cities, BeanProperties::get);
        assertEquals(Arrays.asList("Munich"), names(page));
        assertEquals(4, page.getTotal());
        assertEquals(2, page.getPageCount());
        assertEquals(1, page.getIndex());

        Page<City> byFounding = Query.all().sortBy("founded", false).apply(cities, BeanProperties::get);
        assertEquals(Arrays.asList("Berlin", "Bern", "Munich", "Hamburg"), names(byFounding));
        assertEquals(Arrays.asList("Hamburg"), names(Query.all().where(Filter.of("founded", Operator.IS_EMPTY))
                .apply(cities, BeanProperties::get)));
    }

    @Test
    void beanPropertiesFollowFieldOrder() {
        assertEquals(Arrays.asList("name", "population", "founded"), BeanProperties.of(City.class).stream()
                .map(java.beans.PropertyDescriptor::getName).collect(Collectors.toList()));
        assertEquals(134_000, BeanProperties.get(cities.get(1), "population"));
        assertThrows(IllegalArgumentException.class, () -> BeanProperties.get(cities.get(1), "mayor"));
    }

    @Test
    void progressIsPublishedOnTheEdtAndCanBeCancelled() throws Exception {
        ProgressModel progress = new ProgressModel();
        Thread worker = new Thread(() -> {
            progress.begin("Importing", 4);
            progress.step("one");
            progress.step("two");
        });
        worker.start();
        worker.join();
        Edt.runAndWait(() -> { });

        Edt.runAndWait(() -> {
            assertEquals(4, (int) progress.total().get());
            assertEquals(2, (int) progress.done().get());
            assertEquals("two", progress.message().get());
        });
        progress.cancel();
        assertThrows(CancellationException.class, progress::checkCancelled);
    }
}
