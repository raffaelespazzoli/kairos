package com.portal.auth;

import jakarta.enterprise.context.RequestScoped;

/**
 * Request-scoped bean populated by {@link TeamContextFilter} from JWT claims.
 * Injected into services/resources to scope data access by team.
 */
@RequestScoped
public class TeamContext {

    private String teamIdentifier;
    private String role;
    private Long teamId;

    public String getTeamIdentifier() {
        return teamIdentifier;
    }

    public void setTeamIdentifier(String teamIdentifier) {
        this.teamIdentifier = teamIdentifier;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getTeamId() {
        return teamId;
    }

    public void setTeamId(Long teamId) {
        this.teamId = teamId;
    }
}
