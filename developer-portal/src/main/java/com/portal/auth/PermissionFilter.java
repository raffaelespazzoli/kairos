package com.portal.auth;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.util.Set;

/**
 * JAX-RS filter that enforces Casbin RBAC on every API request.
 * Runs after {@link TeamContextFilter} (which populates {@link TeamContext}).
 *
 * <p>Priority ordering:
 * <ul>
 *   <li>{@code TeamContextFilter}: {@code Priorities.AUTHENTICATION + 10} (1010)</li>
 *   <li>{@code PermissionFilter}: {@code Priorities.AUTHORIZATION} (2000)</li>
 * </ul>
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
@ApplicationScoped
public class PermissionFilter implements ContainerRequestFilter {

    @Inject
    CasbinEnforcer casbinEnforcer;

    @Inject
    TeamContext teamContext;

    private static final Set<String> ACTION_SEGMENTS = Set.of("onboard", "plan", "confirm", "logs");

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        String method = ctx.getMethod();

        if (!path.startsWith("api/v1/")) {
            return;
        }

        String role = teamContext.getRole();
        if (role == null) {
            throw new PortalAuthorizationException("none", "unknown", "unknown");
        }

        String resource = extractResource(path);
        String action = mapAction(method, path, resource);

        // Production deployment gating: deployment endpoints MUST include ?env=prod
        // to trigger the deploy-prod action. This is the API contract — callers that
        // omit the parameter are treated as non-production deployments. When Story 5.x
        // implements the deployment resource, the service layer should additionally
        // verify the target environment's is_production flag as a defense-in-depth check.
        if ("deployments".equals(resource) && "POST".equals(method)) {
            String envParam = ctx.getUriInfo().getQueryParameters().getFirst("env");
            if ("prod".equals(envParam)) {
                action = "deploy-prod";
            }
        }

        if (!casbinEnforcer.enforce(role, resource, action)) {
            throw new PortalAuthorizationException(role, resource, action);
        }
    }

    /**
     * Maps a URL path to the Casbin resource type by extracting the terminal
     * meaningful path segment.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code api/v1/admin/clusters} → {@code clusters}</li>
     *   <li>{@code api/v1/admin/clusters/5} → {@code clusters}</li>
     *   <li>{@code api/v1/teams} → {@code teams}</li>
     *   <li>{@code api/v1/teams/1/applications} → {@code applications}</li>
     *   <li>{@code api/v1/teams/1/applications/2/builds} → {@code builds}</li>
     * </ul>
     */
    String extractResource(String path) {
        String cleanPath = stripQueryString(path);

        if (cleanPath.startsWith("api/v1/admin/")) {
            String adminPath = cleanPath.substring("api/v1/admin/".length());
            String[] parts = adminPath.split("/");
            return parts[0];
        }

        String afterPrefix = cleanPath.substring("api/v1/".length());
        String[] segments = afterPrefix.split("/");

        String lastResource = segments[0];
        for (int i = 1; i < segments.length; i++) {
            if (!isId(segments[i]) && !ACTION_SEGMENTS.contains(segments[i])) {
                lastResource = segments[i];
            }
        }
        return lastResource;
    }

    /**
     * Maps HTTP method + path context to a Casbin action string.
     * Production deployment detection is handled separately in {@link #filter}
     * using the query parameter {@code env=prod}.
     */
    String mapAction(String method, String path, String resource) {
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return "read";
        }

        if ("DELETE".equals(method)) {
            return "delete";
        }

        if ("PUT".equals(method) || "PATCH".equals(method)) {
            return "update";
        }

        if ("POST".equals(method)) {
            if (path.contains("/onboard")) {
                return "onboard";
            }
            if ("builds".equals(resource)) {
                return "trigger";
            }
            if ("deployments".equals(resource)) {
                return "deploy";
            }
            return "create";
        }

        return "read";
    }

    private String stripQueryString(String path) {
        int idx = path.indexOf('?');
        return idx >= 0 ? path.substring(0, idx) : path;
    }

    private boolean isId(String segment) {
        if (segment.isEmpty()) {
            return false;
        }
        // Numeric IDs
        if (Character.isDigit(segment.charAt(0))) {
            return true;
        }
        // UUID-like segments (contains hyphens and hex characters)
        return segment.length() > 8 && segment.contains("-")
                && segment.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '-');
    }
}
