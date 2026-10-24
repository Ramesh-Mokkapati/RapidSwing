package com.rapidswing.demo;

import com.rapidswing.forms.Settings;
import com.rapidswing.property.Property;

import java.util.prefs.Preferences;

/** The demo's user settings, edited in Tools > Settings and read by the screens. */
public final class DemoSettings {

    public enum StartView { DASHBOARD, CUSTOMERS }

    private final Settings settings;
    private final Property<Integer> pageSize;
    private final Property<Integer> largeBalance;
    private final Property<StartView> startView;
    private final Property<Boolean> confirmSignOut;

    public DemoSettings(Preferences store) {
        settings = new Settings(store);
        startView = settings.category("settings.category.general").choice("start.view", "settings.startView",
                StartView.DASHBOARD);
        confirmSignOut = settings.bool("signOut.confirm", "settings.confirmSignOut", true);
        pageSize = settings.category("settings.category.customers").integer("customers.pageSize",
                "settings.pageSize", 10, 5, 200);
        largeBalance = settings.integer("customers.largeBalance", "settings.largeBalance", 20_000, 0, 1_000_000);
    }

    public Settings settings() {
        return settings;
    }

    /** Customers per page. */
    public Property<Integer> pageSize() {
        return pageSize;
    }

    /** Balances from this amount up are highlighted. */
    public Property<Integer> largeBalance() {
        return largeBalance;
    }

    public Property<StartView> startView() {
        return startView;
    }

    public Property<Boolean> confirmSignOut() {
        return confirmSignOut;
    }
}
