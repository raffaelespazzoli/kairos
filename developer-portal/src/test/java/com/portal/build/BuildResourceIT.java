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

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "buildres-team"),
            @Claim(key = "role", value = "member")
    })
    void triggerBuildReturns201() {
        when(credentialProvider.getCredentials(anyString(), anyString()))
                .thenReturn(ClusterCredential.of("test-token", 3600));
        when(tektonAdapter.triggerBuild(
                eq("buildres-payments"),
                eq("buildres-team-payments-build"),
                eq("https://api.buildres-dev.example.com:6443"),
                eq("test-token")))
                .thenReturn(new BuildSummaryDto(
                        "buildres-payments-abc12", "Building", Instant.now(),
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
        when(credentialProvider.getCredentials(anyString(), anyString()))
                .thenReturn(ClusterCredential.of("test-token", 3600));
        when(tektonAdapter.triggerBuild(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new BuildSummaryDto(
                        "run-123", "Building", Instant.now(), "buildres-payments", null));

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
}
