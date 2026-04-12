package com.portal.health;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/teams/{teamId}/applications/{appId}/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    @Inject
    HealthService healthService;

    @GET
    public HealthResponse getApplicationHealth(@PathParam("teamId") Long teamId,
                                               @PathParam("appId") Long appId) {
        return healthService.getApplicationHealth(teamId, appId);
    }
}
