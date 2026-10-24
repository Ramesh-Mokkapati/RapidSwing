package com.rapidswing.demo;

import com.rapidswing.security.SimpleAuthenticator;

/**
 * The demo's roles and accounts.
 * <ul>
 *   <li>{@code admin} / {@code admin123}: everything, including deleting customers</li>
 *   <li>{@code sales} / {@code sales123}: create and edit customers, profile form</li>
 *   <li>{@code viewer} / {@code viewer123}: dashboard, customers read-only</li>
 * </ul>
 * Only password hashes are stored. These accounts exist for the demo; never ship known passwords.
 */
public final class Roles {

    public static final String ADMIN = "admin";
    public static final String SALES = "sales";
    public static final String VIEWER = "viewer";

    private Roles() {
    }

    static SimpleAuthenticator demoAccounts() {
        return new SimpleAuthenticator()
                .addUser("admin", "Ada Admin",
                        "pbkdf2-sha256$310000$KeHKr03CZweCjaK9CNGa2A==$ode0J3nVeLIzUP++nPRZwTASLrXI05etoDGhq+/pzB4=",
                        ADMIN, SALES)
                .addUser("sales", "Sam Sales",
                        "pbkdf2-sha256$310000$AVj95R1BWq860GATv/HX1A==$DN5SIxWQ/ZWhtL2WI2lNodGdsSEx2i05vYTwi9Ke4b8=",
                        SALES)
                .addUser("viewer", "Val Viewer",
                        "pbkdf2-sha256$310000$IV7kF1VevbhTW5yAvGOKUQ==$3zdAIitvU31U0VjtF49m0MqkH69BL8JsRew0WeoN894=",
                        VIEWER);
    }
}
