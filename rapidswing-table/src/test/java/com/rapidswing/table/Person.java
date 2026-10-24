package com.rapidswing.table;

import java.math.BigDecimal;

/** Test bean. */
class Person {

    private final int id;
    private String name;
    private final BigDecimal salary;

    Person(int id, String name, String salary) {
        this.id = id;
        this.name = name;
        this.salary = new BigDecimal(salary);
    }

    int getId() {
        return id;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    BigDecimal getSalary() {
        return salary;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Person && ((Person) other).id == id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    static BeanTableModel<Person> model() {
        return BeanTableModel.builder(Person.class)
                .column("ID", Integer.class, Person::getId)
                .column("Name", String.class, Person::getName).editable(Person::setName)
                .column("Salary", BigDecimal.class, Person::getSalary).format(s -> "$" + s.toPlainString())
                .build();
    }
}
