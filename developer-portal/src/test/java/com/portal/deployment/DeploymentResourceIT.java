package com.portal.deployment;

import com.portal.application.Application;
import com.portal.cluster.Cluster;
import com.portal.environment.Environment;
import com.portal.integration.PortalIntegrationException;
import com.portal.integration.git.GitProvider;
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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeploymentResourceIT {

    private static final String SAMPLE_VALUES_YAML = """
            image:
              repository: registry.example.com/team/orders-api
              tag: v1.4.1
              pullPolicy: IfNotPresent
            replicaCount: 2
            """;

    @InjectMock
    GitProvider gitProvider;

    private Team testTeam;
    private Team otherTeam;
    private Application testApp;
    private Application crossTeamApp;
    private Environment devEnv;
    private Cluster cluster;

    @BeforeAll
    void setUpData() {
        testTeam = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "depres-team";
            t.oidcGroupId = "depres-team";
            t.persist();
            t.flush();
            return t;
        });

        otherTeam = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "depres-other";
            t.oidcGroupId = "depres-other";
            t.persist();
            t.flush();
            return t;
        });

        cluster = QuarkusTransaction.requiringNew().call(() -> {
            Cluster c = new Cluster();
            c.name = "depres-ocp-dev";
            c.apiServerUrl = "https://api.depres-dev.example.com:6443";
            c.persist();
            c.flush();
            return c;
        });

        testApp = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "depres-orders";
            a.teamId = testTeam.id;
            a.gitRepoUrl = "https://github.com/org/orders.git";
            a.runtimeType = "quarkus";
            a.buildClusterId = cluster.id;
            a.buildNamespace = "depres-team-orders-build";
            a.persist();
            a.flush();
            return a;
        });

        crossTeamApp = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "depres-other-app";
            a.teamId = otherTeam.id;
            a.gitRepoUrl = "https://github.com/org/other.git";
            a.runtimeType = "quarkus";
            a.buildClusterId = cluster.id;
            a.buildNamespace = "other-team-other-app-build";
            a.persist();
            a.flush();
            return a;
        });

        devEnv = QuarkusTransaction.requiringNew().call(() -> {
            Environment e = new Environment();
            e.name = "Dev";
            e.applicationId = testApp.id;
            e.clusterId = cluster.id;
            e.namespace = "depres-team-orders-dev";
            e.promotionOrder = 1;
            e.persist();
            e.flush();
            return e;
        });
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "depres-team"),
            @Claim(key = "role", value = "member")
    })
    void deployReturns201WithCorrectBody() {
        when(gitProvider.readFile(anyString(), anyString(), anyString()))
                .thenReturn(SAMPLE_VALUES_YAML);

        given()
                .contentType("application/json")
                .body("{\"releaseVersion\":\"v1.4.2\",\"environmentId\":" + devEnv.id + "}")
                .when()
                .post("/api/v1/teams/{teamId}/applications/{appId}/deployments",
                        testTeam.id, testApp.id)
                .then()
                .statusCode(201)
                .body("releaseVersion", equalTo("v1.4.2"))
                .body("environmentName", equalTo("Dev"))
                .body("status", equalTo("Deploying"))
                .body("deploymentId", notNullValue())
                .body("startedAt", notNullValue());
    }

    @Test
    @TestSecurity(user = "lead@example.com", roles = "lead")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "depres-team"),
            @Claim(key = "role", value = "lead")
    })
    void leadCanDeploy() {
        when(gitProvider.readFile(anyString(), anyString(), anyString()))
                .thenReturn(SAMPLE_VALUES_YAML);

        given()
                .contentType("application/json")
                .body("{\"releaseVersion\":\"v2.0.0\",\"environmentId\":" + devEnv.id + "}")
                .when()
                .post("/api/v1/teams/{teamId}/applications/{appId}/deployments",
                        testTeam.id, testApp.id)
                .then()
                .statusCode(201)
                .body("releaseVersion", equalTo("v2.0.0"));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "depres-team"),
            @Claim(key = "role", value = "member")
    })
    void deployReturns404ForCrossTeamApp() {
        given()
                .contentType("application/json")
                .body("{\"releaseVersion\":\"v1.0.0\",\"environmentId\":" + devEnv.id + "}")
                .when()
                .post("/api/v1/teams/{teamId}/applications/{appId}/deployments",
                        testTeam.id, crossTeamApp.id)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "depres-team"),
            @Claim(key = "role", value = "member")
    })
    void deployReturns404ForInvalidEnvironment() {
        given()
                .contentType("application/json")
                .body("{\"releaseVersion\":\"v1.0.0\",\"environmentId\":999999}")
                .when()
                .post("/api/v1/teams/{teamId}/applications/{appId}/deployments",
                        testTeam.id, testApp.id)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "depres-team"),
            @Claim(key = "role", value = "member")
    })
    void deployReturns502WhenGitProviderFails() {
        when(gitProvider.readFile(anyString(), anyString(), anyString()))
                .thenThrow(new PortalIntegrationException("git", "readFile",
                        "Git server returned HTTP 500"));

        given()
                .contentType("application/json")
                .body("{\"releaseVersion\":\"v1.0.0\",\"environmentId\":" + devEnv.id + "}")
                .when()
                .post("/api/v1/teams/{teamId}/applications/{appId}/deployments",
                        testTeam.id, testApp.id)
                .then()
                .statusCode(502)
                .body("system", equalTo("git"));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "depres-team"),
            @Claim(key = "role", value = "member")
    })
    void deployReturns502WhenGitCommitFails() {
        when(gitProvider.readFile(anyString(), anyString(), anyString()))
                .thenReturn(SAMPLE_VALUES_YAML);
        doThrow(new PortalIntegrationException("git", "commitFiles",
                "Git server returned HTTP 500"))
                .when(gitProvider).commitFiles(anyString(), anyString(), anyMap(), anyString());

        given()
                .contentType("application/json")
                .body("{\"releaseVersion\":\"v1.0.0\",\"environmentId\":" + devEnv.id + "}")
                .when()
                .post("/api/v1/teams/{teamId}/applications/{appId}/deployments",
                        testTeam.id, testApp.id)
                .then()
                .statusCode(502)
                .body("system", equalTo("git"));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "depres-other"),
            @Claim(key = "role", value = "member")
    })
    void idorAttemptWithOtherTeamIdReturns404() {
        given()
                .contentType("application/json")
                .body("{\"releaseVersion\":\"v1.0.0\",\"environmentId\":" + devEnv.id + "}")
                .when()
                .post("/api/v1/teams/{teamId}/applications/{appId}/deployments",
                        otherTeam.id, crossTeamApp.id)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "depres-team"),
            @Claim(key = "role", value = "member")
    })
    void deployReturns400WhenReleaseVersionIsBlank() {
        given()
                .contentType("application/json")
                .body("{\"releaseVersion\":\"\",\"environmentId\":" + devEnv.id + "}")
                .when()
                .post("/api/v1/teams/{teamId}/applications/{appId}/deployments",
                        testTeam.id, testApp.id)
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "depres-team"),
            @Claim(key = "role", value = "member")
    })
    void deployReturns400WhenEnvironmentIdIsNull() {
        given()
                .contentType("application/json")
                .body("{\"releaseVersion\":\"v1.0.0\"}")
                .when()
                .post("/api/v1/teams/{teamId}/applications/{appId}/deployments",
                        testTeam.id, testApp.id)
                .then()
                .statusCode(400);
    }

    @Test
    void unauthenticatedRequestReturns401() {
        given()
                .contentType("application/json")
                .body("{\"releaseVersion\":\"v1.0.0\",\"environmentId\":" + devEnv.id + "}")
                .when()
                .post("/api/v1/teams/{teamId}/applications/{appId}/deployments",
                        testTeam.id, testApp.id)
                .then()
                .statusCode(401);
    }
}
