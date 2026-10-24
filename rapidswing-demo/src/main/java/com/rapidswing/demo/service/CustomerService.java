package com.rapidswing.demo.service;

import com.rapidswing.crud.CrudRepository;
import com.rapidswing.demo.model.Customer;
import com.rapidswing.demo.model.Order;

import java.util.Collections;
import java.util.List;

/** Customer data access. Methods may be slow: call them from background tasks, never on the EDT. */
public interface CustomerService extends CrudRepository<Customer> {

    /** The customer's orders, newest first. */
    default List<Order> ordersOf(Customer customer) throws Exception {
        return Collections.emptyList();
    }
}
