package com.portal.application;

import com.portal.auth.TeamContext;
import com.portal.deeplink.DeepLinkService;
import com.portal.team.Team;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/api/v1/teams/{teamId}/applications")
@Produces(MediaType.APPLICATION_JSON)
public class ApplicationResource {

    @Inject
    TeamContext teamContext;

    @Inject
    ApplicationService applicationService;

    @Inject
    DeepLinkService deepLinkService;

    @GET
    public List<ApplicationSummaryDto> listApplications(
            @PathParam("teamId") Long teamId) {
        Team team = Team.findById(teamId);
        if (team == null || !teamContext.hasAccessToGroup(team.oidcGroupId)) {
            throw new NotFoundException();
        }
        return applicationService.getApplicationsForTeam(teamId).stream()
                .map(app -> ApplicationSummaryDto.from(
                        app,
                        deepLinkService.generateDevSpacesLink(app.gitRepoUrl).orElse(null)))
                .toList();
    }
}
