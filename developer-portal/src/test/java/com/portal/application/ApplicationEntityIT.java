package com.portal.application;

import com.portal.team.Team;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ApplicationEntityIT {

    private Team createTeam(String name, String oidcGroupId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Team team = new Team();
            team.name = name;
            team.oidcGroupId = oidcGroupId;
            team.persist();
            team.flush();
            return team;
        });
    }

    private Application createApplication(String name, Long teamId, String gitRepoUrl, String runtimeType) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Application app = new Application();
            app.name = name;
            app.teamId = teamId;
            app.gitRepoUrl = gitRepoUrl;
            app.runtimeType = runtimeType;
            app.persist();
            app.flush();
            return app;
        });
    }

    @Test
    void persistAndRetrieveApplication() {
        Team team = createTeam("app-persist-team", "app-persist-oidc");

        Application app = createApplication("my-app-persist", team.id,
                "https://github.com/org/my-app.git", "quarkus");

        assertNotNull(app.id);
        assertNotNull(app.createdAt);
        assertNotNull(app.updatedAt);

        Application found = QuarkusTransaction.requiringNew().call(() -> Application.findById(app.id));

        assertNotNull(found);
        assertEquals("my-app-persist", found.name);
        assertEquals(team.id, found.teamId);
        assertEquals("https://github.com/org/my-app.git", found.gitRepoUrl);
        assertEquals("quarkus", found.runtimeType);
        assertNull(found.onboardingPrUrl);
        assertNull(found.onboardedAt);
    }

    @Test
    void findByTeamReturnsOnlyAppsForGivenTeam() {
        Team teamA = createTeam("team-a-find", "team-a-find-oidc");
        Team teamB = createTeam("team-b-find", "team-b-find-oidc");

        createApplication("app-beta", teamA.id, "https://github.com/org/beta.git", "spring-boot");
        createApplication("app-alpha", teamA.id, "https://github.com/org/alpha.git", "quarkus");
        createApplication("app-gamma", teamB.id, "https://github.com/org/gamma.git", "quarkus");

        List<Application> teamAApps = QuarkusTransaction.requiringNew()
                .call(() -> Application.findByTeam(teamA.id));
        List<Application> teamBApps = QuarkusTransaction.requiringNew()
                .call(() -> Application.findByTeam(teamB.id));

        assertEquals(2, teamAApps.size());
        assertEquals("app-alpha", teamAApps.get(0).name);
        assertEquals("app-beta", teamAApps.get(1).name);

        assertEquals(1, teamBApps.size());
        assertEquals("app-gamma", teamBApps.get(0).name);
    }

    @Test
    void uniqueConstraintRejectsDuplicateNamePerTeam() {
        Team team = createTeam("dup-app-team", "dup-app-team-oidc");

        createApplication("dup-app-name", team.id, "https://github.com/org/first.git", "quarkus");

        assertThrows(PersistenceException.class, () ->
                createApplication("dup-app-name", team.id, "https://github.com/org/second.git", "spring-boot")
        );
    }

    @Test
    void sameNameAllowedInDifferentTeams() {
        Team teamX = createTeam("team-x-samename", "team-x-samename-oidc");
        Team teamY = createTeam("team-y-samename", "team-y-samename-oidc");

        Application appX = createApplication("shared-name", teamX.id,
                "https://github.com/orgX/app.git", "quarkus");
        Application appY = createApplication("shared-name", teamY.id,
                "https://github.com/orgY/app.git", "quarkus");

        assertNotNull(appX.id);
        assertNotNull(appY.id);
        assertNotEquals(appX.id, appY.id);
    }
}
