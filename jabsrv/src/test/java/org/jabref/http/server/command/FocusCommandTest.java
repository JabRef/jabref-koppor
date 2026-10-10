package org.jabref.http.server.command;

import org.jabref.http.server.ServerTest;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FocusCommandTest extends ServerTest {

    @Override
    protected Application configure() {
        ResourceConfig resourceConfig = new ResourceConfig(CommandResource.class);
        addGuiBridgeToResourceConfig(resourceConfig);
        addGsonToResourceConfig(resourceConfig);
        return resourceConfig.getApplication();
    }

    @Test
    void focusOnCliShouldReturnServerError() {
        String json = """
                {
                  "command": "focus"
                }
                """;
        Response response = target("/commands").request().post(Entity.json(json));
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertEquals("This command is not supported in CLI mode.", response.readEntity(String.class));
    }
}
