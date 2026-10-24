package com.rapidswing.security;

import com.rapidswing.i18n.I18n;
import com.rapidswing.property.Property;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is signed in, and what they may do.
 * <p>
 * Role checks in the user interface hide and disable what a user cannot use, but they are a convenience,
 * not a security boundary: anyone who runs the client can bypass them. Enforce access where the data is,
 * on the server or in the database, or at least in your service layer with {@link #require}.
 * <p>
 * Change the user on the EDT. {@link #hasRole}, {@link #hasAnyRole} and {@link #require} may be called
 * from any thread, e.g. from services running in background tasks.
 */
public class Session {

    private final Property<User> user = new Property<>();
    private final Map<List<String>, Property<Boolean>> roleProperties = new ConcurrentHashMap<>();
    private volatile User current;
    private volatile Instant loginTime;

    /** The signed-in user, or {@code null}. Observe it here; change it with {@link #login} and {@link #logout}. */
    public Property<User> user() {
        return user;
    }

    public User getUser() {
        return current;
    }

    /** When the current user signed in, or {@code null}. */
    public Instant getLoginTime() {
        return loginTime;
    }

    public boolean isLoggedIn() {
        return current != null;
    }

    public void login(User newUser) {
        current = Objects.requireNonNull(newUser, "newUser");
        loginTime = Instant.now();
        user.set(newUser);
    }

    public void logout() {
        current = null;
        loginTime = null;
        user.set(null);
    }

    public boolean hasRole(String role) {
        User signedIn = current;
        return signedIn != null && signedIn.hasRole(role);
    }

    public boolean hasAnyRole(String... roles) {
        User signedIn = current;
        return signedIn != null && signedIn.hasAnyRole(roles);
    }

    /**
     * A live "has any of these roles" flag, e.g. for {@code AppAction.requires(...)} or
     * {@code ViewManager.restrict(...)}. Properties are cached per role list; use them for long-lived
     * things such as menu actions. Views that are rebuilt per sign-in can simply call {@link #hasAnyRole}.
     */
    public Property<Boolean> anyRole(String... roles) {
        return roleProperties.computeIfAbsent(Arrays.asList(roles.clone()), key -> {
            Property<Boolean> allowed = new Property<>(hasAnyRole(roles));
            user.onChange(newUser -> allowed.set(hasAnyRole(roles)));
            return allowed;
        });
    }

    /** Throws {@link AccessDeniedException} unless the current user has one of the roles. */
    public void require(String... roles) {
        if (!hasAnyRole(roles)) {
            throw new AccessDeniedException(I18n.global().get("security.denied"));
        }
    }
}
