package com.portal.release;

import com.portal.auth.TeamContext;
import com.portal.team.Team;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/v1/teams/{teamId}/applications/{appId}/releases")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReleaseResource {

    @Inject
    ReleaseService releaseService;

    @Inject
    TeamContext teamContext;

    @GET
    public List<ReleaseSummaryDto> listReleases(@PathParam("teamId") Long teamId,
                                                @PathParam("appId") Long appId) {
        validateTeamAccess(teamId);
        return releaseService.listReleases(teamId, appId);
    }

    @POST
    public Response createRelease(@PathParam("teamId") Long teamId,
                                   @PathParam("appId") Long appId,
                                   CreateReleaseRequest request) {
        validateTeamAccess(teamId);
        ReleaseSummaryDto result = releaseService.createRelease(teamId, appId, request);
        return Response.status(Response.Status.CREATED).entity(result).build();
    }

    private void validateTeamAccess(Long teamId) {
        Team team = Team.findById(teamId);
        if (team == null || !teamContext.hasAccessToGroup(team.oidcGroupId)) {
            throw new NotFoundException();
        }
    }
}
