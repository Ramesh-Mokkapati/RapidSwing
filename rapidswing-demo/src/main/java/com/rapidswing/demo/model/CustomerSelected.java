package com.rapidswing.demo.model;

/** Published on the event bus when a customer is selected in the customers view. */
public class CustomerSelected {

    private final Customer customer;

    public CustomerSelected(Customer customer) {
        this.customer = customer;
    }

    public Customer getCustomer() {
        return customer;
    }
}
