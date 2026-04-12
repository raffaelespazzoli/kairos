package com.portal.health;

import com.portal.application.Application;
import com.portal.cluster.Cluster;
import com.portal.environment.Environment;
import com.portal.integration.PortalIntegrationException;
import com.portal.integration.prometheus.PrometheusAdapter;
import com.portal.integration.prometheus.model.GoldenSignal;
import com.portal.integration.prometheus.model.GoldenSignalType;
import com.portal.integration.prometheus.model.HealthSignalsResult;
import com.portal.team.Team;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HealthServiceTest {

    @Inject
    HealthService healthService;

    @InjectMock
    PrometheusAdapter prometheusAdapter;

    private Team team;
    private Team otherTeam;
    private Application app;
    private Application crossTeamApp;

    @BeforeAll
    void setUpData() {
        team = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "health-svc-team";
            t.oidcGroupId = "health-svc-team";
            t.persist();
            t.flush();
            return t;
        });

        otherTeam = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "health-svc-other";
            t.oidcGroupId = "health-svc-other";
            t.persist();
            t.flush();
            return t;
        });

        Cluster cluster = QuarkusTransaction.requiringNew().call(() -> {
            Cluster c = new Cluster();
            c.name = "health-svc-ocp-dev";
            c.apiServerUrl = "https://api.health-svc-dev.example.com:6443";
            c.persist();
            c.flush();
            return c;
        });

        app = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "health-svc-payments";
            a.teamId = team.id;
            a.gitRepoUrl = "https://github.com/org/payments.git";
            a.runtimeType = "quarkus";
            a.persist();
            a.flush();
            return a;
        });

        crossTeamApp = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "health-svc-other-app";
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
            dev.applicationId = app.id;
            dev.clusterId = cluster.id;
            dev.namespace = "health-payments-dev";
            dev.promotionOrder = 0;
            dev.persist();

            Environment staging = new Environment();
            staging.name = "staging";
            staging.applicationId = app.id;
            staging.clusterId = cluster.id;
            staging.namespace = "health-payments-staging";
            staging.promotionOrder = 1;
            staging.persist();

            Environment prod = new Environment();
            prod.name = "prod";
            prod.applicationId = app.id;
            prod.clusterId = cluster.id;
            prod.namespace = "health-payments-prod";
            prod.promotionOrder = 2;
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

    private HealthSignalsResult unhealthySignals() {
        return new HealthSignalsResult(List.of(
                new GoldenSignal("Latency P50", 120.0, "ms", GoldenSignalType.LATENCY_P50),
                new GoldenSignal("Latency P95", 800.0, "ms", GoldenSignalType.LATENCY_P95),
                new GoldenSignal("Latency P99", 2500.0, "ms", GoldenSignalType.LATENCY_P99),
                new GoldenSignal("Traffic Rate", 10.0, "req/s", GoldenSignalType.TRAFFIC_RATE),
                new GoldenSignal("Error Rate", 12.5, "%", GoldenSignalType.ERROR_RATE),
                new GoldenSignal("CPU Saturation", 55.0, "%", GoldenSignalType.SATURATION_CPU),
                new GoldenSignal("Memory Saturation", 70.0, "%", GoldenSignalType.SATURATION_MEMORY)
        ), true);
    }

    private HealthSignalsResult degradedSignals() {
        return new HealthSignalsResult(List.of(
                new GoldenSignal("Latency P50", 80.0, "ms", GoldenSignalType.LATENCY_P50),
                new GoldenSignal("Latency P95", 400.0, "ms", GoldenSignalType.LATENCY_P95),
                new GoldenSignal("Latency P99", 1200.0, "ms", GoldenSignalType.LATENCY_P99),
                new GoldenSignal("Traffic Rate", 30.0, "req/s", GoldenSignalType.TRAFFIC_RATE),
                new GoldenSignal("Error Rate", 1.0, "%", GoldenSignalType.ERROR_RATE),
                new GoldenSignal("CPU Saturation", 92.0, "%", GoldenSignalType.SATURATION_CPU),
                new GoldenSignal("Memory Saturation", 65.0, "%", GoldenSignalType.SATURATION_MEMORY)
        ), true);
    }

    private HealthSignalsResult noDataSignals() {
        return new HealthSignalsResult(List.of(
                new GoldenSignal("Latency P50", 0.0, "ms", GoldenSignalType.LATENCY_P50),
                new GoldenSignal("Latency P95", 0.0, "ms", GoldenSignalType.LATENCY_P95),
                new GoldenSignal("Latency P99", 0.0, "ms", GoldenSignalType.LATENCY_P99),
                new GoldenSignal("Traffic Rate", 0.0, "req/s", GoldenSignalType.TRAFFIC_RATE),
                new GoldenSignal("Error Rate", 0.0, "%", GoldenSignalType.ERROR_RATE),
                new GoldenSignal("CPU Saturation", 0.0, "%", GoldenSignalType.SATURATION_CPU),
                new GoldenSignal("Memory Saturation", 0.0, "%", GoldenSignalType.SATURATION_MEMORY)
        ), false);
    }

    @Test
    void healthySignalsDerivesHealthyStatus() {
        when(prometheusAdapter.getGoldenSignals(anyString())).thenReturn(healthySignals());

        HealthResponse response = healthService.getApplicationHealth(team.id, app.id);

        assertEquals(3, response.environments().size());
        for (EnvironmentHealthDto env : response.environments()) {
            assertNotNull(env.healthStatus());
            assertEquals(HealthStatus.HEALTHY, env.healthStatus().status());
            assertNull(env.error());
        }
    }

    @Test
    void highErrorRateDerivesUnhealthyStatus() {
        when(prometheusAdapter.getGoldenSignals(anyString())).thenReturn(unhealthySignals());

        HealthResponse response = healthService.getApplicationHealth(team.id, app.id);

        for (EnvironmentHealthDto env : response.environments()) {
            assertEquals(HealthStatus.UNHEALTHY, env.healthStatus().status());
        }
    }

    @Test
    void highSaturationDerivesDegradedStatus() {
        when(prometheusAdapter.getGoldenSignals(anyString())).thenReturn(degradedSignals());

        HealthResponse response = healthService.getApplicationHealth(team.id, app.id);

        for (EnvironmentHealthDto env : response.environments()) {
            assertEquals(HealthStatus.DEGRADED, env.healthStatus().status());
        }
    }

    @Test
    void noDataDerivesNoDataStatus() {
        when(prometheusAdapter.getGoldenSignals(anyString())).thenReturn(noDataSignals());

        HealthResponse response = healthService.getApplicationHealth(team.id, app.id);

        for (EnvironmentHealthDto env : response.environments()) {
            assertEquals(HealthStatus.NO_DATA, env.healthStatus().status());
        }
    }

    @Test
    void parallelQueriesReturnCorrectPerEnvironmentResults() {
        when(prometheusAdapter.getGoldenSignals(eq("health-payments-dev")))
                .thenReturn(healthySignals());
        when(prometheusAdapter.getGoldenSignals(eq("health-payments-staging")))
                .thenReturn(degradedSignals());
        when(prometheusAdapter.getGoldenSignals(eq("health-payments-prod")))
                .thenReturn(noDataSignals());

        HealthResponse response = healthService.getApplicationHealth(team.id, app.id);

        assertEquals(3, response.environments().size());

        EnvironmentHealthDto dev = response.environments().get(0);
        assertEquals("dev", dev.environmentName());
        assertEquals(HealthStatus.HEALTHY, dev.healthStatus().status());
        assertEquals("health-payments-dev", dev.healthStatus().namespace());

        EnvironmentHealthDto staging = response.environments().get(1);
        assertEquals("staging", staging.environmentName());
        assertEquals(HealthStatus.DEGRADED, staging.healthStatus().status());

        EnvironmentHealthDto prod = response.environments().get(2);
        assertEquals("prod", prod.environmentName());
        assertEquals(HealthStatus.NO_DATA, prod.healthStatus().status());
    }

    @Test
    void perEnvironmentFailureIsolation() {
        when(prometheusAdapter.getGoldenSignals(eq("health-payments-dev")))
                .thenReturn(healthySignals());
        when(prometheusAdapter.getGoldenSignals(eq("health-payments-staging")))
                .thenThrow(new PortalIntegrationException("prometheus", "getGoldenSignals",
                        "Health data unavailable — metrics system is unreachable"));
        when(prometheusAdapter.getGoldenSignals(eq("health-payments-prod")))
                .thenReturn(healthySignals());

        HealthResponse response = healthService.getApplicationHealth(team.id, app.id);

        assertEquals(3, response.environments().size());

        EnvironmentHealthDto dev = response.environments().get(0);
        assertNotNull(dev.healthStatus());
        assertEquals(HealthStatus.HEALTHY, dev.healthStatus().status());
        assertNull(dev.error());

        EnvironmentHealthDto staging = response.environments().get(1);
        assertNull(staging.healthStatus());
        assertNotNull(staging.error());
        assertTrue(staging.error().contains("unreachable"));

        EnvironmentHealthDto prod = response.environments().get(2);
        assertNotNull(prod.healthStatus());
        assertEquals(HealthStatus.HEALTHY, prod.healthStatus().status());
        assertNull(prod.error());
    }

    @Test
    void grafanaDeepLinksIncluded() {
        when(prometheusAdapter.getGoldenSignals(anyString())).thenReturn(healthySignals());

        HealthResponse response = healthService.getApplicationHealth(team.id, app.id);

        for (EnvironmentHealthDto env : response.environments()) {
            assertNotNull(env.grafanaDeepLink());
            assertTrue(env.grafanaDeepLink().contains("grafana"));
            assertTrue(env.grafanaDeepLink().contains("var-namespace="));
        }
    }

    @Test
    void wrongTeamReturns404() {
        assertThrows(NotFoundException.class,
                () -> healthService.getApplicationHealth(team.id, crossTeamApp.id));
    }

    @Test
    void nonExistentApplicationReturns404() {
        assertThrows(NotFoundException.class,
                () -> healthService.getApplicationHealth(team.id, 999999L));
    }
}
