package com.portal.application;

import com.portal.auth.TeamContext;
import com.portal.team.Team;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApplicationServiceTest {

    @Inject
    ApplicationService applicationService;

    @InjectMock
    TeamContext teamContext;

    private Team testTeam;
    private Team otherTeam;
    private Application testApp;
    private Application crossTeamApp;

    @BeforeAll
    void setUpData() {
        testTeam = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "svc-test-team";
            t.oidcGroupId = "svc-test-team";
            t.persist();
            t.flush();
            return t;
        });

        otherTeam = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "svc-other-team";
            t.oidcGroupId = "svc-other-team";
            t.persist();
            t.flush();
            return t;
        });

        testApp = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "svc-alpha";
            a.teamId = testTeam.id;
            a.gitRepoUrl = "https://github.com/org/alpha.git";
            a.runtimeType = "quarkus";
            a.persist();
            a.flush();
            return a;
        });

        crossTeamApp = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "svc-other-app";
            a.teamId = otherTeam.id;
            a.gitRepoUrl = "https://github.com/org/other.git";
            a.runtimeType = "spring-boot";
            a.persist();
            a.flush();
            return a;
        });
    }

    @BeforeEach
    void setupMocks() {
        Mockito.when(teamContext.getTeamId()).thenReturn(testTeam.id);
    }

    @Test
    void getTeamApplicationsReturnsAppsForTeam() {
        List<Application> apps = applicationService.getTeamApplications();
        assertTrue(apps.stream().anyMatch(a -> a.name.equals("svc-alpha")));
        assertTrue(apps.stream().noneMatch(a -> a.name.equals("svc-other-app")));
    }

    @Test
    void getApplicationByIdReturnsAppWhenTeamMatches() {
        Application app = applicationService.getApplicationById(testApp.id);
        assertEquals("svc-alpha", app.name);
        assertEquals(testTeam.id, app.teamId);
    }

    @Test
    void getApplicationByIdThrowsNotFoundForCrossTeam() {
        assertThrows(NotFoundException.class,
                () -> applicationService.getApplicationById(crossTeamApp.id));
    }

    @Test
    void getApplicationByIdThrowsNotFoundForNonExistent() {
        assertThrows(NotFoundException.class,
                () -> applicationService.getApplicationById(999999L));
    }
}
