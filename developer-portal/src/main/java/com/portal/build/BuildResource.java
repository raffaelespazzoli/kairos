package com.portal.build;

import com.portal.auth.TeamContext;
import com.portal.team.Team;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/teams/{teamId}/applications/{appId}/builds")
@Produces(MediaType.APPLICATION_JSON)
public class BuildResource {

    @Inject
    BuildService buildService;

    @Inject
    TeamContext teamContext;

    @POST
    public Response triggerBuild(@PathParam("teamId") Long teamId,
                                 @PathParam("appId") Long appId) {
        Team team = Team.findById(teamId);
        if (team == null || !teamContext.hasAccessToGroup(team.oidcGroupId)) {
            throw new NotFoundException();
        }
        BuildSummaryDto result = buildService.triggerBuild(teamId, appId);
        return Response.status(Response.Status.CREATED).entity(result).build();
    }
}
