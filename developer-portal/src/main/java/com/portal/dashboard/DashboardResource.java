package com.portal.dashboard;

import com.portal.auth.TeamContext;
import com.portal.team.Team;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/teams/{teamId}")
@Produces(MediaType.APPLICATION_JSON)
public class DashboardResource {

    @Inject
    DashboardService dashboardService;

    @Inject
    TeamContext teamContext;

    @GET
    @Path("dashboard")
    public TeamDashboardDto getTeamDashboard(@PathParam("teamId") Long teamId) {
        validateTeamAccess(teamId);
        return dashboardService.getTeamDashboard(teamId);
    }

    @GET
    @Path("applications/{appId}/activity")
    public AppActivityResponse getApplicationActivity(
            @PathParam("teamId") Long teamId,
            @PathParam("appId") Long appId) {
        validateTeamAccess(teamId);
        return dashboardService.getApplicationActivity(teamId, appId);
    }

    private void validateTeamAccess(Long teamId) {
        Team team = Team.findById(teamId);
        if (team == null || !teamContext.hasAccessToGroup(team.oidcGroupId)) {
            throw new NotFoundException();
        }
    }
}
