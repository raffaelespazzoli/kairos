package com.portal.release;

import com.portal.application.Application;
import com.portal.build.BuildDetailDto;
import com.portal.cluster.Cluster;
import com.portal.integration.git.GitProvider;
import com.portal.integration.registry.RegistryAdapter;
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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReleaseResourceIT {

    @InjectMock
    TektonAdapter tektonAdapter;

    @InjectMock
    SecretManagerCredentialProvider credentialProvider;

    @InjectMock
    GitProvider gitProvider;

    @InjectMock
    RegistryAdapter registryAdapter;

    private Team testTeam;
    private Team otherTeam;
    private Application testApp;
    private Application crossTeamApp;
    private Cluster buildCluster;

    @BeforeAll
    void setUpData() {
        testTeam = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "relres-team";
            t.oidcGroupId = "relres-team";
            t.persist();
            t.flush();
            return t;
        });

        otherTeam = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "relres-other";
            t.oidcGroupId = "relres-other";
            t.persist();
            t.flush();
            return t;
        });

        buildCluster = QuarkusTransaction.requiringNew().call(() -> {
            Cluster c = new Cluster();
            c.name = "relres-ocp-dev";
            c.apiServerUrl = "https://api.relres-dev.example.com:6443";
            c.persist();
            c.flush();
            return c;
        });

        testApp = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "relres-payments";
            a.teamId = testTeam.id;
            a.gitRepoUrl = "https://github.com/org/payments.git";
            a.runtimeType = "quarkus";
            a.buildClusterId = buildCluster.id;
            a.buildNamespace = "relres-team-payments-build";
            a.persist();
            a.flush();
            return a;
        });

        crossTeamApp = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "relres-other-app";
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

    private void stubPassedBuild(String buildId) {
        when(credentialProvider.getCredentials(anyString(), anyString()))
                .thenReturn(ClusterCredential.of("test-token", 3600));
        when(tektonAdapter.getBuildDetail(
                eq(buildId), anyString(), anyString(), anyString()))
                .thenReturn(new BuildDetailDto(
                        buildId, "Passed",
                        Instant.now().minusSeconds(600), Instant.now().minusSeconds(300),
                        "5m 0s", "relres-payments",
                        "registry.example.com/team/app:abc1234",
                        "abc1234def567", null, null, null, null));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "relres-team"),
            @Claim(key = "role", value = "member")
    })
    void createReleaseReturns201() {
        stubPassedBuild("relres-payments-run1");

        given()
                .contentType("application/json")
                .body("{\"buildId\":\"relres-payments-run1\",\"version\":\"v1.4.2\"}")
                .when()
                .post("/api/v1/teams/{teamId}/applications/{appId}/releases",
                        testTeam.id, testApp.id)
                .then()
                .statusCode(201)
                .body("version", equalTo("v1.4.2"))
                .body("buildId", equalTo("relres-payments-run1"))
                .body("commitSha", equalTo("abc1234def567"))
                .body("imageReference", equalTo("registry.example.com/team/app:abc1234"))
                .body("createdAt", notNullValue());
    }

    @Test
    @TestSecurity(user = "lead@example.com", roles = "lead")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "relres-team"),
            @Claim(key = "role", value = "lead")
    })
    void leadCanCreateRelease() {
        stubPassedBuild("relres-payments-lead1");

        given()
                .contentType("application/json")
                .body("{\"buildId\":\"relres-payments-lead1\",\"version\":\"v2.0.0\"}")
                .when()
                .post("/api/v1/teams/{teamId}/applications/{appId}/releases",
                        testTeam.id, testApp.id)
                .then()
                .statusCode(201)
                .body("version", equalTo("v2.0.0"));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "relres-team"),
            @Claim(key = "role", value = "member")
    })
    void createReleaseReturns404ForCrossTeamApp() {
        given()
                .contentType("application/json")
                .body("{\"buildId\":\"run-1\",\"version\":\"v1.0.0\"}")
                .when()
                .post("/api/v1/teams/{teamId}/applications/{appId}/releases",
                        testTeam.id, crossTeamApp.id)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "relres-team"),
            @Claim(key = "role", value = "member")
    })
    void createReleaseReturns404ForNonExistentApp() {
        given()
                .contentType("application/json")
                .body("{\"buildId\":\"run-1\",\"version\":\"v1.0.0\"}")
                .when()
                .post("/api/v1/teams/{teamId}/applications/{appId}/releases",
                        testTeam.id, 999999L)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "relres-team"),
            @Claim(key = "role", value = "member")
    })
    void createReleaseReturns400ForFailedBuild() {
        when(credentialProvider.getCredentials(anyString(), anyString()))
                .thenReturn(ClusterCredential.of("test-token", 3600));
        when(tektonAdapter.getBuildDetail(
                eq("relres-payments-failed1"), anyString(), anyString(), anyString()))
                .thenReturn(new BuildDetailDto(
                        "relres-payments-failed1", "Failed",
                        Instant.now(), null, null, "relres-payments",
                        null, null, "Run Tests", "Test failed", null, null));

        given()
                .contentType("application/json")
                .body("{\"buildId\":\"relres-payments-failed1\",\"version\":\"v1.0.0\"}")
                .when()
                .post("/api/v1/teams/{teamId}/applications/{appId}/releases",
                        testTeam.id, testApp.id)
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "relres-team"),
            @Claim(key = "role", value = "member")
    })
    void createReleaseReturns400ForBuildWithNoImage() {
        when(credentialProvider.getCredentials(anyString(), anyString()))
                .thenReturn(ClusterCredential.of("test-token", 3600));
        when(tektonAdapter.getBuildDetail(
                eq("relres-no-image"), anyString(), anyString(), anyString()))
                .thenReturn(new BuildDetailDto(
                        "relres-no-image", "Passed",
                        Instant.now().minusSeconds(600), Instant.now().minusSeconds(300),
                        "5m 0s", "relres-payments",
                        null, "abc1234def567", null, null, null, null));

        given()
                .contentType("application/json")
                .body("{\"buildId\":\"relres-no-image\",\"version\":\"v1.0.0\"}")
                .when()
                .post("/api/v1/teams/{teamId}/applications/{appId}/releases",
                        testTeam.id, testApp.id)
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "relres-team"),
            @Claim(key = "role", value = "member")
    })
    void idorAttemptWithOtherTeamIdReturns404() {
        given()
                .contentType("application/json")
                .body("{\"buildId\":\"run-1\",\"version\":\"v1.0.0\"}")
                .when()
                .post("/api/v1/teams/{teamId}/applications/{appId}/releases",
                        otherTeam.id, crossTeamApp.id)
                .then()
                .statusCode(404);
    }
}
