package com.portal.team;

public record TeamSummaryDto(Long id, String name, String oidcGroupId) {

    public static TeamSummaryDto from(Team team) {
        return new TeamSummaryDto(team.id, team.name, team.oidcGroupId);
    }
}
