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
import com.portal.integration.PortalIntegrationException;
import com.portal.integration.prometheus.model.DoraMetricType;
import com.portal.integration.prometheus.model.TrendDirection;
import com.portal.release.ReleaseService;
import com.portal.release.ReleaseSummaryDto;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@QuarkusTest
class DashboardServiceTest {

    @Inject
    DashboardService dashboardService;

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

    private Application appA;
    private Application appB;

    @BeforeEach
    void setUp() {
        appA = new Application();
        appA.id = 1L;
        appA.name = "app-alpha";
        appA.teamId = 100L;
        appA.runtimeType = "quarkus";
        appA.gitRepoUrl = "https://github.com/org/alpha.git";

        appB = new Application();
        appB.id = 2L;
        appB.name = "app-beta";
        appB.teamId = 100L;
        appB.runtimeType = "spring-boot";
        appB.gitRepoUrl = "https://github.com/org/beta.git";
    }

    // ── Health merge rule tests ─────────────────────────────────────────

    @Test
    void environmentsPreservePromotionOrder() {
        when(applicationService.getApplicationsForTeam(100L)).thenReturn(List.of(appA));

        List<EnvironmentChainEntryDto> chain = List.of(
                envEntry("dev", 0, "HEALTHY", "1.0.0"),
                envEntry("staging", 1, "HEALTHY", "0.9.0"),
                envEntry("prod", 2, "HEALTHY", "0.8.0"));
        when(environmentService.getEnvironmentChain(100L, 1L))
                .thenReturn(new EnvironmentChainResponse(chain, null));
        when(healthService.getApplicationHealth(100L, 1L))
                .thenReturn(healthResponse("dev", HealthStatus.HEALTHY,
                        "staging", HealthStatus.HEALTHY, "prod", HealthStatus.HEALTHY));
        stubEmptyDora();
        stubEmptyActivity();

        TeamDashboardDto result = dashboardService.getTeamDashboard(100L);

        assertEquals(1, result.applications().size());
        List<DashboardEnvironmentEntryDto> envs = result.applications().get(0).environments();
        assertEquals(3, envs.size());
        assertEquals("dev", envs.get(0).environmentName());
        assertEquals("staging", envs.get(1).environmentName());
        assertEquals("prod", envs.get(2).environmentName());
    }

    @Test
    void degradedPrometheusDowngradesHealthyToUnhealthy() {
        when(applicationService.getApplicationsForTeam(100L)).thenReturn(List.of(appA));

        List<EnvironmentChainEntryDto> chain = List.of(
                envEntry("dev", 0, "HEALTHY", "1.0.0"));
        when(environmentService.getEnvironmentChain(100L, 1L))
                .thenReturn(new EnvironmentChainResponse(chain, null));
        when(healthService.getApplicationHealth(100L, 1L))
                .thenReturn(healthResponse("dev", HealthStatus.DEGRADED));
        stubEmptyDora();
        stubEmptyActivity();

        TeamDashboardDto result = dashboardService.getTeamDashboard(100L);

        DashboardEnvironmentEntryDto devEnv = result.applications().get(0).environments().get(0);
        assertEquals("UNHEALTHY", devEnv.status());
        assertEquals("Degraded", devEnv.statusDetail());
    }

    @Test
    void noDataPrometheusDoesNotOverrideArgoCdStatus() {
        when(applicationService.getApplicationsForTeam(100L)).thenReturn(List.of(appA));

        List<EnvironmentChainEntryDto> chain = List.of(
                envEntry("dev", 0, "HEALTHY", "1.0.0"));
        when(environmentService.getEnvironmentChain(100L, 1L))
                .thenReturn(new EnvironmentChainResponse(chain, null));
        when(healthService.getApplicationHealth(100L, 1L))
                .thenReturn(healthResponse("dev", HealthStatus.NO_DATA));
        stubEmptyDora();
        stubEmptyActivity();

        TeamDashboardDto result = dashboardService.getTeamDashboard(100L);

        DashboardEnvironmentEntryDto devEnv = result.applications().get(0).environments().get(0);
        assertEquals("HEALTHY", devEnv.status());
    }

    @Test
    void missingArgoCdDataYieldsUnknown() {
        when(applicationService.getApplicationsForTeam(100L)).thenReturn(List.of(appA));

        List<EnvironmentChainEntryDto> chain = List.of(
                envEntry("dev", 0, "UNKNOWN", null));
        when(environmentService.getEnvironmentChain(100L, 1L))
                .thenReturn(new EnvironmentChainResponse(chain, "ArgoCD unreachable"));
        when(healthService.getApplicationHealth(100L, 1L))
                .thenReturn(new HealthResponse(List.of()));
        stubEmptyDora();
        stubEmptyActivity();

        TeamDashboardDto result = dashboardService.getTeamDashboard(100L);

        DashboardEnvironmentEntryDto devEnv = result.applications().get(0).environments().get(0);
        assertEquals("UNKNOWN", devEnv.status());
        assertTrue(devEnv.statusDetail().contains("Status unavailable"));
        assertTrue(devEnv.statusDetail().contains("ArgoCD unreachable"));
    }

    @Test
    void deployingAndNotDeployedStatusesPreserved() {
        when(applicationService.getApplicationsForTeam(100L)).thenReturn(List.of(appA));

        List<EnvironmentChainEntryDto> chain = List.of(
                envEntry("dev", 0, "DEPLOYING", "1.0.0"),
                envEntry("staging", 1, "NOT_DEPLOYED", null));
        when(environmentService.getEnvironmentChain(100L, 1L))
                .thenReturn(new EnvironmentChainResponse(chain, null));
        when(healthService.getApplicationHealth(100L, 1L))
                .thenReturn(new HealthResponse(List.of()));
        stubEmptyDora();
        stubEmptyActivity();

        TeamDashboardDto result = dashboardService.getTeamDashboard(100L);

        List<DashboardEnvironmentEntryDto> envs = result.applications().get(0).environments();
        assertEquals("DEPLOYING", envs.get(0).status());
        assertEquals("Deploying", envs.get(0).statusDetail());
        assertEquals("NOT_DEPLOYED", envs.get(1).status());
        assertEquals("Not deployed", envs.get(1).statusDetail());
    }

    // ── DORA aggregation math tests ─────────────────────────────────────

    @Test
    void doraDeploymentFrequencyIsSummed() {
        when(applicationService.getApplicationsForTeam(100L)).thenReturn(List.of(appA, appB));
        stubEmptyHealth();
        stubEmptyActivity();

        DoraMetricDto dfA = doraMetric(DoraMetricType.DEPLOYMENT_FREQUENCY, 5.0, 3.0, List.of());
        DoraMetricDto dfB = doraMetric(DoraMetricType.DEPLOYMENT_FREQUENCY, 8.0, 4.0, List.of());
        when(doraService.getDoraMetrics(100L, 1L, null))
                .thenReturn(new DoraMetricsDto(List.of(dfA), "30d", true));
        when(doraService.getDoraMetrics(100L, 2L, null))
                .thenReturn(new DoraMetricsDto(List.of(dfB), "30d", true));

        TeamDashboardDto result = dashboardService.getTeamDashboard(100L);

        DoraMetricDto aggregatedDf = findMetric(result.dora(), DoraMetricType.DEPLOYMENT_FREQUENCY);
        assertNotNull(aggregatedDf);
        assertEquals(13.0, aggregatedDf.currentValue(), 0.001);
        assertEquals(7.0, aggregatedDf.previousValue(), 0.001);
    }

    @Test
    void doraLeadTimeIsMedianOddCount() {
        when(applicationService.getApplicationsForTeam(100L)).thenReturn(List.of(appA, appB));
        stubEmptyHealth();
        stubEmptyActivity();

        Application appC = new Application();
        appC.id = 3L;
        appC.name = "app-gamma";
        appC.teamId = 100L;
        appC.runtimeType = "node";
        appC.gitRepoUrl = "https://github.com/org/gamma.git";
        when(applicationService.getApplicationsForTeam(100L)).thenReturn(List.of(appA, appB, appC));

        DoraMetricDto ltA = doraMetric(DoraMetricType.LEAD_TIME, 2.0, 3.0, List.of());
        DoraMetricDto ltB = doraMetric(DoraMetricType.LEAD_TIME, 5.0, 1.0, List.of());
        DoraMetricDto ltC = doraMetric(DoraMetricType.LEAD_TIME, 8.0, 6.0, List.of());
        when(doraService.getDoraMetrics(100L, 1L, null))
                .thenReturn(new DoraMetricsDto(List.of(ltA), "30d", true));
        when(doraService.getDoraMetrics(100L, 2L, null))
                .thenReturn(new DoraMetricsDto(List.of(ltB), "30d", true));
        when(doraService.getDoraMetrics(100L, 3L, null))
                .thenReturn(new DoraMetricsDto(List.of(ltC), "30d", true));

        TeamDashboardDto result = dashboardService.getTeamDashboard(100L);

        DoraMetricDto aggregatedLt = findMetric(result.dora(), DoraMetricType.LEAD_TIME);
        assertNotNull(aggregatedLt);
        assertEquals(5.0, aggregatedLt.currentValue(), 0.001);
        assertEquals(3.0, aggregatedLt.previousValue(), 0.001);
    }

    @Test
    void doraLeadTimeIsMedianEvenCount() {
        when(applicationService.getApplicationsForTeam(100L)).thenReturn(List.of(appA, appB));
        stubEmptyHealth();
        stubEmptyActivity();

        DoraMetricDto ltA = doraMetric(DoraMetricType.LEAD_TIME, 2.0, 4.0, List.of());
        DoraMetricDto ltB = doraMetric(DoraMetricType.LEAD_TIME, 6.0, 8.0, List.of());
        when(doraService.getDoraMetrics(100L, 1L, null))
                .thenReturn(new DoraMetricsDto(List.of(ltA), "30d", true));
        when(doraService.getDoraMetrics(100L, 2L, null))
                .thenReturn(new DoraMetricsDto(List.of(ltB), "30d", true));

        TeamDashboardDto result = dashboardService.getTeamDashboard(100L);

        DoraMetricDto aggregatedLt = findMetric(result.dora(), DoraMetricType.LEAD_TIME);
        assertNotNull(aggregatedLt);
        assertEquals(4.0, aggregatedLt.currentValue(), 0.001);
        assertEquals(6.0, aggregatedLt.previousValue(), 0.001);
    }

    @Test
    void doraCfrIsWeightedAverageByDeploymentFrequency() {
        when(applicationService.getApplicationsForTeam(100L)).thenReturn(List.of(appA, appB));
        stubEmptyHealth();
        stubEmptyActivity();

        DoraMetricDto dfA = doraMetric(DoraMetricType.DEPLOYMENT_FREQUENCY, 10.0, 8.0, List.of());
        DoraMetricDto cfrA = doraMetric(DoraMetricType.CHANGE_FAILURE_RATE, 20.0, 25.0, List.of());
        DoraMetricDto dfB = doraMetric(DoraMetricType.DEPLOYMENT_FREQUENCY, 30.0, 12.0, List.of());
        DoraMetricDto cfrB = doraMetric(DoraMetricType.CHANGE_FAILURE_RATE, 10.0, 15.0, List.of());

        when(doraService.getDoraMetrics(100L, 1L, null))
                .thenReturn(new DoraMetricsDto(List.of(dfA, cfrA), "30d", true));
        when(doraService.getDoraMetrics(100L, 2L, null))
                .thenReturn(new DoraMetricsDto(List.of(dfB, cfrB), "30d", true));

        TeamDashboardDto result = dashboardService.getTeamDashboard(100L);

        DoraMetricDto aggregatedCfr = findMetric(result.dora(), DoraMetricType.CHANGE_FAILURE_RATE);
        assertNotNull(aggregatedCfr);
        // Current: (20*10 + 10*30) / (10+30) = (200+300)/40 = 12.5
        assertEquals(12.5, aggregatedCfr.currentValue(), 0.001);
        // Previous: (25*8 + 15*12) / (8+12) = (200+180)/20 = 19.0
        assertEquals(19.0, aggregatedCfr.previousValue(), 0.001);
    }

    @Test
    void doraTimeSeriesAlignmentForSum() {
        when(applicationService.getApplicationsForTeam(100L)).thenReturn(List.of(appA, appB));
        stubEmptyHealth();
        stubEmptyActivity();

        List<TimeSeriesPointDto> tsA = List.of(
                new TimeSeriesPointDto(1000L, 3.0),
                new TimeSeriesPointDto(2000L, 5.0));
        List<TimeSeriesPointDto> tsB = List.of(
                new TimeSeriesPointDto(1000L, 2.0),
                new TimeSeriesPointDto(2000L, 4.0));

        DoraMetricDto dfA = doraMetric(DoraMetricType.DEPLOYMENT_FREQUENCY, 5.0, 3.0, tsA);
        DoraMetricDto dfB = doraMetric(DoraMetricType.DEPLOYMENT_FREQUENCY, 8.0, 4.0, tsB);

        when(doraService.getDoraMetrics(100L, 1L, null))
                .thenReturn(new DoraMetricsDto(List.of(dfA), "30d", true));
        when(doraService.getDoraMetrics(100L, 2L, null))
                .thenReturn(new DoraMetricsDto(List.of(dfB), "30d", true));

        TeamDashboardDto result = dashboardService.getTeamDashboard(100L);

        DoraMetricDto df = findMetric(result.dora(), DoraMetricType.DEPLOYMENT_FREQUENCY);
        assertNotNull(df);
        assertEquals(2, df.timeSeries().size());
        assertEquals(5.0, df.timeSeries().get(0).value(), 0.001);
        assertEquals(9.0, df.timeSeries().get(1).value(), 0.001);
    }

    // ── Activity feed tests ─────────────────────────────────────────────

    @Test
    void activityFeedSortedByTimestampDescendingAndCappedAt20() {
        when(applicationService.getApplicationsForTeam(100L)).thenReturn(List.of(appA));
        stubEmptyHealth();
        stubEmptyDora();

        Instant now = Instant.now();
        List<BuildSummaryDto> builds = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) {
            builds.add(new BuildSummaryDto(
                    "build-" + i, "Passed", now.minusSeconds(i * 60), null,
                    "30s", null, appA.name, null));
        }
        when(buildService.listBuilds(100L, 1L)).thenReturn(builds);
        when(releaseService.listReleases(100L, 1L)).thenReturn(List.of());
        when(deploymentService.listDeployments(100L, 1L, null)).thenReturn(List.of());

        TeamDashboardDto result = dashboardService.getTeamDashboard(100L);

        assertTrue(result.recentActivity().size() <= 20);
        for (int i = 0; i < result.recentActivity().size() - 1; i++) {
            assertTrue(result.recentActivity().get(i).timestamp()
                    .isAfter(result.recentActivity().get(i + 1).timestamp())
                    || result.recentActivity().get(i).timestamp()
                    .equals(result.recentActivity().get(i + 1).timestamp()));
        }
    }

    @Test
    void activityFeedMixesEventTypes() {
        when(applicationService.getApplicationsForTeam(100L)).thenReturn(List.of(appA));
        stubEmptyHealth();
        stubEmptyDora();

        Instant now = Instant.now();
        when(buildService.listBuilds(100L, 1L)).thenReturn(List.of(
                new BuildSummaryDto("b-1", "Passed", now.minusSeconds(100), null,
                        "30s", null, appA.name, null)));
        when(releaseService.listReleases(100L, 1L)).thenReturn(List.of(
                new ReleaseSummaryDto("v1.0.0", now.minusSeconds(50), null, "abc123", null)));
        when(deploymentService.listDeployments(100L, 1L, null)).thenReturn(List.of(
                new DeploymentHistoryDto("d-1", "v1.0.0", "Deployed",
                        now, null, "dev@example.com", "prod", null)));

        TeamDashboardDto result = dashboardService.getTeamDashboard(100L);

        assertEquals(3, result.recentActivity().size());
        assertEquals("deployment", result.recentActivity().get(0).eventType());
        assertEquals("dev@example.com", result.recentActivity().get(0).actor());
        assertEquals("prod", result.recentActivity().get(0).environmentName());
        assertEquals("release", result.recentActivity().get(1).eventType());
        assertEquals("build", result.recentActivity().get(2).eventType());
        assertEquals("System", result.recentActivity().get(2).actor());
    }

    @Test
    void activityDeploymentActorFallsBackToSystem() {
        when(applicationService.getApplicationsForTeam(100L)).thenReturn(List.of(appA));
        stubEmptyHealth();
        stubEmptyDora();

        Instant now = Instant.now();
        when(buildService.listBuilds(100L, 1L)).thenReturn(List.of());
        when(releaseService.listReleases(100L, 1L)).thenReturn(List.of());
        when(deploymentService.listDeployments(100L, 1L, null)).thenReturn(List.of(
                new DeploymentHistoryDto("d-1", "v1.0.0", "Deployed",
                        now, null, null, "dev", null)));

        TeamDashboardDto result = dashboardService.getTeamDashboard(100L);

        assertEquals(1, result.recentActivity().size());
        assertEquals("System", result.recentActivity().get(0).actor());
    }

    // ── Partial failure tests ───────────────────────────────────────────

    @Test
    void partialHealthFailureReturnsAvailableDataWithError() {
        when(applicationService.getApplicationsForTeam(100L)).thenReturn(List.of(appA, appB));

        List<EnvironmentChainEntryDto> chainA = List.of(
                envEntry("dev", 0, "HEALTHY", "1.0.0"));
        when(environmentService.getEnvironmentChain(100L, 1L))
                .thenReturn(new EnvironmentChainResponse(chainA, null));
        when(healthService.getApplicationHealth(100L, 1L))
                .thenReturn(healthResponse("dev", HealthStatus.HEALTHY));

        when(environmentService.getEnvironmentChain(100L, 2L))
                .thenThrow(new PortalIntegrationException("argocd", "getEnvironmentChain",
                        "ArgoCD unreachable"));
        when(healthService.getApplicationHealth(100L, 2L))
                .thenReturn(new HealthResponse(List.of()));

        stubEmptyDora();
        stubEmptyActivity();

        TeamDashboardDto result = dashboardService.getTeamDashboard(100L);

        assertEquals(2, result.applications().size());
        assertNotNull(result.healthError());
        assertTrue(result.healthError().contains("app-beta"));

        ApplicationHealthSummaryDto healthyApp = result.applications().stream()
                .filter(a -> "app-alpha".equals(a.applicationName())).findFirst().orElseThrow();
        assertEquals(1, healthyApp.environments().size());
        assertEquals("HEALTHY", healthyApp.environments().get(0).status());
    }

    @Test
    void partialDoraFailureAggregatesFromSuccessfulApps() {
        when(applicationService.getApplicationsForTeam(100L)).thenReturn(List.of(appA, appB));
        stubEmptyHealth();
        stubEmptyActivity();

        DoraMetricDto dfA = doraMetric(DoraMetricType.DEPLOYMENT_FREQUENCY, 5.0, 3.0, List.of());
        when(doraService.getDoraMetrics(100L, 1L, null))
                .thenReturn(new DoraMetricsDto(List.of(dfA), "30d", true));
        when(doraService.getDoraMetrics(100L, 2L, null))
                .thenThrow(new PortalIntegrationException("prometheus", "getDoraMetrics",
                        "Prometheus unreachable"));

        TeamDashboardDto result = dashboardService.getTeamDashboard(100L);

        assertTrue(result.dora().hasData());
        assertNotNull(result.doraError());
        assertTrue(result.doraError().contains("app-beta"));
        DoraMetricDto df = findMetric(result.dora(), DoraMetricType.DEPLOYMENT_FREQUENCY);
        assertNotNull(df);
        assertEquals(5.0, df.currentValue(), 0.001);
    }

    @Test
    void allDoraFailureReturnsNoDataWithError() {
        when(applicationService.getApplicationsForTeam(100L)).thenReturn(List.of(appA));
        stubEmptyHealth();
        stubEmptyActivity();

        when(doraService.getDoraMetrics(100L, 1L, null))
                .thenThrow(new PortalIntegrationException("prometheus", "getDoraMetrics",
                        "Prometheus unreachable"));

        TeamDashboardDto result = dashboardService.getTeamDashboard(100L);

        assertFalse(result.dora().hasData());
        assertNotNull(result.doraError());
    }

    @Test
    void partialActivityFailureReturnsAvailableEventsWithError() {
        when(applicationService.getApplicationsForTeam(100L)).thenReturn(List.of(appA));
        stubEmptyHealth();
        stubEmptyDora();

        Instant now = Instant.now();
        when(buildService.listBuilds(100L, 1L))
                .thenThrow(new PortalIntegrationException("tekton", "listBuilds",
                        "Tekton unreachable"));
        when(releaseService.listReleases(100L, 1L)).thenReturn(List.of(
                new ReleaseSummaryDto("v1.0.0", now, null, "abc", null)));
        when(deploymentService.listDeployments(100L, 1L, null)).thenReturn(List.of());

        TeamDashboardDto result = dashboardService.getTeamDashboard(100L);

        assertEquals(1, result.recentActivity().size());
        assertEquals("release", result.recentActivity().get(0).eventType());
        assertNotNull(result.activityError());
        assertTrue(result.activityError().contains("Build activity unavailable"));
    }

    // ── Helper methods ──────────────────────────────────────────────────

    private EnvironmentChainEntryDto envEntry(String name, int order, String status,
                                               String version) {
        return new EnvironmentChainEntryDto(
                name, "cluster-1", name + "-ns", order, status, version,
                version != null ? Instant.now() : null,
                null, null, null, (long) order, order >= 2);
    }

    private HealthResponse healthResponse(Object... args) {
        List<EnvironmentHealthDto> envHealths = new java.util.ArrayList<>();
        for (int i = 0; i < args.length; i += 2) {
            String envName = (String) args[i];
            HealthStatus status = (HealthStatus) args[i + 1];
            HealthStatusDto statusDto = new HealthStatusDto(status, List.of(), envName + "-ns");
            envHealths.add(new EnvironmentHealthDto(envName, statusDto, null, null));
        }
        return new HealthResponse(envHealths);
    }

    private DoraMetricDto doraMetric(DoraMetricType type, double current, double previous,
                                     List<TimeSeriesPointDto> ts) {
        return new DoraMetricDto(type, current, previous, TrendDirection.STABLE,
                0.0, "/wk", ts);
    }

    private DoraMetricDto findMetric(DoraMetricsDto dora, DoraMetricType type) {
        return dora.metrics().stream()
                .filter(m -> m.type() == type)
                .findFirst()
                .orElse(null);
    }

    private void stubEmptyHealth() {
        when(environmentService.getEnvironmentChain(anyLong(), anyLong()))
                .thenReturn(new EnvironmentChainResponse(List.of(), null));
        when(healthService.getApplicationHealth(anyLong(), anyLong()))
                .thenReturn(new HealthResponse(List.of()));
    }

    private void stubEmptyDora() {
        when(doraService.getDoraMetrics(anyLong(), anyLong(), any()))
                .thenReturn(new DoraMetricsDto(List.of(), "30d", false));
    }

    private void stubEmptyActivity() {
        when(buildService.listBuilds(anyLong(), anyLong())).thenReturn(List.of());
        when(releaseService.listReleases(anyLong(), anyLong())).thenReturn(List.of());
        when(deploymentService.listDeployments(anyLong(), anyLong(), any())).thenReturn(List.of());
    }
}
