package com.portal.environment;

import com.portal.auth.TeamContext;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/teams/{teamId}/applications/{appId}/environments")
@Produces(MediaType.APPLICATION_JSON)
public class EnvironmentResource {

    @Inject
    TeamContext teamContext;

    @Inject
    EnvironmentService environmentService;

    @GET
    public EnvironmentChainResponse getEnvironmentChain(
            @PathParam("teamId") Long teamId,
            @PathParam("appId") Long appId) {
        if (!teamContext.getTeamId().equals(teamId)) {
            throw new NotFoundException();
        }
        return environmentService.getEnvironmentChain(teamId, appId);
    }
}
