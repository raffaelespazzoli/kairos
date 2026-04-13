package com.portal.dashboard;

import com.portal.application.Application;
import com.portal.application.ApplicationService;
import com.portal.build.BuildService;
import com.portal.build.BuildSummaryDto;
import com.portal.deployment.DeploymentHistoryDto;
import com.portal.deployment.DeploymentService;
import com.portal.environment.EnvironmentChainEntryDto;
import com.portal.environment.EnvironmentChainResponse;
import com.portal.environment.EnvironmentService;
import com.portal.health.DoraMetricDto;
import com.portal.health.DoraMetricsDto;
import com.portal.health.DoraService;
import com.portal.health.EnvironmentHealthDto;
import com.portal.health.HealthResponse;
import com.portal.health.HealthService;
import com.portal.health.HealthStatus;
import com.portal.health.HealthStatusDto;
import com.portal.health.TimeSeriesPointDto;
import com.portal.integration.prometheus.model.DoraMetricType;
import com.portal.integration.prometheus.model.TrendDirection;
import com.portal.release.ReleaseService;
import com.portal.release.ReleaseSummaryDto;
import com.portal.team.Team;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Instant;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DashboardResourceIT {

    @InjectMock
    ApplicationService applicationService;

    @InjectMock
    EnvironmentService environmentService;

    @InjectMock
    HealthService healthService;

    @InjectMock
    DoraService doraService;

    @InjectMock
    BuildService buildService;

    @InjectMock
    ReleaseService releaseService;

    @InjectMock
    DeploymentService deploymentService;

    private Team testTeam;
    private Team otherTeam;

    @BeforeAll
    void setUpData() {
        testTeam = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "dashres-team";
            t.oidcGroupId = "dashres-team";
            t.persist();
            t.flush();
            return t;
        });

        otherTeam = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "dashres-other";
            t.oidcGroupId = "dashres-other";
            t.persist();
            t.flush();
            return t;
        });
    }

    private void stubFullDashboard() {
        Application app = new Application();
        app.id = 1L;
        app.name = "dash-payments";
        app.teamId = testTeam.id;
        app.runtimeType = "quarkus";
        app.gitRepoUrl = "https://github.com/org/payments.git";

        when(applicationService.getApplicationsForTeam(testTeam.id)).thenReturn(List.of(app));

        List<EnvironmentChainEntryDto> chain = List.of(
                new EnvironmentChainEntryDto("dev", "ocp-dev", "payments-dev", 0,
                        "HEALTHY", "1.0.0", Instant.now(), null, null, null, 1L, false),
                new EnvironmentChainEntryDto("prod", "ocp-prod", "payments-prod", 1,
                        "HEALTHY", "0.9.0", Instant.now(), null, null, null, 2L, true));
        when(environmentService.getEnvironmentChain(testTeam.id, 1L))
                .thenReturn(new EnvironmentChainResponse(chain, null));

        HealthStatusDto devHealth = new HealthStatusDto(HealthStatus.HEALTHY, List.of(), "payments-dev");
        HealthStatusDto prodHealth = new HealthStatusDto(HealthStatus.HEALTHY, List.of(), "payments-prod");
        when(healthService.getApplicationHealth(testTeam.id, 1L))
                .thenReturn(new HealthResponse(List.of(
                        new EnvironmentHealthDto("dev", devHealth, null, null),
                        new EnvironmentHealthDto("prod", prodHealth, null, null))));

        DoraMetricDto df = new DoraMetricDto(DoraMetricType.DEPLOYMENT_FREQUENCY, 5.0, 3.0,
                TrendDirection.IMPROVING, 66.7, "/wk",
                List.of(new TimeSeriesPointDto(1000L, 5.0)));
        when(doraService.getDoraMetrics(testTeam.id, 1L, null))
                .thenReturn(new DoraMetricsDto(List.of(df), "30d", true));

        Instant now = Instant.now();
        when(buildService.listBuilds(testTeam.id, 1L)).thenReturn(List.of(
                new BuildSummaryDto("b-1", "Passed", now.minusSeconds(300), null,
                        "45s", null, "dash-payments", null)));
        when(releaseService.listReleases(testTeam.id, 1L)).thenReturn(List.of(
                new ReleaseSummaryDto("v1.0.0", now.minusSeconds(200), null, "abc", null)));
        when(deploymentService.listDeployments(testTeam.id, 1L, null)).thenReturn(List.of(
                new DeploymentHistoryDto("d-1", "v1.0.0", "Deployed",
                        now.minusSeconds(100), null, "dev@example.com", "prod", null)));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "dashres-team"),
            @Claim(key = "role", value = "member")
    })
    void getDashboardReturns200WithExpectedJsonShape() {
        stubFullDashboard();

        given()
                .when()
                .get("/api/v1/teams/{teamId}/dashboard", testTeam.id)
                .then()
                .statusCode(200)
                .body("applications.size()", equalTo(1))
                .body("applications[0].applicationId", equalTo(1))
                .body("applications[0].applicationName", equalTo("dash-payments"))
                .body("applications[0].runtimeType", equalTo("quarkus"))
                .body("applications[0].environments.size()", equalTo(2))
                .body("applications[0].environments[0].environmentName", equalTo("dev"))
                .body("applications[0].environments[0].status", equalTo("HEALTHY"))
                .body("applications[0].environments[0].deployedVersion", equalTo("1.0.0"))
                .body("applications[0].environments[0].statusDetail", equalTo("Healthy"))
                .body("dora.hasData", equalTo(true))
                .body("dora.metrics.size()", equalTo(1))
                .body("dora.metrics[0].type", equalTo("DEPLOYMENT_FREQUENCY"))
                .body("dora.timeRange", equalTo("30d"))
                .body("recentActivity.size()", equalTo(3))
                .body("recentActivity[0].eventType", anyOf(
                        equalTo("build"), equalTo("release"), equalTo("deployment")))
                .body("healthError", nullValue())
                .body("doraError", nullValue())
                .body("activityError", nullValue());
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "dashres-team"),
            @Claim(key = "role", value = "member")
    })
    void nonExistentTeamReturns404() {
        given()
                .when()
                .get("/api/v1/teams/{teamId}/dashboard", 999999L)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "dashres-team"),
            @Claim(key = "role", value = "member")
    })
    void crossTeamAccessReturns404() {
        given()
                .when()
                .get("/api/v1/teams/{teamId}/dashboard", otherTeam.id)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "lead@example.com", roles = "lead")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "dashres-team"),
            @Claim(key = "role", value = "lead")
    })
    void leadRoleCanAccessDashboard() {
        stubFullDashboard();

        given()
                .when()
                .get("/api/v1/teams/{teamId}/dashboard", testTeam.id)
                .then()
                .statusCode(200)
                .body("applications.size()", equalTo(1));
    }

    @Test
    @TestSecurity(user = "admin@example.com", roles = "admin")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "dashres-team"),
            @Claim(key = "role", value = "admin")
    })
    void adminRoleCanAccessDashboard() {
        stubFullDashboard();

        given()
                .when()
                .get("/api/v1/teams/{teamId}/dashboard", testTeam.id)
                .then()
                .statusCode(200)
                .body("applications.size()", equalTo(1));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "dashres-team"),
            @Claim(key = "role", value = "member")
    })
    void partialFailureStillReturns200() {
        Application app = new Application();
        app.id = 1L;
        app.name = "dash-payments";
        app.teamId = testTeam.id;
        app.runtimeType = "quarkus";
        app.gitRepoUrl = "https://github.com/org/payments.git";

        when(applicationService.getApplicationsForTeam(testTeam.id)).thenReturn(List.of(app));
        when(environmentService.getEnvironmentChain(testTeam.id, 1L))
                .thenReturn(new EnvironmentChainResponse(List.of(), null));
        when(healthService.getApplicationHealth(testTeam.id, 1L))
                .thenReturn(new HealthResponse(List.of()));
        when(doraService.getDoraMetrics(anyLong(), anyLong(), any()))
                .thenReturn(new DoraMetricsDto(List.of(), "30d", false));
        when(buildService.listBuilds(anyLong(), anyLong()))
                .thenThrow(new RuntimeException("Tekton unreachable"));
        when(releaseService.listReleases(anyLong(), anyLong())).thenReturn(List.of());
        when(deploymentService.listDeployments(anyLong(), anyLong(), any())).thenReturn(List.of());

        given()
                .when()
                .get("/api/v1/teams/{teamId}/dashboard", testTeam.id)
                .then()
                .statusCode(200)
                .body("applications.size()", equalTo(1))
                .body("activityError", notNullValue());
    }
}
