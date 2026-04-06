package com.portal.auth;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonString;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import com.portal.team.Team;
import com.portal.team.TeamService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Extracts team and role from JWT claims and populates the request-scoped {@link TeamContext}.
 * Runs after OIDC token validation but before authorization checks.
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 10)
@ApplicationScoped
public class TeamContextFilter implements ContainerRequestFilter {

    @Inject
    JsonWebToken jwt;

    @Inject
    TeamContext teamContext;

    @Inject
    TeamService teamService;

    @ConfigProperty(name = "portal.oidc.team-claim", defaultValue = "team")
    String teamClaim;

    @ConfigProperty(name = "portal.oidc.role-claim", defaultValue = "role")
    String roleClaim;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (jwt.getRawToken() == null) {
            return;
        }

        List<String> groups = extractAllStringClaims(teamClaim);
        if (groups.isEmpty()) {
            requestContext.abortWith(buildErrorResponse(
                    Response.Status.FORBIDDEN,
                    "missing_team_claim",
                    "JWT does not contain a team claim",
                    "Expected claim: " + teamClaim,
                    "oidc-provider"));
            return;
        }

        String primaryTeam = groups.get(0);
        String role = extractStringClaim(roleClaim);
        teamContext.setTeamIdentifier(primaryTeam);
        teamContext.setTeamGroups(groups);
        teamContext.setRole(role != null && !role.isBlank() ? role : "member");

        Team persisted = teamService.findOrCreate(primaryTeam);
        teamContext.setTeamId(persisted.id);
    }

    /**
     * Extracts all values from a claim as a list, handling comma-separated strings,
     * JSON arrays, and collections.
     */
    List<String> extractAllStringClaims(String claimName) {
        Object raw = jwt.getClaim(claimName);
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof String s) {
            List<String> result = new ArrayList<>();
            for (String part : s.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) result.add(trimmed);
            }
            return result;
        }
        if (raw instanceof JsonArray arr) {
            List<String> result = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                result.add(arr.getString(i).trim());
            }
            return result;
        }
        if (raw instanceof Collection<?> col) {
            return col.stream().map(o -> o.toString().trim())
                    .filter(s -> !s.isEmpty()).toList();
        }
        String val = raw.toString().trim();
        return val.isEmpty() ? List.of() : List.of(val);
    }

    /**
     * Extracts a claim value as a string, handling string, array (first element),
     * and comma-separated values (first token).
     */
    String extractStringClaim(String claimName) {
        Object raw = jwt.getClaim(claimName);
        if (raw == null) {
            return null;
        }
        if (raw instanceof String s) {
            return s.contains(",") ? s.split(",")[0].trim() : s.trim();
        }
        if (raw instanceof JsonArray arr && !arr.isEmpty()) {
            return arr.getString(0).trim();
        }
        if (raw instanceof JsonString js) {
            String s = js.getString();
            return s.contains(",") ? s.split(",")[0].trim() : s.trim();
        }
        if (raw instanceof Collection<?> col && !col.isEmpty()) {
            return col.iterator().next().toString().trim();
        }
        return raw.toString().trim();
    }

    private Response buildErrorResponse(Response.Status status, String error,
                                        String message, String detail, String system) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity("""
                        {"error":"%s","message":"%s","detail":"%s","system":"%s","timestamp":"%s"}"""
                        .formatted(error, message, sanitize(detail), system, Instant.now().toString()))
                .build();
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "'").replace("\\", "");
    }
}
