package com.rapidswing.binding;

import com.rapidswing.util.Edt;
import org.junit.jupiter.api.Test;

import javax.swing.JTextField;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class BeanPropertyTest {

    static class Person {
        private String name;

        Person(String name) {
            this.name = name;
        }

        String getName() {
            return name;
        }

        void setName(String name) {
            this.name = name;
        }
    }

    @Test
    void readsAndWritesThroughToBean() {
        Person person = new Person("Asha");
        BeanProperty<Person, String> name = BeanProperty.of(person, Person::getName, Person::setName);

        assertEquals("Asha", name.get());
        name.set("Liam");
        assertEquals("Liam", person.getName());
        assertSame(person, name.getBean());
    }

    @Test
    void reloadPicksUpDirectBeanChanges() {
        Edt.runAndWait(() -> {
            Person person = new Person("Asha");
            BeanProperty<Person, String> name = BeanProperty.of(person, Person::getName, Person::setName);
            JTextField field = new JTextField();
            Bindings.text(field, name);

            field.setText("Mei");
            assertEquals("Mei", person.getName());

            person.setName("Noah");
            name.reload();
            assertEquals("Noah", field.getText());
        });
    }
}
