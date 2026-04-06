package com.portal.application;

import com.portal.auth.TeamContext;
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

    @GET
    public List<ApplicationSummaryDto> listApplications(
            @PathParam("teamId") Long teamId) {
        if (!teamContext.getTeamId().equals(teamId)) {
            throw new NotFoundException();
        }
        return applicationService.getTeamApplications().stream()
                .map(ApplicationSummaryDto::from)
                .toList();
    }
}
