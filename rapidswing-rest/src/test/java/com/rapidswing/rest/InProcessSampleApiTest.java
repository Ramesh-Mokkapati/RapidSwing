package com.rapidswing.rest;

import com.rapidswing.rest.sample.SampleApiServer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sample API without a socket, as the demo uses it in the browser. */
class InProcessSampleApiTest {

    @Test
    void answersWithoutASocket() {
        SampleApiServer server = SampleApiServer.inProcess();
        RestClient client = server.client();
        OpenApiSpec spec = OpenApiSpec.fetch(client);
        RestRepository customers = new RestRepository(client, spec.resource("customers"));

        assertTrue(server.isInProcess());
        assertEquals(3, customers.findAll().size());
        assertEquals("UP", client.get("/api/health").path("status").asText());
        List<ApiRecord> orders = new RestRepository(client, spec.resource("orders")).findAll();
        assertEquals(2, orders.size());
    }

    @Test
    void reportsErrorsLikeOverHttp() {
        RestClient client = SampleApiServer.inProcess().client();

        RestException error = assertThrows(RestException.class, () -> client.get("/api/customers/99"));
        assertEquals(404, error.getStatus());
        assertEquals("customer 99 not found", error.getMessage());
    }
}
