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
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DoraServiceTest {

    @Inject
    DoraService doraService;

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
            t.name = "dora-svc-team";
            t.oidcGroupId = "dora-svc-team";
            t.persist();
            t.flush();
            return t;
        });

        otherTeam = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "dora-svc-other";
            t.oidcGroupId = "dora-svc-other";
            t.persist();
            t.flush();
            return t;
        });

        Cluster cluster = QuarkusTransaction.requiringNew().call(() -> {
            Cluster c = new Cluster();
            c.name = "dora-svc-ocp-dev";
            c.apiServerUrl = "https://api.dora-svc-dev.example.com:6443";
            c.persist();
            c.flush();
            return c;
        });

        app = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "dora-svc-payments";
            a.teamId = team.id;
            a.gitRepoUrl = "https://github.com/org/payments.git";
            a.runtimeType = "quarkus";
            a.persist();
            a.flush();
            return a;
        });

        crossTeamApp = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "dora-svc-other-app";
            a.teamId = otherTeam.id;
            a.gitRepoUrl = "https://github.com/org/other.git";
            a.runtimeType = "spring-boot";
            a.persist();
            a.flush();
            return a;
        });
    }

    private DoraMetricsResult mockDoraResult() {
        List<TimeSeriesPoint> series = new java.util.ArrayList<>();
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
    void successfulDoraMetricsRetrievalAndDtoMapping() {
        when(prometheusAdapter.getDoraMetrics(eq("dora-svc-payments"), eq("30d")))
                .thenReturn(mockDoraResult());

        DoraMetricsDto dto = doraService.getDoraMetrics(team.id, app.id, null);

        assertTrue(dto.hasData());
        assertEquals("30d", dto.timeRange());
        assertEquals(4, dto.metrics().size());

        DoraMetricDto deployFreq = dto.metrics().get(0);
        assertEquals(DoraMetricType.DEPLOYMENT_FREQUENCY, deployFreq.type());
        assertEquals(4.2, deployFreq.currentValue(), 0.001);
        assertEquals(3.6, deployFreq.previousValue(), 0.001);
        assertEquals(TrendDirection.IMPROVING, deployFreq.trend());
        assertEquals("/wk", deployFreq.unit());
        assertFalse(deployFreq.timeSeries().isEmpty());
    }

    @Test
    void defaultTimeRangeAppliedWhenNull() {
        when(prometheusAdapter.getDoraMetrics(eq("dora-svc-payments"), eq("30d")))
                .thenReturn(mockDoraResult());

        DoraMetricsDto dto = doraService.getDoraMetrics(team.id, app.id, null);

        assertEquals("30d", dto.timeRange());
    }

    @Test
    void customTimeRangePassedThrough() {
        when(prometheusAdapter.getDoraMetrics(eq("dora-svc-payments"), eq("90d")))
                .thenReturn(mockDoraResult());

        DoraMetricsDto dto = doraService.getDoraMetrics(team.id, app.id, "90d");

        assertEquals("90d", dto.timeRange());
    }

    @Test
    void unitFormattingHoursForLeadTime() {
        when(prometheusAdapter.getDoraMetrics(eq("dora-svc-payments"), eq("30d")))
                .thenReturn(mockDoraResult());

        DoraMetricsDto dto = doraService.getDoraMetrics(team.id, app.id, null);

        DoraMetricDto leadTime = dto.metrics().get(1);
        assertEquals("h", leadTime.unit());
    }

    @Test
    void unitFormattingMinutesForMttr() {
        when(prometheusAdapter.getDoraMetrics(eq("dora-svc-payments"), eq("30d")))
                .thenReturn(mockDoraResult());

        DoraMetricsDto dto = doraService.getDoraMetrics(team.id, app.id, null);

        DoraMetricDto mttr = dto.metrics().get(3);
        assertEquals("m", mttr.unit());
    }

    @Test
    void percentageChangeCalculated() {
        when(prometheusAdapter.getDoraMetrics(eq("dora-svc-payments"), eq("30d")))
                .thenReturn(mockDoraResult());

        DoraMetricsDto dto = doraService.getDoraMetrics(team.id, app.id, null);

        DoraMetricDto deployFreq = dto.metrics().get(0);
        double expected = ((4.2 - 3.6) / 3.6) * 100;
        assertEquals(expected, deployFreq.percentageChange(), 0.1);
    }

    @Test
    void wrongTeamReturns404() {
        assertThrows(NotFoundException.class,
                () -> doraService.getDoraMetrics(team.id, crossTeamApp.id, null));
    }

    @Test
    void nonExistentApplicationReturns404() {
        assertThrows(NotFoundException.class,
                () -> doraService.getDoraMetrics(team.id, 999999L, null));
    }
}
