package com.rapidswing.rest;

import com.rapidswing.rest.sample.SampleApiServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestRepositoryTest {

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
    void readsTypedValues() {
        List<ApiRecord> customers = new RestRepository(client, spec.resource("customers")).findAll();

        ApiRecord asha = customers.get(0);
        assertEquals(3, customers.size());
        assertEquals(1L, asha.getId());
        assertEquals(new BigDecimal("12500.00"), asha.get("balance"));
        assertEquals(LocalDate.of(2019, 4, 12), asha.get("since"));
        assertEquals(Boolean.TRUE, asha.get("active"));
        assertEquals("GOLD", asha.get("tier"));
    }

    @Test
    void createsUpdatesAndDeletes() {
        RestRepository customers = new RestRepository(client, spec.resource("customers"));

        ApiRecord created = customers.save(new ApiRecord("id").set("name", "Noah Schmidt")
                .set("email", "noah@example.com").set("since", LocalDate.of(2026, 1, 2)));
        assertNotNull(created.getId());
        assertEquals(LocalDate.of(2026, 1, 2), created.get("since"));

        ApiRecord moved = customers.save(new ApiRecord(created).set("city", "Berlin"));
        assertEquals("Berlin", moved.get("city"));
        assertEquals(created, moved);

        customers.delete(moved);
        assertEquals(3, customers.findAll().size());
    }

    @Test
    void readsPagedLists() {
        List<ApiRecord> orders = new RestRepository(client, spec.resource("orders")).findAll();

        assertEquals(2, orders.size());
        assertEquals(new BigDecimal("268.90"), orders.get(0).get("total"));
    }

    @Test
    void serverErrorsKeepSpringsMessage() {
        RestRepository customers = new RestRepository(client, spec.resource("customers"));

        RestException invalid = assertThrows(RestException.class,
                () -> customers.save(new ApiRecord("id").set("name", "X").set("email", "nope")));
        assertEquals(400, invalid.getStatus());
        assertEquals("email: must be a well-formed email address", invalid.getMessage());

        RestException missing = assertThrows(RestException.class, () -> client.get("/api/customers/999"));
        assertEquals(404, missing.getStatus());
        assertEquals("customer 999 not found", missing.getMessage());
    }
}
