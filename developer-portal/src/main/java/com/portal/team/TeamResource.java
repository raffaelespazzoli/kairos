package com.portal.team;

import com.portal.auth.TeamContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/api/v1/teams")
@Produces(MediaType.APPLICATION_JSON)
public class TeamResource {

    @Inject
    TeamContext teamContext;

    @Inject
    TeamService teamService;

    @GET
    public List<TeamSummaryDto> getTeams() {
        return teamService.getTeamsForUser(teamContext.getTeamGroups())
                .stream()
                .map(TeamSummaryDto::from)
                .toList();
    }

    /**
     * Returns a team by ID, but only if it belongs to the caller's team scope.
     * Returns 404 for both non-existent teams and teams outside the caller's
     * scope — never reveals cross-team resource existence.
     */
    @GET
    @Path("/{teamId}")
    public TeamSummaryDto getTeamById(@PathParam("teamId") Long teamId) {
        Team team = Team.findById(teamId);
        if (team == null || !teamContext.getTeamGroups().contains(team.oidcGroupId)) {
            throw new NotFoundException();
        }
        return TeamSummaryDto.from(team);
    }
}
