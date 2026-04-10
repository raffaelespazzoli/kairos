package com.portal.deployment;

import com.portal.auth.TeamContext;
import com.portal.team.Team;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/teams/{teamId}/applications/{appId}/deployments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DeploymentResource {

    @Inject
    DeploymentService deploymentService;

    @Inject
    TeamContext teamContext;

    @POST
    public Response deploy(@PathParam("teamId") Long teamId,
                           @PathParam("appId") Long appId,
                           @Valid DeployRequest request) {
        validateTeamAccess(teamId);
        DeploymentStatusDto result = deploymentService.deployRelease(teamId, appId, request);
        return Response.status(Response.Status.CREATED).entity(result).build();
    }

    private void validateTeamAccess(Long teamId) {
        Team team = Team.findById(teamId);
        if (team == null || !teamContext.hasAccessToGroup(team.oidcGroupId)) {
            throw new NotFoundException();
        }
    }
}
