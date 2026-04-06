package com.portal.integration.argocd;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Quarkus REST Client for the ArgoCD REST API.
 * Uses JsonNode for response flexibility — the ArgoCD response is deeply nested
 * and only a few fields are needed by the adapter.
 */
@RegisterRestClient(configKey = "argocd-api")
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface ArgoCdRestClient {

    @GET
    @Path("/applications/{name}")
    JsonNode getApplication(
            @PathParam("name") String name,
            @HeaderParam("Authorization") String authHeader);
}
