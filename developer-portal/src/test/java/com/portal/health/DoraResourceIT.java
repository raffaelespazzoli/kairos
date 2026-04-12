package com.portal.health;

import com.portal.application.Application;
import com.portal.cluster.Cluster;
import com.portal.integration.prometheus.PrometheusAdapter;
import com.portal.integration.prometheus.model.DoraMetric;
import com.portal.integration.prometheus.model.DoraMetricType;
import com.portal.integration.prometheus.model.DoraMetricsResult;
import com.portal.integration.prometheus.model.TimeSeriesPoint;
import com.portal.integration.prometheus.model.TrendDirection;
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

import java.util.ArrayList;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DoraResourceIT {

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
            t.name = "dorares-team";
            t.oidcGroupId = "dorares-team";
            t.persist();
            t.flush();
            return t;
        });

        otherTeam = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "dorares-other";
            t.oidcGroupId = "dorares-other";
            t.persist();
            t.flush();
            return t;
        });

        Cluster cluster = QuarkusTransaction.requiringNew().call(() -> {
            Cluster c = new Cluster();
            c.name = "dorares-ocp-dev";
            c.apiServerUrl = "https://api.dorares-dev.example.com:6443";
            c.persist();
            c.flush();
            return c;
        });

        testApp = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "dorares-payments";
            a.teamId = testTeam.id;
            a.gitRepoUrl = "https://github.com/org/payments.git";
            a.runtimeType = "quarkus";
            a.persist();
            a.flush();
            return a;
        });

        crossTeamApp = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "dorares-other-app";
            a.teamId = otherTeam.id;
            a.gitRepoUrl = "https://github.com/org/other.git";
            a.runtimeType = "spring-boot";
            a.persist();
            a.flush();
            return a;
        });
    }

    private DoraMetricsResult mockDoraResult() {
        List<TimeSeriesPoint> series = new ArrayList<>();
        long base = 1712345678L;
        for (int i = 0; i < 30; i++) {
            series.add(new TimeSeriesPoint(base + i * 86400L, 4.0 + i * 0.01));
        }
        return new DoraMetricsResult(List.of(
                new DoraMetric(DoraMetricType.DEPLOYMENT_FREQUENCY, 4.2, 3.6,
                        TrendDirection.IMPROVING, "/wk", series),
                new DoraMetric(DoraMetricType.LEAD_TIME, 2.1, 2.8,
                        TrendDirection.IMPROVING, "h", series),
                new DoraMetric(DoraMetricType.CHANGE_FAILURE_RATE, 2.3, 3.1,
                        TrendDirection.IMPROVING, "%", series),
                new DoraMetric(DoraMetricType.MTTR, 45.0, 48.0,
                        TrendDirection.STABLE, "m", series)
        ), true);
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "dorares-team"),
            @Claim(key = "role", value = "member")
    })
    void getDoraReturns200WithExpectedStructure() {
        when(prometheusAdapter.getDoraMetrics(eq("dorares-payments"), anyString()))
                .thenReturn(mockDoraResult());

        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications/{appId}/dora",
                        testTeam.id, testApp.id)
                .then()
                .statusCode(200)
                .body("metrics.size()", equalTo(4))
                .body("timeRange", equalTo("30d"))
                .body("hasData", equalTo(true))
                .body("metrics[0].type", equalTo("DEPLOYMENT_FREQUENCY"))
                .body("metrics[0].currentValue", equalTo(4.2f))
                .body("metrics[0].trend", equalTo("IMPROVING"))
                .body("metrics[0].unit", equalTo("/wk"))
                .body("metrics[0].timeSeries.size()", equalTo(30));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "dorares-team"),
            @Claim(key = "role", value = "member")
    })
    void getDoraWithTimeRangeParameter() {
        when(prometheusAdapter.getDoraMetrics(eq("dorares-payments"), eq("90d")))
                .thenReturn(mockDoraResult());

        given()
                .queryParam("timeRange", "90d")
                .when()
                .get("/api/v1/teams/{teamId}/applications/{appId}/dora",
                        testTeam.id, testApp.id)
                .then()
                .statusCode(200)
                .body("timeRange", equalTo("90d"));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "dorares-team"),
            @Claim(key = "role", value = "member")
    })
    void crossTeamAccessReturns404() {
        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications/{appId}/dora",
                        testTeam.id, crossTeamApp.id)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "dorares-team"),
            @Claim(key = "role", value = "member")
    })
    void nonExistentApplicationReturns404() {
        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications/{appId}/dora",
                        testTeam.id, 999999L)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "lead@example.com", roles = "lead")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "dorares-team"),
            @Claim(key = "role", value = "lead")
    })
    void leadRoleCanAccessDora() {
        when(prometheusAdapter.getDoraMetrics(eq("dorares-payments"), anyString()))
                .thenReturn(mockDoraResult());

        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications/{appId}/dora",
                        testTeam.id, testApp.id)
                .then()
                .statusCode(200)
                .body("metrics.size()", equalTo(4));
    }

    @Test
    @TestSecurity(user = "admin@example.com", roles = "admin")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "dorares-team"),
            @Claim(key = "role", value = "admin")
    })
    void adminRoleCanAccessDora() {
        when(prometheusAdapter.getDoraMetrics(eq("dorares-payments"), anyString()))
                .thenReturn(mockDoraResult());

        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications/{appId}/dora",
                        testTeam.id, testApp.id)
                .then()
                .statusCode(200)
                .body("metrics.size()", equalTo(4));
    }
}
