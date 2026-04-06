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
import java.util.List;

/**
 * Dev-only filter that populates {@link TeamContext} with sensible defaults
 * so the Casbin {@link PermissionFilter} can function without real OIDC tokens.
 *
 * <p>Override the role via query param: {@code ?role=admin} or {@code ?role=lead}.
 * Override active team via {@code ?team=team-2} or {@code X-Dev-Team} header.
 * The dev user belongs to all seeded teams so the team switcher works.
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 5)
@ApplicationScoped
@IfBuildProfile("dev")
public class DevAuthFilter implements ContainerRequestFilter {

    private static final List<String> DEV_TEAM_GROUPS = List.of("team-1", "team-2");

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

        String teamHeader = ctx.getHeaderString("X-Dev-Team");
        String teamParam = ctx.getUriInfo().getQueryParameters().getFirst("team");
        String teamName = teamHeader != null ? teamHeader : teamParam != null ? teamParam : "team-1";

        teamContext.setTeamIdentifier(teamName);
        teamContext.setTeamGroups(DEV_TEAM_GROUPS);
        teamContext.setRole(role);

        Team team = teamService.findOrCreate(teamName);
        teamContext.setTeamId(team.id);
    }
}
