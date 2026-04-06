package com.portal.team;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * Domain service for team operations including auto-provisioning from OIDC claims.
 */
@ApplicationScoped
public class TeamService {

    /**
     * Finds the team by OIDC group identifier, creating it if it doesn't exist.
     */
    @Transactional
    public Team findOrCreate(String oidcGroupId) {
        Team team = Team.findByOidcGroupId(oidcGroupId);
        if (team != null) {
            return team;
        }

        Team newTeam = new Team();
        newTeam.name = oidcGroupId;
        newTeam.oidcGroupId = oidcGroupId;
        newTeam.persist();
        return newTeam;
    }

    public Team findByOidcGroupId(String oidcGroupId) {
        return Team.findByOidcGroupId(oidcGroupId);
    }

    /**
     * Returns all teams matching any of the given OIDC group identifiers.
     */
    public List<Team> getTeamsForUser(List<String> teamGroups) {
        if (teamGroups == null || teamGroups.isEmpty()) {
            return List.of();
        }
        return Team.list("oidcGroupId in ?1", teamGroups);
    }
}
