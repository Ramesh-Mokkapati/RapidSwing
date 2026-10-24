package com.rapidswing.demo.model;

import com.rapidswing.property.Property;

import java.time.LocalDate;

/** View model for the profile form: plain properties, no Swing code. */
public class ProfileModel {

    private final Property<String> name = new Property<>("");
    private final Property<String> email = new Property<>("");
    private final Property<Integer> age = new Property<>();
    private final Property<LocalDate> birthday = new Property<>();
    private final Property<String> country = new Property<>("India");
    private final Property<Integer> satisfaction = new Property<>(7);
    private final Property<Boolean> newsletter = new Property<>(false);
    private final Property<String> password = new Property<>("");
    private final Property<String> confirmPassword = new Property<>("");
    private final Property<String> notes = new Property<>("");

    public Property<String> name() {
        return name;
    }

    public Property<String> email() {
        return email;
    }

    public Property<Integer> age() {
        return age;
    }

    public Property<LocalDate> birthday() {
        return birthday;
    }

    public Property<String> country() {
        return country;
    }

    public Property<Integer> satisfaction() {
        return satisfaction;
    }

    public Property<Boolean> newsletter() {
        return newsletter;
    }

    public Property<String> password() {
        return password;
    }

    public Property<String> confirmPassword() {
        return confirmPassword;
    }

    public Property<String> notes() {
        return notes;
    }

    public void loadSample() {
        name.set("Asha Rao");
        email.set("asha.rao@example.com");
        age.set(34);
        birthday.set(LocalDate.of(1992, 3, 14));
        country.set("Japan");
        satisfaction.set(9);
        newsletter.set(true);
        password.set("secret123");
        confirmPassword.set("secret123");
        notes.set("Prefers email contact.");
    }

    public void reset() {
        name.set("");
        email.set("");
        age.set(null);
        birthday.set(null);
        country.set("India");
        satisfaction.set(7);
        newsletter.set(false);
        password.set("");
        confirmPassword.set("");
        notes.set("");
    }

    public String summary() {
        return String.format("name=%s, email=%s, age=%s, birthday=%s, country=%s, satisfaction=%s, newsletter=%s",
                name.get(), email.get(), age.get(), birthday.get(), country.get(), satisfaction.get(),
                newsletter.get());
    }
}
