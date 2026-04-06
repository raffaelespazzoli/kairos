package com.portal.auth;

import jakarta.enterprise.context.RequestScoped;
import java.util.List;

/**
 * Request-scoped bean populated by {@link TeamContextFilter} from JWT claims.
 * Injected into services/resources to scope data access by team.
 */
@RequestScoped
public class TeamContext {

    private String teamIdentifier;
    private List<String> teamGroups = List.of();
    private String role;
    private Long teamId;

    public String getTeamIdentifier() {
        return teamIdentifier;
    }

    public void setTeamIdentifier(String teamIdentifier) {
        this.teamIdentifier = teamIdentifier;
    }

    /** All OIDC group IDs the user belongs to. */
    public List<String> getTeamGroups() {
        return teamGroups;
    }

    public void setTeamGroups(List<String> teamGroups) {
        this.teamGroups = teamGroups;
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

    /**
     * Returns true if the given OIDC group ID belongs to this user's team groups.
     */
    public boolean hasAccessToGroup(String oidcGroupId) {
        return teamGroups.contains(oidcGroupId);
    }
}
