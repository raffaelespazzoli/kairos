package com.portal.auth;

/**
 * Thrown when a Casbin permission check denies the requested action.
 * Caught by {@link com.portal.common.GlobalExceptionMapper} to produce a 403 response.
 */
public class PortalAuthorizationException extends RuntimeException {

    private final String role;
    private final String resource;
    private final String action;

    public PortalAuthorizationException(String role, String resource, String action) {
        super("Role '%s' is not permitted to '%s' on '%s'".formatted(role, action, resource));
        this.role = role;
        this.resource = resource;
        this.action = action;
    }

    public String getRole() {
        return role;
    }

    public String getResource() {
        return resource;
    }

    public String getAction() {
        return action;
    }
}
