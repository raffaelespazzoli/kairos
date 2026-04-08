package com.portal.build;

import com.portal.application.Application;
import com.portal.cluster.Cluster;
import com.portal.integration.secrets.ClusterCredential;
import com.portal.integration.secrets.SecretManagerCredentialProvider;
import com.portal.integration.tekton.TektonAdapter;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BuildResourceIT {

    @InjectMock
    TektonAdapter tektonAdapter;

    @InjectMock
    SecretManagerCredentialProvider credentialProvider;

    private Team testTeam;
    private Team otherTeam;
    private Application testApp;
    private Application crossTeamApp;
    private Cluster buildCluster;

    @BeforeAll
    void setUpData() {
        testTeam = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "buildres-team";
            t.oidcGroupId = "buildres-team";
            t.persist();
            t.flush();
            return t;
        });

        otherTeam = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "buildres-other";
            t.oidcGroupId = "buildres-other";
            t.persist();
            t.flush();
            return t;
        });

        buildCluster = QuarkusTransaction.requiringNew().call(() -> {
            Cluster c = new Cluster();
            c.name = "buildres-ocp-dev";
            c.apiServerUrl = "https://api.buildres-dev.example.com:6443";
            c.persist();
            c.flush();
            return c;
        });

        testApp = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "buildres-payments";
            a.teamId = testTeam.id;
            a.gitRepoUrl = "https://github.com/org/payments.git";
            a.runtimeType = "quarkus";
            a.buildClusterId = buildCluster.id;
            a.buildNamespace = "buildres-team-payments-build";
            a.persist();
            a.flush();
            return a;
        });

        crossTeamApp = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "buildres-other-app";
            a.teamId = otherTeam.id;
            a.gitRepoUrl = "https://github.com/org/other.git";
            a.runtimeType = "quarkus";
            a.buildClusterId = buildCluster.id;
            a.buildNamespace = "other-team-other-app-build";
            a.persist();
            a.flush();
            return a;
        });
    }

    private void stubCredentials() {
        when(credentialProvider.getCredentials(anyString(), anyString()))
                .thenReturn(ClusterCredential.of("test-token", 3600));
    }

    // --- POST trigger build ---

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "buildres-team"),
            @Claim(key = "role", value = "member")
    })
    void triggerBuildReturns201() {
        stubCredentials();
        when(tektonAdapter.triggerBuild(
                eq("buildres-payments"),
                eq("buildres-team-payments-build"),
                eq("https://api.buildres-dev.example.com:6443"),
                eq("test-token")))
                .thenReturn(new BuildSummaryDto(
                        "buildres-payments-abc12", "Building", Instant.now(), null, null, null,
                        "buildres-payments",
                        "https://tekton.example.com/#/pipelineruns/buildres-payments-abc12"));

        given()
                .when()
                .post("/api/v1/teams/{teamId}/applications/{appId}/builds",
                        testTeam.id, testApp.id)
                .then()
                .statusCode(201)
                .body("buildId", equalTo("buildres-payments-abc12"))
                .body("status", equalTo("Building"))
                .body("applicationName", equalTo("buildres-payments"))
                .body("startedAt", notNullValue())
                .body("tektonDeepLink", notNullValue());
    }

    @Test
    @TestSecurity(user = "lead@example.com", roles = "lead")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "buildres-team"),
            @Claim(key = "role", value = "lead")
    })
    void leadCanTriggerBuild() {
        stubCredentials();
        when(tektonAdapter.triggerBuild(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new BuildSummaryDto(
                        "run-123", "Building", Instant.now(), null, null, null, "buildres-payments", null));

        given()
                .when()
                .post("/api/v1/teams/{teamId}/applications/{appId}/builds",
                        testTeam.id, testApp.id)
                .then()
                .statusCode(201)
                .body("status", equalTo("Building"));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "buildres-team"),
            @Claim(key = "role", value = "member")
    })
    void triggerBuildForOtherTeamAppReturns404() {
        given()
                .when()
                .post("/api/v1/teams/{teamId}/applications/{appId}/builds",
                        testTeam.id, crossTeamApp.id)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "buildres-team"),
            @Claim(key = "role", value = "member")
    })
    void idorAttemptWithOtherTeamIdReturns404() {
        given()
                .when()
                .post("/api/v1/teams/{teamId}/applications/{appId}/builds",
                        otherTeam.id, crossTeamApp.id)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "buildres-team"),
            @Claim(key = "role", value = "member")
    })
    void triggerBuildForNonExistentAppReturns404() {
        given()
                .when()
                .post("/api/v1/teams/{teamId}/applications/{appId}/builds",
                        testTeam.id, 999999L)
                .then()
                .statusCode(404);
    }

    // --- GET list builds ---

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "buildres-team"),
            @Claim(key = "role", value = "member")
    })
    void listBuildsReturns200() {
        stubCredentials();
        when(tektonAdapter.listBuilds(
                eq("buildres-payments"),
                eq("buildres-team-payments-build"),
                eq("https://api.buildres-dev.example.com:6443"),
                eq("test-token")))
                .thenReturn(List.of(
                        new BuildSummaryDto("run-1", "Building", Instant.now(), null, "0s", null,
                                "buildres-payments", "https://tekton.example.com/#/pipelineruns/run-1"),
                        new BuildSummaryDto("run-2", "Passed",
                                Instant.now().minusSeconds(600), Instant.now().minusSeconds(300), "5m 0s", null,
                                "buildres-payments", null)));

        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications/{appId}/builds",
                        testTeam.id, testApp.id)
                .then()
                .statusCode(200)
                .body("$.size()", equalTo(2))
                .body("[0].buildId", equalTo("run-1"))
                .body("[0].status", equalTo("Building"))
                .body("[1].buildId", equalTo("run-2"))
                .body("[1].status", equalTo("Passed"))
                .body("[1].completedAt", notNullValue())
                .body("[1].duration", equalTo("5m 0s"));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "buildres-team"),
            @Claim(key = "role", value = "member")
    })
    void listBuildsReturns404ForNonExistentApp() {
        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications/{appId}/builds",
                        testTeam.id, 999999L)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "buildres-team"),
            @Claim(key = "role", value = "member")
    })
    void listBuildsReturns404ForCrossTeamApp() {
        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications/{appId}/builds",
                        testTeam.id, crossTeamApp.id)
                .then()
                .statusCode(404);
    }

    // --- GET build detail ---

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "buildres-team"),
            @Claim(key = "role", value = "member")
    })
    void getBuildDetailReturns200() {
        stubCredentials();
        when(tektonAdapter.getBuildDetail(
                eq("buildres-payments-xk7f2"),
                eq("buildres-team-payments-build"),
                eq("https://api.buildres-dev.example.com:6443"),
                eq("test-token")))
                .thenReturn(new BuildDetailDto(
                        "buildres-payments-xk7f2", "Passed",
                        Instant.now().minusSeconds(600), Instant.now().minusSeconds(300), "5m 0s",
                        "buildres-payments",
                        "registry.example.com/team/app:sha123",
                        "abc1234def567", null, null, null,
                        "https://tekton.example.com/#/pipelineruns/buildres-payments-xk7f2"));

        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications/{appId}/builds/{buildId}",
                        testTeam.id, testApp.id, "buildres-payments-xk7f2")
                .then()
                .statusCode(200)
                .body("buildId", equalTo("buildres-payments-xk7f2"))
                .body("status", equalTo("Passed"))
                .body("imageReference", equalTo("registry.example.com/team/app:sha123"))
                .body("startedAt", notNullValue())
                .body("completedAt", notNullValue())
                .body("duration", equalTo("5m 0s"))
                .body("tektonDeepLink", notNullValue());
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "buildres-team"),
            @Claim(key = "role", value = "member")
    })
    void getBuildDetailShowsFailureInfo() {
        stubCredentials();
        when(tektonAdapter.getBuildDetail(
                eq("buildres-payments-fail1"),
                eq("buildres-team-payments-build"),
                eq("https://api.buildres-dev.example.com:6443"),
                eq("test-token")))
                .thenReturn(new BuildDetailDto(
                        "buildres-payments-fail1", "Failed",
                        Instant.now().minusSeconds(600), Instant.now().minusSeconds(300), "5m 0s",
                        "buildres-payments", null, null,
                        "Run Tests", "Test failure in SomeTest.testMethod", null,
                        "https://tekton.example.com/#/pipelineruns/buildres-payments-fail1"));

        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications/{appId}/builds/{buildId}",
                        testTeam.id, testApp.id, "buildres-payments-fail1")
                .then()
                .statusCode(200)
                .body("status", equalTo("Failed"))
                .body("failedStageName", equalTo("Run Tests"))
                .body("errorSummary", equalTo("Test failure in SomeTest.testMethod"))
                .body("imageReference", nullValue());
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "buildres-team"),
            @Claim(key = "role", value = "member")
    })
    void getBuildDetailReturns404ForOtherTeamApp() {
        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications/{appId}/builds/{buildId}",
                        testTeam.id, crossTeamApp.id, "buildres-other-xyz12")
                .then()
                .statusCode(404);
    }

    // --- GET build logs ---

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "buildres-team"),
            @Claim(key = "role", value = "member")
    })
    void getBuildLogsReturns200WithTextPlain() {
        stubCredentials();
        when(tektonAdapter.getBuildDetail(
                eq("buildres-payments-logs1"),
                eq("buildres-team-payments-build"),
                eq("https://api.buildres-dev.example.com:6443"),
                eq("test-token")))
                .thenReturn(new BuildDetailDto("buildres-payments-logs1", "Passed",
                        Instant.now(), null, null, "buildres-payments",
                        null, null, null, null, null, null));
        when(tektonAdapter.getBuildLogs(
                eq("buildres-payments-logs1"),
                eq("buildres-team-payments-build"),
                eq("https://api.buildres-dev.example.com:6443"),
                eq("test-token")))
                .thenReturn("=== Run Tests / run ===\nAll 42 tests passed.\n");

        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications/{appId}/builds/{buildId}/logs",
                        testTeam.id, testApp.id, "buildres-payments-logs1")
                .then()
                .statusCode(200)
                .contentType("text/plain")
                .body(containsString("All 42 tests passed"));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "buildres-team"),
            @Claim(key = "role", value = "member")
    })
    void getBuildLogsReturns404ForOtherTeamApp() {
        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications/{appId}/builds/{buildId}/logs",
                        testTeam.id, crossTeamApp.id, "buildres-other-xyz12")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "buildres-team"),
            @Claim(key = "role", value = "member")
    })
    void getBuildLogsReturns404ForNonExistentApp() {
        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications/{appId}/builds/{buildId}/logs",
                        testTeam.id, 999999L, "buildres-payments-logs1")
                .then()
                .statusCode(404);
    }
}
