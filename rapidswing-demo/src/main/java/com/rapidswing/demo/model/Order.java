package com.rapidswing.demo.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** An order of a customer, shown as a detail table under the customer form. */
public class Order {

    public enum Status { OPEN, SHIPPED, OVERDUE }

    private final String number;
    private final LocalDate date;
    private final BigDecimal amount;
    private final Status status;

    public Order(String number, LocalDate date, BigDecimal amount, Status status) {
        this.number = number;
        this.date = date;
        this.amount = amount;
        this.status = status;
    }

    public String getNumber() {
        return number;
    }

    public LocalDate getDate() {
        return date;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Status getStatus() {
        return status;
    }
}
