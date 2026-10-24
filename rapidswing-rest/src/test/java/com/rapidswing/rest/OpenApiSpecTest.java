package com.rapidswing.rest;

import com.rapidswing.rest.sample.SampleApiServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiSpecTest {

    private static SampleApiServer server;
    private static OpenApiSpec spec;

    @BeforeAll
    static void start() {
        server = SampleApiServer.start(0);
        spec = OpenApiSpec.fetch(new RestClient(server.getBaseUrl()));
    }

    @AfterAll
    static void stop() {
        server.close();
    }

    @Test
    void findsCrudResourcesButNotOtherEndpoints() {
        List<String> names = new ArrayList<>();
        spec.getResources().forEach(resource -> names.add(resource.getName()));

        assertEquals(Arrays.asList("customers", "products", "orders"), names);
        assertEquals("Sample Shop API", spec.getTitle());
        assertEquals(server.getBaseUrl(), spec.getServerUrl());
    }

    @Test
    void readsFieldTypesAndConstraints() {
        ApiResource customers = spec.resource("customers");
        List<String> fields = new ArrayList<>();
        customers.getFields().forEach(field -> fields.add(field.getName() + ":" + field.getType()));

        assertEquals(Arrays.asList("id:LONG", "name:STRING", "email:STRING", "city:STRING", "balance:DECIMAL",
                "since:DATE", "active:BOOLEAN", "tier:ENUM"), fields, "the nested address object is left out");
        ApiField id = customers.getFields().get(0);
        ApiField name = customers.getFields().get(1);
        ApiField email = customers.getFields().get(2);
        assertTrue(id.isReadOnly());
        assertTrue(name.isRequired());
        assertEquals(80, name.getMaxLength());
        assertTrue(email.isEmail());
        assertEquals(Arrays.asList("BRONZE", "SILVER", "GOLD"), customers.getFields().get(7).getEnumValues());
        assertEquals("Customer", customers.getSingularTitle());
        assertEquals("/api/customers/42", customers.itemPathFor(42L));
    }

    @Test
    void capabilitiesFollowTheAvailableOperations() {
        ApiResource customers = spec.resource("customers");
        ApiResource products = spec.resource("products");
        ApiResource orders = spec.resource("orders");

        assertTrue(customers.canCreate() && customers.canUpdate() && customers.canDelete());
        assertTrue(products.canCreate() && products.canUpdate());
        assertFalse(products.canDelete());
        assertEquals("Product", products.getSingularTitle(), "the Dto suffix is dropped");
        assertEquals(new BigDecimal("0"), products.getFields().get(2).getMinimum());
        assertFalse(orders.canCreate() || orders.canUpdate() || orders.canDelete());
        assertNull(orders.getItemPath());
        assertEquals(4, orders.getFields().size(), "fields come from the Spring Data page's content items");
    }

    @Test
    void labelsAreReadable() {
        assertEquals("First name", new ApiField("firstName", ApiField.Type.STRING, false, false, null, null, null,
                null, null, false, new ArrayList<>()).getLabel());
        assertEquals("Placed on", spec.resource("orders").getFields().get(3).getLabel());
    }

    @Test
    void rejectsSwagger2AndNonOpenApiDocuments() {
        assertThrows(IllegalArgumentException.class, () -> OpenApiSpec.parse("{\"swagger\":\"2.0\",\"paths\":{}}"));
        assertThrows(IllegalArgumentException.class, () -> OpenApiSpec.parse("{\"hello\":\"world\"}"));
        assertThrows(IllegalArgumentException.class, () -> OpenApiSpec.parse("not json"));
    }
}
