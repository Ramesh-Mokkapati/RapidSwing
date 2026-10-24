package com.rapidswing.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A signed-in user and their roles. Immutable. Two users are equal when their user names are equal.
 */
public final class User {

    private final String username;
    private final String displayName;
    private final Set<String> roles;

    public User(String username, String displayName, Set<String> roles) {
        this.username = Objects.requireNonNull(username, "username");
        this.displayName = displayName != null ? displayName : username;
        this.roles = Collections.unmodifiableSet(new LinkedHashSet<>(roles));
    }

    public static User of(String username, String displayName, String... roles) {
        return new User(username, displayName, new LinkedHashSet<>(Arrays.asList(roles)));
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean hasAnyRole(String... candidates) {
        for (String role : candidates) {
            if (roles.contains(role)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof User && ((User) other).username.equals(username);
    }

    @Override
    public int hashCode() {
        return username.hashCode();
    }

    @Override
    public String toString() {
        return displayName + " " + roles;
    }
}
