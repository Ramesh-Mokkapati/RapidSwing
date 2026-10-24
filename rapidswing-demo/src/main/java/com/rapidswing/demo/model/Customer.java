package com.rapidswing.demo.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Customer {

    private int id;
    private String name = "";
    private String email = "";
    private String city = "";
    private BigDecimal balance = BigDecimal.ZERO;
    private LocalDate since = LocalDate.now();
    private boolean active = true;

    /** A new, unsaved customer (id 0). */
    public Customer() {
    }

    public Customer(int id, String name, String email, String city, BigDecimal balance, LocalDate since,
                    boolean active) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.city = city;
        this.balance = balance;
        this.since = since;
        this.active = active;
    }

    /** Copy constructor, used to edit a customer without touching the original until it is saved. */
    public Customer(Customer other) {
        this(other.id, other.name, other.email, other.city, other.balance, other.since, other.active);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public LocalDate getSince() {
        return since;
    }

    public void setSince(LocalDate since) {
        this.since = since;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /** Customers are equal by id, so a refreshed or saved copy matches the row it came from. */
    @Override
    public boolean equals(Object other) {
        return other instanceof Customer && ((Customer) other).id == id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return name;
    }
}
