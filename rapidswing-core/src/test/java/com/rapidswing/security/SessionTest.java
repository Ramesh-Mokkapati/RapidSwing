package com.rapidswing.security;

import com.rapidswing.property.Property;
import com.rapidswing.ui.AppAction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTest {

    private final User admin = User.of("ada", "Ada", "admin", "sales");
    private final User viewer = User.of("val", "Val", "viewer");

    @Test
    void rolesFollowTheSignedInUser() {
        Session session = new Session();
        assertFalse(session.isLoggedIn());
        assertFalse(session.hasRole("admin"));

        session.login(admin);
        assertTrue(session.hasRole("admin"));
        assertTrue(session.hasAnyRole("viewer", "sales"));
        assertFalse(session.hasAnyRole("viewer"));

        session.logout();
        assertNull(session.getUser());
        assertFalse(session.hasAnyRole("admin", "sales"));
    }

    @Test
    void roleFlagsAreLiveAndCached() {
        Session session = new Session();
        Property<Boolean> canEdit = session.anyRole("admin", "sales");
        List<Boolean> changes = new ArrayList<>();
        canEdit.onChange(changes::add);

        session.login(viewer);
        session.logout();
        session.login(admin);
        session.login(viewer);

        assertEquals(Arrays.asList(true, false), changes);
        assertSame(canEdit, session.anyRole("admin", "sales"));
    }

    @Test
    void requireGuardsServiceCalls() {
        Session session = new Session();
        session.login(viewer);

        AccessDeniedException e = assertThrows(AccessDeniedException.class, () -> session.require("admin"));
        assertEquals("You do not have permission to do this.", e.getMessage());
        assertDoesNotThrow(() -> session.require("admin", "viewer"));
    }

    @Test
    void actionsArePermittedOnlyForTheirRoles() {
        Session session = new Session();
        List<String> runs = new ArrayList<>();
        AppAction delete = AppAction.of("Delete", () -> runs.add("delete")).requiresRole(session, "admin");

        assertFalse(delete.isEnabled());
        assertFalse(delete.permitted().get());
        delete.actionPerformed(null);
        assertTrue(runs.isEmpty());

        session.login(admin);
        assertTrue(delete.isEnabled());
        delete.actionPerformed(null);
        assertEquals(1, runs.size());

        // Enabled and permitted are independent: neither one overrides the other.
        delete.setEnabled(false);
        session.logout();
        session.login(admin);
        assertFalse(delete.isEnabled());
        delete.setEnabled(true);
        assertTrue(delete.isEnabled());
    }

    @Test
    void userEqualityIsByUsername() {
        assertEquals(User.of("ada", "Ada"), User.of("ada", "Ada Lovelace", "admin"));
        assertTrue(admin.getRoles().contains("sales"));
        assertThrows(UnsupportedOperationException.class, () -> admin.getRoles().add("root"));
    }
}
