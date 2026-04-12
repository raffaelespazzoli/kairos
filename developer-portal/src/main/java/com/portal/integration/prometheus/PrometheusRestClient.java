package com.portal.integration.prometheus;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Quarkus REST Client for the Prometheus HTTP API.
 * Uses JsonNode for response flexibility — only the {@code data.result[0].value[1]}
 * path is needed for instant query vector results.
 */
@RegisterRestClient(configKey = "prometheus-api")
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface PrometheusRestClient {

    @GET
    @Path("/query")
    JsonNode query(@QueryParam("query") String query);
}
