package com.portal.auth;

import com.portal.team.Team;
import com.portal.team.TeamService;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * Dev-only filter that populates {@link TeamContext} with sensible defaults
 * so the Casbin {@link PermissionFilter} can function without real OIDC tokens.
 *
 * <p>Override the role via query param: {@code ?role=admin} or {@code ?role=lead}.
 * Defaults to {@code member} / team {@code platform}.
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 5)
@ApplicationScoped
@IfBuildProfile("dev")
public class DevAuthFilter implements ContainerRequestFilter {

    @Inject
    TeamContext teamContext;

    @Inject
    TeamService teamService;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();
        if (!path.startsWith("/api/") && !path.startsWith("api/")) {
            return;
        }

        String roleHeader = ctx.getHeaderString("X-Dev-Role");
        String roleParam = ctx.getUriInfo().getQueryParameters().getFirst("role");
        String candidate = roleHeader != null ? roleHeader : roleParam;
        String role = ("admin".equals(candidate) || "lead".equals(candidate))
                ? candidate : "member";

        String teamName = "platform";
        teamContext.setTeamIdentifier(teamName);
        teamContext.setRole(role);

        Team team = teamService.findOrCreate(teamName);
        teamContext.setTeamId(team.id);
    }
}
