package com.rapidswing.demo.service;

import com.rapidswing.crud.QueryRepository;
import com.rapidswing.data.BeanProperties;
import com.rapidswing.data.Page;
import com.rapidswing.data.Query;
import com.rapidswing.demo.model.Customer;
import com.rapidswing.demo.model.Order;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sample data with artificial delays, to show background tasks and the loading message.
 * Stores and returns copies, like a database would, so callers never share instances with it. As a
 * {@link QueryRepository} it searches, sorts and pages itself, as a database server would.
 */
public class InMemoryCustomerService implements CustomerService, QueryRepository<Customer> {

    private final List<Customer> customers = new ArrayList<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    public InMemoryCustomerService() {
        add("Asha Rao", "asha.rao@example.com", "Hyderabad", "12500.00", "2019-04-12", true);
        add("Liam Carter", "liam.carter@example.com", "London", "980.50", "2021-11-03", true);
        add("Mei Tanaka", "mei.tanaka@example.com", "Osaka", "0.00", "2018-01-22", false);
        add("Diego Alvarez", "diego.alvarez@example.com", "Madrid", "45210.75", "2020-06-30", true);
        add("Fatima Khan", "fatima.khan@example.com", "Dubai", "3300.00", "2022-02-14", true);
        add("Noah Schmidt", "noah.schmidt@example.com", "Berlin", "150.25", "2017-09-05", false);
        add("Priya Nair", "priya.nair@example.com", "Bengaluru", "8120.40", "2023-03-19", true);
        add("Olivia Brown", "olivia.brown@example.com", "Toronto", "27000.00", "2016-12-01", true);
        add("Kenji Sato", "kenji.sato@example.com", "Tokyo", "640.00", "2024-05-27", true);
        add("Sofia Rossi", "sofia.rossi@example.com", "Milan", "19999.99", "2019-08-08", true);
        add("Arjun Mehta", "arjun.mehta@example.com", "Mumbai", "75.10", "2025-01-15", false);
        add("Emma Dubois", "emma.dubois@example.com", "Paris", "5600.00", "2021-07-04", true);
    }

    @Override
    public synchronized List<Customer> findAll() throws InterruptedException {
        return find(Query.all()).getItems();
    }

    @Override
    public synchronized Page<Customer> find(Query query) throws InterruptedException {
        Thread.sleep(600);
        Page<Customer> page = query.apply(customers, BeanProperties::get);
        List<Customer> copies = new ArrayList<>();
        for (Customer customer : page.getItems()) {
            copies.add(new Customer(customer));
        }
        return new Page<>(copies, page.getOffset(), page.getLimit(), page.getTotal());
    }

    /** A few orders per customer, the same every time. */
    @Override
    public List<Order> ordersOf(Customer customer) throws InterruptedException {
        Thread.sleep(250);
        List<Order> orders = new ArrayList<>();
        int count = 1 + customer.getId() % 4;
        for (int i = 0; i < count; i++) {
            LocalDate date = LocalDate.of(2026, 9, 28).minusDays(customer.getId() * 3L + i * 17L);
            BigDecimal amount = new BigDecimal(120 + (customer.getId() * 37 + i * 91) % 900).setScale(2);
            Order.Status status = i == 0 ? Order.Status.OPEN : (customer.getId() + i) % 5 == 0
                    ? Order.Status.OVERDUE : Order.Status.SHIPPED;
            orders.add(new Order(String.format("SO-%04d-%d", customer.getId(), count - i), date, amount, status));
        }
        return orders;
    }

    @Override
    public synchronized Customer save(Customer customer) throws InterruptedException {
        Thread.sleep(300);
        Customer stored = new Customer(customer);
        if (stored.getId() == 0) {
            stored.setId(nextId.getAndIncrement());
            customers.add(stored);
        } else {
            int index = customers.indexOf(stored);
            if (index < 0) {
                throw new IllegalStateException("Customer " + stored.getId() + " no longer exists");
            }
            customers.set(index, stored);
        }
        return new Customer(stored);
    }

    @Override
    public synchronized void delete(Customer customer) throws InterruptedException {
        Thread.sleep(300);
        customers.remove(customer);
    }

    private void add(String name, String email, String city, String balance, String since, boolean active) {
        customers.add(new Customer(nextId.getAndIncrement(), name, email, city, new BigDecimal(balance),
                LocalDate.parse(since), active));
    }
}
