package com.portal.health;

import com.portal.application.Application;
import com.portal.cluster.Cluster;
import com.portal.environment.Environment;
import com.portal.integration.prometheus.PrometheusAdapter;
import com.portal.integration.prometheus.model.GoldenSignal;
import com.portal.integration.prometheus.model.GoldenSignalType;
import com.portal.integration.prometheus.model.HealthSignalsResult;
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

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HealthResourceIT {

    @InjectMock
    PrometheusAdapter prometheusAdapter;

    private Team testTeam;
    private Team otherTeam;
    private Application testApp;
    private Application crossTeamApp;

    @BeforeAll
    void setUpData() {
        testTeam = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "healthres-team";
            t.oidcGroupId = "healthres-team";
            t.persist();
            t.flush();
            return t;
        });

        otherTeam = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "healthres-other";
            t.oidcGroupId = "healthres-other";
            t.persist();
            t.flush();
            return t;
        });

        Cluster cluster = QuarkusTransaction.requiringNew().call(() -> {
            Cluster c = new Cluster();
            c.name = "healthres-ocp-dev";
            c.apiServerUrl = "https://api.healthres-dev.example.com:6443";
            c.persist();
            c.flush();
            return c;
        });

        testApp = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "healthres-payments";
            a.teamId = testTeam.id;
            a.gitRepoUrl = "https://github.com/org/payments.git";
            a.runtimeType = "quarkus";
            a.persist();
            a.flush();
            return a;
        });

        crossTeamApp = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "healthres-other-app";
            a.teamId = otherTeam.id;
            a.gitRepoUrl = "https://github.com/org/other.git";
            a.runtimeType = "spring-boot";
            a.persist();
            a.flush();
            return a;
        });

        QuarkusTransaction.requiringNew().run(() -> {
            Environment dev = new Environment();
            dev.name = "dev";
            dev.applicationId = testApp.id;
            dev.clusterId = cluster.id;
            dev.namespace = "healthres-payments-dev";
            dev.promotionOrder = 0;
            dev.persist();

            Environment prod = new Environment();
            prod.name = "prod";
            prod.applicationId = testApp.id;
            prod.clusterId = cluster.id;
            prod.namespace = "healthres-payments-prod";
            prod.promotionOrder = 1;
            prod.persist();
        });
    }

    private HealthSignalsResult healthySignals() {
        return new HealthSignalsResult(List.of(
                new GoldenSignal("Latency P50", 45.0, "ms", GoldenSignalType.LATENCY_P50),
                new GoldenSignal("Latency P95", 245.0, "ms", GoldenSignalType.LATENCY_P95),
                new GoldenSignal("Latency P99", 890.0, "ms", GoldenSignalType.LATENCY_P99),
                new GoldenSignal("Traffic Rate", 42.5, "req/s", GoldenSignalType.TRAFFIC_RATE),
                new GoldenSignal("Error Rate", 0.3, "%", GoldenSignalType.ERROR_RATE),
                new GoldenSignal("CPU Saturation", 45.0, "%", GoldenSignalType.SATURATION_CPU),
                new GoldenSignal("Memory Saturation", 62.0, "%", GoldenSignalType.SATURATION_MEMORY)
        ), true);
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "healthres-team"),
            @Claim(key = "role", value = "member")
    })
    void getHealthReturns200WithExpectedStructure() {
        when(prometheusAdapter.getGoldenSignals(anyString())).thenReturn(healthySignals());

        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications/{appId}/health",
                        testTeam.id, testApp.id)
                .then()
                .statusCode(200)
                .body("environments.size()", equalTo(2))
                .body("environments[0].environmentName", equalTo("dev"))
                .body("environments[0].healthStatus.status", equalTo("HEALTHY"))
                .body("environments[0].healthStatus.goldenSignals.size()", equalTo(7))
                .body("environments[0].healthStatus.namespace", equalTo("healthres-payments-dev"))
                .body("environments[0].grafanaDeepLink", notNullValue())
                .body("environments[0].error", nullValue())
                .body("environments[1].environmentName", equalTo("prod"));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "healthres-team"),
            @Claim(key = "role", value = "member")
    })
    void crossTeamAccessReturns404() {
        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications/{appId}/health",
                        testTeam.id, crossTeamApp.id)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "healthres-team"),
            @Claim(key = "role", value = "member")
    })
    void nonExistentApplicationReturns404() {
        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications/{appId}/health",
                        testTeam.id, 999999L)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "lead@example.com", roles = "lead")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "healthres-team"),
            @Claim(key = "role", value = "lead")
    })
    void leadRoleCanAccessHealth() {
        when(prometheusAdapter.getGoldenSignals(anyString())).thenReturn(healthySignals());

        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications/{appId}/health",
                        testTeam.id, testApp.id)
                .then()
                .statusCode(200)
                .body("environments.size()", equalTo(2));
    }

    @Test
    @TestSecurity(user = "admin@example.com", roles = "admin")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "healthres-team"),
            @Claim(key = "role", value = "admin")
    })
    void adminRoleCanAccessHealth() {
        when(prometheusAdapter.getGoldenSignals(anyString())).thenReturn(healthySignals());

        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications/{appId}/health",
                        testTeam.id, testApp.id)
                .then()
                .statusCode(200)
                .body("environments.size()", equalTo(2));
    }
}
