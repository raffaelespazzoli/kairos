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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Pattern TEAM_PATH_PATTERN = Pattern.compile("api/v1/teams/(\\d+)/");

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
        String teamName;
        if (teamHeader != null) {
            teamName = teamHeader;
        } else if (teamParam != null) {
            teamName = teamParam;
        } else {
            teamName = inferTeamFromPath(path);
        }

        teamContext.setTeamIdentifier(teamName);
        teamContext.setTeamGroups(DEV_TEAM_GROUPS);
        teamContext.setRole(role);

        Team team = teamService.findOrCreate(teamName);
        teamContext.setTeamId(team.id);
    }

    /**
     * Extracts the team ID from URL paths like {@code api/v1/teams/{id}/...} and
     * resolves it to the team name. Falls back to team-1 when the path doesn't
     * match or the team doesn't exist.
     */
    private String inferTeamFromPath(String path) {
        Matcher m = TEAM_PATH_PATTERN.matcher(path);
        if (m.find()) {
            try {
                Long teamId = Long.parseLong(m.group(1));
                Team t = Team.findById(teamId);
                if (t != null) {
                    return t.oidcGroupId;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return "team-1";
    }
}
