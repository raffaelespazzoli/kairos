package com.portal.environment;

import com.portal.auth.TeamContext;
import com.portal.team.Team;

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
        Team team = Team.findById(teamId);
        if (team == null || !teamContext.hasAccessToGroup(team.oidcGroupId)) {
            throw new NotFoundException();
        }
        return environmentService.getEnvironmentChain(teamId, appId);
    }
}
