package com.portal.onboarding;

import com.portal.auth.TeamContext;
import com.portal.integration.PortalIntegrationException;
import com.portal.integration.git.GitProvider;
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

@Path("/api/v1/teams/{teamId}/applications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OnboardingResource {

    @Inject
    GitProvider gitProvider;

    @Inject
    ContractValidator contractValidator;

    @Inject
    OnboardingService onboardingService;

    @Inject
    TeamContext teamContext;

    @POST
    @Path("/onboard")
    public ContractValidationResult onboard(@PathParam("teamId") String teamId,
                                            @Valid ValidateRepoRequest request) {
        verifyTeamAccess(teamId);
        try {
            gitProvider.validateRepoAccess(request.gitRepoUrl());
        } catch (PortalIntegrationException e) {
            throw new PortalIntegrationException("git", "validateRepoAccess",
                    "Cannot access repository — check the URL and ensure the portal has read access", null, e);
        }
        return contractValidator.validate(request.gitRepoUrl());
    }

    @POST
    @Path("/onboard/plan")
    public OnboardingPlanResult plan(@PathParam("teamId") String teamId,
                                     @Valid OnboardingPlanRequest request) {
        verifyTeamAccess(teamId);
        return onboardingService.buildPlan(request.appName(), request);
    }

    @POST
    @Path("/onboard/confirm")
    public Response confirm(@PathParam("teamId") String teamId,
                            @Valid OnboardingConfirmRequest request) {
        verifyTeamAccess(teamId);
        OnboardingResultDto result = onboardingService.confirmOnboarding(request);
        return Response.status(Response.Status.CREATED).entity(result).build();
    }

    private void verifyTeamAccess(String teamId) {
        if (!teamId.equals(teamContext.getTeamIdentifier())
                && (teamContext.getTeamId() == null || !teamId.equals(teamContext.getTeamId().toString()))) {
            throw new NotFoundException("Team not found");
        }
    }
}
