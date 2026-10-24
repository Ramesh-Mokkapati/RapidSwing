package com.rapidswing.rest;

import com.rapidswing.crud.CrudScreen;
import com.rapidswing.rest.sample.SampleApiServer;
import com.rapidswing.testing.AppHarness;
import com.rapidswing.testing.AppHarnessExtension;
import com.rapidswing.testing.Find;
import com.rapidswing.testing.Ui;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Screens built at runtime from the OpenAPI document, driven like a user would. */
class ApiScreensTest {

    @RegisterExtension
    final AppHarnessExtension harness = new AppHarnessExtension();

    private SampleApiServer server;
    private RestClient client;
    private OpenApiSpec spec;

    @BeforeEach
    void start() {
        server = SampleApiServer.start(0);
        client = new RestClient(server.getBaseUrl());
        spec = OpenApiSpec.fetch(client);
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void editsCustomersThroughTheApi() {
        AppHarness app = harness.get();
        ApiExplorer explorer = app.show("api", ctx -> new ApiExplorer(ctx, spec, client));
        CrudScreen<ApiRecord> customers = explorer.getCurrentScreen();
        Find find = Find.in(customers);

        assertEquals(3, find.table().getRowCount());
        Ui.onEdt(() -> customers.getGrid().select(customers.getGrid().getModel().getRow(0)));
        Ui.type(find.field("City"), "Pune");
        Ui.click(find.button("Save"));

        app.dialogs().assertNoErrors();
        assertEquals("Asha Rao saved", app.status());
        assertEquals("Pune", client.get("/api/customers/1").path("city").asText());
    }

    @Test
    void schemaRulesBecomeFormValidation() {
        AppHarness app = harness.get();
        CrudScreen<ApiRecord> customers = app.show("customers",
                ctx -> ApiScreens.create(ctx, spec.resource("customers"), client));
        Find find = Find.in(customers);

        Ui.click(find.button("New"));
        Ui.type(find.field("Name"), "Noah");
        Ui.type(find.field("Email"), "not-an-email");
        Ui.click(find.button("Save"));

        assertEquals("Enter a valid email address", customers.getForm().errorText("Email"));
        assertEquals(3, client.getArray("/api/customers").size());
    }

    @Test
    void screensOfferOnlyWhatTheApiAllows() {
        AppHarness app = harness.get();
        ApiExplorer explorer = app.show("api", ctx -> new ApiExplorer(ctx, spec, client));

        Ui.onEdt(() -> explorer.show("products"));
        Find products = Find.in(explorer.getCurrentScreen());
        products.button("New");
        assertThrows(AssertionError.class, () -> products.button("Delete"));

        Ui.onEdt(() -> explorer.show("orders"));
        Find orders = Find.in(explorer.getCurrentScreen());
        assertEquals(2, orders.table().getRowCount());
        assertThrows(AssertionError.class, () -> orders.button("New"));
        assertEquals("Placed on", orders.table().getColumnName(3));
        assertFalse(orders.containsText("Save"));
    }
}
