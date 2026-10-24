package com.rapidswing.crud;

import com.rapidswing.data.Operator;
import com.rapidswing.data.Page;
import com.rapidswing.data.Query;
import com.rapidswing.event.EventBus;
import com.rapidswing.table.DataGrid;
import com.rapidswing.testing.TestTasks;
import com.rapidswing.testing.Ui;
import org.junit.jupiter.api.Test;

import javax.swing.JTextField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrudQueryTest {

    public static final class Customer {
        private int id;
        private String name;
        private String city;

        public Customer(int id, String name, String city) {
            this.id = id;
            this.name = name;
            this.city = city;
        }

        public Customer(Customer other) {
            this(other.id, other.name, other.city);
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCity() {
            return city;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Customer && ((Customer) other).id == id;
        }

        @Override
        public int hashCode() {
            return id;
        }
    }

    public static final class Order {
        private final String number;

        public Order(String number) {
            this.number = number;
        }

        public String getNumber() {
            return number;
        }
    }

    static final class CustomersChanged {
    }

    /** Records the queries it answers. */
    static final class RecordingRepository extends InMemoryRepository<Customer> {
        final List<Query> queries = new ArrayList<>();

        RecordingRepository() {
            super(Customer::getId, Customer::new);
        }

        @Override
        public synchronized Page<Customer> find(Query query) {
            queries.add(query);
            return super.find(query);
        }
    }

    private static List<Customer> customers(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> new Customer(i, String.format("Customer %02d", i), i % 2 == 0 ? "Berlin" : "Bern"))
                .collect(Collectors.toList());
    }

    private static CrudScreen.Builder<Customer> builder(CrudRepository<Customer> repository) {
        return CrudScreen.builder(Customer.class)
                .names("Customer", "Customers")
                .repository(repository)
                .newItem(() -> new Customer(0, "", ""))
                .copy(Customer::new)
                .itemName(Customer::getName)
                .columns(table -> table
                        .column("Name", String.class, Customer::getName).field("name")
                        .column("City", String.class, Customer::getCity).field("city"))
                .form((form, edit) -> form.text("Name", edit.property(Customer::getName, Customer::setName)))
                .tasks(TestTasks.immediate())
                .confirmWith(message -> true)
                .errorsWith((message, error) -> {
                    throw new AssertionError(message, error);
                });
    }

    private static List<String> names(DataGrid<Customer> grid) {
        return grid.visibleRows().stream().map(Customer::getName).collect(Collectors.toList());
    }

    @Test
    void searchAndPagingGoToTheQueryRepository() {
        RecordingRepository repository = new RecordingRepository();
        repository.addAll(customers(30));
        CrudScreen<Customer> screen = Ui.onEdt(() -> {
            CrudScreen<Customer> built = builder(repository)
                    .search(panel -> panel.field("name", "Name", String.class).field("city", "City", String.class))
                    .pageSize(25)
                    .build();
            built.onShow();
            return built;
        });

        assertEquals(25, screen.getGrid().getModel().getRowCount());
        assertEquals("Page 1 of 2", screen.getPagingBar().getPositionText());
        Ui.onEdt(() -> assertTrue(screen.getPagingBar().goTo(1)));
        assertEquals(Arrays.asList("Customer 26", "Customer 27", "Customer 28", "Customer 29", "Customer 30"),
                names(screen.getGrid()));

        Ui.onEdt(() -> {
            ((JTextField) screen.getSearchPanel().valueOf("city")).setText("berlin");
            screen.getSearchPanel().operatorOf("city").setSelectedItem(Operator.EQUALS);
            assertTrue(screen.getSearchPanel().search());
        });

        Query last = repository.queries.get(repository.queries.size() - 1);
        assertEquals(0, last.getOffset(), "a new search starts on the first page");
        assertEquals(15, screen.getGrid().getModel().getRowCount());
        assertEquals("Page 1 of 1", screen.getPagingBar().getPositionText());
    }

    @Test
    void plainRepositoriesAreSearchedInMemory() {
        CrudRepository<Customer> plain = new CrudRepository<Customer>() {
            @Override
            public List<Customer> findAll() {
                return customers(6);
            }

            @Override
            public Customer save(Customer item) {
                return item;
            }

            @Override
            public void delete(Customer item) {
            }
        };
        CrudScreen<Customer> screen = Ui.onEdt(() -> {
            CrudScreen<Customer> built = builder(plain).search(panel -> panel.field("name", "Name", String.class)).build();
            built.onShow();
            return built;
        });
        Ui.onEdt(() -> {
            ((JTextField) screen.getSearchPanel().valueOf("name")).setText("05");
            screen.getSearchPanel().search();
        });

        assertEquals(Collections.singletonList("Customer 05"), names(screen.getGrid()));
    }

    @Test
    void detailsFollowTheEditedItem() {
        RecordingRepository repository = new RecordingRepository();
        repository.addAll(customers(3));
        CrudScreen<Customer> screen = Ui.onEdt(() -> {
            CrudScreen<Customer> built = builder(repository)
                    .details("Orders", Order.class, table -> table.column("Number", String.class, Order::getNumber),
                            customer -> Arrays.asList(new Order("A-" + customer.getId()), new Order("B-" + customer.getId())))
                    .build();
            built.onShow();
            return built;
        });
        DataGrid<Order> orders = screen.getDetailGrid(0);

        Ui.onEdt(() -> screen.getGrid().getTable().setRowSelectionInterval(1, 1));
        assertEquals(Arrays.asList("A-2", "B-2"), orders.visibleRows().stream()
                .map(Order::getNumber).collect(Collectors.toList()));

        Ui.onEdt(screen::newItem);
        assertTrue(orders.visibleRows().isEmpty());
    }

    @Test
    void eventsRefreshTheScreenWhileShownAndWithoutUnsavedChanges() {
        RecordingRepository repository = new RecordingRepository();
        repository.addAll(customers(2));
        EventBus events = new EventBus();
        CrudScreen<Customer> screen = Ui.onEdt(() -> {
            CrudScreen<Customer> built = builder(repository).refreshOn(events, CustomersChanged.class).build();
            built.onShow();
            return built;
        });
        int before = repository.queries.size();

        repository.save(new Customer(3, "Customer 03", "Bern"));
        Ui.onEdt(() -> events.publish(new CustomersChanged()));
        assertEquals(before + 1, repository.queries.size());
        assertEquals(3, screen.getGrid().getModel().getRowCount());

        Ui.onEdt(() -> screen.getGrid().getTable().setRowSelectionInterval(0, 0));
        Ui.onEdt(() -> ((JTextField) screen.getForm().component("Name")).setText("Changed"));
        Ui.onEdt(() -> events.publish(new CustomersChanged()));
        assertEquals(before + 1, repository.queries.size(), "unsaved changes are not overwritten");

        Ui.onEdt(() -> {
            screen.cancel();
            screen.onHide();
            events.publish(new CustomersChanged());
        });
        assertEquals(before + 1, repository.queries.size(), "hidden screens do not refresh");
    }
}
