package com.portal.application;

import com.portal.auth.TeamContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.util.List;

/**
 * Team-scoped business logic for application queries.
 * All operations are automatically scoped to the caller's team via {@link TeamContext}.
 */
@ApplicationScoped
public class ApplicationService {

    @Inject
    TeamContext teamContext;

    public List<Application> getTeamApplications() {
        return Application.findByTeam(teamContext.getTeamId());
    }

    /**
     * Returns applications for the given team after verifying the caller has access.
     * Defense-in-depth: the resource layer validates OIDC group membership;
     * this check ensures the service cannot be misused by internal callers
     * passing an unvalidated teamId.
     */
    public List<Application> getApplicationsForTeam(Long teamId) {
        if (!teamContext.getTeamId().equals(teamId)) {
            throw new NotFoundException();
        }
        return Application.findByTeam(teamId);
    }

    /**
     * Returns an application by ID if it belongs to the caller's team.
     * Returns 404 for both missing and cross-team resources.
     */
    public Application getApplicationById(Long appId) {
        Application app = Application.findById(appId);
        if (app == null || !app.teamId.equals(teamContext.getTeamId())) {
            throw new NotFoundException();
        }
        return app;
    }
}
