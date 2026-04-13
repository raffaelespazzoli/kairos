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
import com.portal.health.TimeSeriesPointDto;
import com.portal.integration.prometheus.model.DoraMetricType;
import com.portal.integration.prometheus.model.TrendDirection;
import com.portal.release.ReleaseService;
import com.portal.release.ReleaseSummaryDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@ApplicationScoped
public class DashboardService {

    private static final Logger LOG = Logger.getLogger(DashboardService.class);
    private static final int MAX_ACTIVITY_EVENTS = 20;
    private static final int MAX_EVENTS_PER_SOURCE_PER_APP = 10;

    @Inject
    ApplicationService applicationService;

    @Inject
    EnvironmentService environmentService;

    @Inject
    HealthService healthService;

    @Inject
    DoraService doraService;

    @Inject
    BuildService buildService;

    @Inject
    ReleaseService releaseService;

    @Inject
    DeploymentService deploymentService;

    /**
     * Assembles the team dashboard by aggregating health, DORA, and activity data
     * in parallel across all team applications. Partial failures are isolated to
     * their respective sections rather than failing the entire dashboard.
     */
    public TeamDashboardDto getTeamDashboard(Long teamId) {
        List<Application> apps = applicationService.getApplicationsForTeam(teamId);

        CompletableFuture<HealthAggregation> healthFuture =
                CompletableFuture.supplyAsync(() -> aggregateHealth(teamId, apps));
        CompletableFuture<DoraAggregation> doraFuture =
                CompletableFuture.supplyAsync(() -> aggregateDora(teamId, apps));
        CompletableFuture<ActivityAggregation> activityFuture =
                CompletableFuture.supplyAsync(() -> aggregateActivity(teamId, apps));

        HealthAggregation health = joinSafely(healthFuture);
        DoraAggregation dora = joinSafely(doraFuture);
        ActivityAggregation activity = joinSafely(activityFuture);

        return new TeamDashboardDto(
                health.summaries,
                dora.metrics,
                activity.events,
                health.error,
                dora.error,
                activity.error);
    }

    // ── Health aggregation ──────────────────────────────────────────────

    HealthAggregation aggregateHealth(Long teamId, List<Application> apps) {
        List<String> errors = new CopyOnWriteArrayList<>();
        List<ApplicationHealthSummaryDto> summaries = new ArrayList<>();

        List<CompletableFuture<ApplicationHealthSummaryDto>> futures = apps.stream()
                .map(app -> CompletableFuture.supplyAsync(() ->
                        buildAppHealthSummary(teamId, app, errors)))
                .toList();

        for (CompletableFuture<ApplicationHealthSummaryDto> f : futures) {
            try {
                summaries.add(f.join());
            } catch (CompletionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                LOG.warnf("Health aggregation failed for an application: %s", cause.getMessage());
                errors.add(cause.getMessage());
            }
        }

        String error = errors.isEmpty() ? null : String.join("; ", errors);
        return new HealthAggregation(summaries, error);
    }

    ApplicationHealthSummaryDto buildAppHealthSummary(Long teamId, Application app,
                                                      List<String> errors) {
        EnvironmentChainResponse chain;
        try {
            chain = environmentService.getEnvironmentChain(teamId, app.id);
        } catch (Exception e) {
            LOG.warnf("Environment chain unavailable for app %s: %s", app.name, e.getMessage());
            errors.add("Environment data unavailable for " + app.name);
            return new ApplicationHealthSummaryDto(app.id, app.name, app.runtimeType, List.of());
        }

        HealthResponse healthResponse = null;
        boolean prometheusUnavailable = false;
        try {
            healthResponse = healthService.getApplicationHealth(teamId, app.id);
        } catch (Exception e) {
            LOG.warnf("Prometheus health unavailable for app %s: %s", app.name, e.getMessage());
            prometheusUnavailable = true;
        }

        String argocdError = chain.argocdError();
        List<DashboardEnvironmentEntryDto> entries = new ArrayList<>();

        for (EnvironmentChainEntryDto envEntry : chain.environments()) {
            String baseStatus = envEntry.status();
            String deployedVersion = envEntry.deployedVersion();
            Instant lastDeployedAt = envEntry.lastDeployedAt();

            String finalStatus;
            String statusDetail;

            if (argocdError != null && "UNKNOWN".equals(baseStatus)) {
                finalStatus = "UNKNOWN";
                statusDetail = "Status unavailable — " + argocdError;
            } else if ("DEPLOYING".equals(baseStatus)) {
                finalStatus = "DEPLOYING";
                statusDetail = "Deploying";
            } else if ("NOT_DEPLOYED".equals(baseStatus)) {
                finalStatus = "NOT_DEPLOYED";
                statusDetail = "Not deployed";
            } else if ("UNKNOWN".equals(baseStatus)) {
                finalStatus = "UNKNOWN";
                statusDetail = "Status unavailable";
            } else {
                HealthStatus prometheusStatus = findPrometheusHealth(
                        healthResponse, envEntry.environmentName());
                finalStatus = mergeStatus(baseStatus, prometheusStatus);
                statusDetail = deriveStatusDetail(finalStatus, prometheusStatus);
                if (prometheusUnavailable) {
                    statusDetail = statusDetail + " (live health unavailable)";
                }
            }

            entries.add(new DashboardEnvironmentEntryDto(
                    envEntry.environmentName(), finalStatus, deployedVersion,
                    lastDeployedAt, statusDetail));
        }

        return new ApplicationHealthSummaryDto(app.id, app.name, app.runtimeType, entries);
    }

    /**
     * Finds the Prometheus health status for a given environment name from the
     * health response. Returns null if no health data is available.
     */
    HealthStatus findPrometheusHealth(HealthResponse healthResponse, String envName) {
        if (healthResponse == null) {
            return null;
        }
        return healthResponse.environments().stream()
                .filter(e -> envName.equals(e.environmentName()))
                .findFirst()
                .map(e -> e.healthStatus() != null ? e.healthStatus().status() : null)
                .orElse(null);
    }

    /**
     * Merges ArgoCD base status with Prometheus health following the compact
     * dashboard merge rules from AC #4.
     */
    String mergeStatus(String baseStatus, HealthStatus prometheusStatus) {
        if (prometheusStatus == null || prometheusStatus == HealthStatus.NO_DATA) {
            return baseStatus;
        }
        if (prometheusStatus == HealthStatus.UNHEALTHY) {
            return "UNHEALTHY";
        }
        if (prometheusStatus == HealthStatus.DEGRADED) {
            return "UNHEALTHY";
        }
        return baseStatus;
    }

    String deriveStatusDetail(String finalStatus, HealthStatus prometheusStatus) {
        if (prometheusStatus == HealthStatus.DEGRADED && "UNHEALTHY".equals(finalStatus)) {
            return "Degraded";
        }
        return switch (finalStatus) {
            case "HEALTHY" -> "Healthy";
            case "UNHEALTHY" -> "Unhealthy";
            case "UNKNOWN" -> "Status unavailable";
            default -> finalStatus;
        };
    }

    // ── DORA aggregation ────────────────────────────────────────────────

    DoraAggregation aggregateDora(Long teamId, List<Application> apps) {
        List<String> errors = new CopyOnWriteArrayList<>();
        List<DoraMetricsDto> perAppDora = new ArrayList<>();

        List<CompletableFuture<DoraMetricsDto>> futures = apps.stream()
                .map(app -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return doraService.getDoraMetrics(teamId, app.id, null);
                    } catch (Exception e) {
                        LOG.warnf("DORA retrieval failed for app %s: %s", app.name, e.getMessage());
                        errors.add("DORA unavailable for " + app.name);
                        return null;
                    }
                }))
                .toList();

        for (CompletableFuture<DoraMetricsDto> f : futures) {
            try {
                DoraMetricsDto result = f.join();
                if (result != null && result.hasData()) {
                    perAppDora.add(result);
                }
            } catch (CompletionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                LOG.warnf("DORA aggregation failed: %s", cause.getMessage());
                errors.add(cause.getMessage());
            }
        }

        String error = errors.isEmpty() ? null : String.join("; ", errors);

        if (perAppDora.isEmpty()) {
            return new DoraAggregation(
                    new DoraMetricsDto(List.of(), "30d", false),
                    error != null ? error : "No DORA data available");
        }

        DoraMetricsDto aggregated = aggregateDoraMetrics(perAppDora);
        return new DoraAggregation(aggregated, error);
    }

    DoraMetricsDto aggregateDoraMetrics(List<DoraMetricsDto> perAppDora) {
        String timeRange = perAppDora.get(0).timeRange();

        Map<DoraMetricType, List<DoraMetricDto>> byType = perAppDora.stream()
                .flatMap(d -> d.metrics().stream())
                .collect(Collectors.groupingBy(DoraMetricDto::type));

        List<DoraMetricDto> dfMetrics = byType.getOrDefault(DoraMetricType.DEPLOYMENT_FREQUENCY, List.of());

        List<DoraMetricDto> aggregatedMetrics = new ArrayList<>();
        for (DoraMetricType type : DoraMetricType.values()) {
            List<DoraMetricDto> metricsForType = byType.getOrDefault(type, List.of());
            if (metricsForType.isEmpty()) {
                continue;
            }
            aggregatedMetrics.add(aggregateSingleMetric(type, metricsForType, dfMetrics));
        }

        return new DoraMetricsDto(aggregatedMetrics, timeRange, !aggregatedMetrics.isEmpty());
    }

    DoraMetricDto aggregateSingleMetric(DoraMetricType type, List<DoraMetricDto> metrics,
                                        List<DoraMetricDto> dfMetrics) {
        double currentValue;
        double previousValue;
        List<TimeSeriesPointDto> aggregatedTimeSeries;

        switch (type) {
            case DEPLOYMENT_FREQUENCY -> {
                currentValue = metrics.stream().mapToDouble(DoraMetricDto::currentValue).sum();
                previousValue = metrics.stream().mapToDouble(DoraMetricDto::previousValue).sum();
                aggregatedTimeSeries = aggregateTimeSeriesSum(metrics);
            }
            case LEAD_TIME -> {
                currentValue = median(metrics.stream().mapToDouble(DoraMetricDto::currentValue).toArray());
                previousValue = median(metrics.stream().mapToDouble(DoraMetricDto::previousValue).toArray());
                aggregatedTimeSeries = aggregateTimeSeriesMedian(metrics);
            }
            case CHANGE_FAILURE_RATE -> {
                currentValue = weightedAverageCFR(metrics, dfMetrics, true);
                previousValue = weightedAverageCFR(metrics, dfMetrics, false);
                aggregatedTimeSeries = aggregateTimeSeriesWeightedCFR(metrics, dfMetrics);
            }
            case MTTR -> {
                currentValue = median(metrics.stream().mapToDouble(DoraMetricDto::currentValue).toArray());
                previousValue = median(metrics.stream().mapToDouble(DoraMetricDto::previousValue).toArray());
                aggregatedTimeSeries = aggregateTimeSeriesMedian(metrics);
            }
            default -> {
                currentValue = 0;
                previousValue = 0;
                aggregatedTimeSeries = List.of();
            }
        }

        double percentageChange = calculatePercentageChange(currentValue, previousValue);
        TrendDirection trend = deriveTrend(type, currentValue, previousValue);
        String unit = resolveUnit(type, currentValue);

        return new DoraMetricDto(type, currentValue, previousValue, trend,
                percentageChange, unit, aggregatedTimeSeries);
    }

    /**
     * Weighted average of CFR using each application's deployment frequency as weight.
     * CFR and DF lists are aligned by position (both come from the same per-app DORA results).
     */
    double weightedAverageCFR(List<DoraMetricDto> cfrMetrics, List<DoraMetricDto> dfMetrics,
                              boolean useCurrent) {
        double weightedSum = 0;
        double totalWeight = 0;

        for (int i = 0; i < cfrMetrics.size(); i++) {
            DoraMetricDto cfr = cfrMetrics.get(i);
            double cfrValue = useCurrent ? cfr.currentValue() : cfr.previousValue();
            double weight = 0;
            if (i < dfMetrics.size()) {
                weight = useCurrent ? dfMetrics.get(i).currentValue() : dfMetrics.get(i).previousValue();
            }
            if (weight > 0) {
                weightedSum += cfrValue * weight;
                totalWeight += weight;
            }
        }

        if (totalWeight > 0) {
            return weightedSum / totalWeight;
        }
        // Fall back to simple average when no app has positive deployment frequency
        return cfrMetrics.stream()
                .mapToDouble(m -> useCurrent ? m.currentValue() : m.previousValue())
                .average()
                .orElse(0);
    }

    // ── Time series aggregation helpers ─────────────────────────────────

    List<TimeSeriesPointDto> aggregateTimeSeriesSum(List<DoraMetricDto> metrics) {
        Map<Long, Double> buckets = collectTimeSeriesBuckets(metrics);
        return buckets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new TimeSeriesPointDto(e.getKey(), e.getValue()))
                .toList();
    }

    List<TimeSeriesPointDto> aggregateTimeSeriesMedian(List<DoraMetricDto> metrics) {
        Map<Long, List<Double>> buckets = collectTimeSeriesBucketsGrouped(metrics);
        return buckets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new TimeSeriesPointDto(e.getKey(),
                        median(e.getValue().stream().mapToDouble(Double::doubleValue).toArray())))
                .toList();
    }

    /**
     * Weighted-average CFR time-series using DF time-series values as weights at each bucket.
     */
    List<TimeSeriesPointDto> aggregateTimeSeriesWeightedCFR(List<DoraMetricDto> cfrMetrics,
                                                             List<DoraMetricDto> dfMetrics) {
        Map<Long, List<Double>> cfrBuckets = collectTimeSeriesBucketsGrouped(cfrMetrics);
        Map<Long, Double> dfSumBuckets = collectTimeSeriesBuckets(dfMetrics);
        Map<Long, List<Double>> dfGroupedBuckets = collectTimeSeriesBucketsGrouped(dfMetrics);

        return cfrBuckets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    long ts = e.getKey();
                    List<Double> cfrValues = e.getValue();
                    Double dfTotal = dfSumBuckets.get(ts);

                    if (dfTotal != null && dfTotal > 0 && !dfMetrics.isEmpty()) {
                        Map<Long, List<Double>> dfGrouped = dfGroupedBuckets;
                        List<Double> dfValues = dfGrouped.getOrDefault(ts, List.of());
                        double weightedSum = 0;
                        double totalWeight = 0;
                        for (int i = 0; i < cfrValues.size(); i++) {
                            double w = i < dfValues.size() ? dfValues.get(i) : 0;
                            if (w > 0) {
                                weightedSum += cfrValues.get(i) * w;
                                totalWeight += w;
                            }
                        }
                        if (totalWeight > 0) {
                            return new TimeSeriesPointDto(ts, weightedSum / totalWeight);
                        }
                    }

                    double avg = cfrValues.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                    return new TimeSeriesPointDto(ts, avg);
                })
                .toList();
    }

    private Map<Long, Double> collectTimeSeriesBuckets(List<DoraMetricDto> metrics) {
        return metrics.stream()
                .flatMap(m -> m.timeSeries().stream())
                .collect(Collectors.groupingBy(
                        TimeSeriesPointDto::timestamp,
                        Collectors.summingDouble(TimeSeriesPointDto::value)));
    }

    private Map<Long, List<Double>> collectTimeSeriesBucketsGrouped(List<DoraMetricDto> metrics) {
        return metrics.stream()
                .flatMap(m -> m.timeSeries().stream())
                .collect(Collectors.groupingBy(
                        TimeSeriesPointDto::timestamp,
                        Collectors.mapping(TimeSeriesPointDto::value, Collectors.toList())));
    }

    double median(double[] values) {
        if (values.length == 0) {
            return 0;
        }
        double[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        int mid = sorted.length / 2;
        if (sorted.length % 2 == 0) {
            return (sorted[mid - 1] + sorted[mid]) / 2.0;
        }
        return sorted[mid];
    }

    double calculatePercentageChange(double current, double previous) {
        if (previous == 0 && current == 0) {
            return 0.0;
        }
        if (previous == 0) {
            return 100.0;
        }
        double change = ((current - previous) / Math.abs(previous)) * 100;
        return Math.max(-100.0, Math.min(change, 999.0));
    }

    private static final double TREND_THRESHOLD = 0.05;

    TrendDirection deriveTrend(DoraMetricType type, double current, double previous) {
        if (previous == 0 && current == 0) {
            return TrendDirection.STABLE;
        }
        if (previous == 0) {
            boolean higherIsBetter = type == DoraMetricType.DEPLOYMENT_FREQUENCY;
            return higherIsBetter ? TrendDirection.IMPROVING : TrendDirection.DECLINING;
        }
        double changeRatio = (current - previous) / Math.abs(previous);
        if (Math.abs(changeRatio) < TREND_THRESHOLD) {
            return TrendDirection.STABLE;
        }
        boolean higherIsBetter = type == DoraMetricType.DEPLOYMENT_FREQUENCY;
        if (higherIsBetter) {
            return current > previous ? TrendDirection.IMPROVING : TrendDirection.DECLINING;
        } else {
            return current < previous ? TrendDirection.IMPROVING : TrendDirection.DECLINING;
        }
    }

    String resolveUnit(DoraMetricType type, double currentValue) {
        return switch (type) {
            case DEPLOYMENT_FREQUENCY -> "/wk";
            case CHANGE_FAILURE_RATE -> "%";
            case LEAD_TIME -> currentValue >= 1.0 ? "h" : "m";
            case MTTR -> currentValue >= 60.0 ? "h" : "m";
        };
    }

    // ── Activity aggregation ────────────────────────────────────────────

    ActivityAggregation aggregateActivity(Long teamId, List<Application> apps) {
        List<String> errors = new CopyOnWriteArrayList<>();
        List<TeamActivityEventDto> allEvents = new ArrayList<>();

        List<CompletableFuture<List<TeamActivityEventDto>>> futures = apps.stream()
                .map(app -> CompletableFuture.supplyAsync(() ->
                        collectAppActivity(teamId, app, errors)))
                .toList();

        for (CompletableFuture<List<TeamActivityEventDto>> f : futures) {
            try {
                allEvents.addAll(f.join());
            } catch (CompletionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                LOG.warnf("Activity aggregation failed: %s", cause.getMessage());
                errors.add(cause.getMessage());
            }
        }

        allEvents.sort(Comparator.comparing(TeamActivityEventDto::timestamp).reversed());
        List<TeamActivityEventDto> topEvents = allEvents.size() > MAX_ACTIVITY_EVENTS
                ? allEvents.subList(0, MAX_ACTIVITY_EVENTS)
                : allEvents;

        String error = errors.isEmpty() ? null : String.join("; ", errors);
        return new ActivityAggregation(new ArrayList<>(topEvents), error);
    }

    List<TeamActivityEventDto> collectAppActivity(Long teamId, Application app,
                                                   List<String> errors) {
        List<TeamActivityEventDto> events = new ArrayList<>();

        try {
            List<BuildSummaryDto> builds = buildService.listBuilds(teamId, app.id);
            builds.stream()
                    .sorted(Comparator.comparing(BuildSummaryDto::startedAt,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(MAX_EVENTS_PER_SOURCE_PER_APP)
                    .map(b -> new TeamActivityEventDto(
                            "build", app.id, app.name, b.buildId(),
                            b.startedAt(), b.status(), "System", null))
                    .forEach(events::add);
        } catch (Exception e) {
            LOG.warnf("Build activity unavailable for app %s: %s", app.name, e.getMessage());
            errors.add("Build activity unavailable for " + app.name);
        }

        try {
            List<ReleaseSummaryDto> releases = releaseService.listReleases(teamId, app.id);
            releases.stream()
                    .sorted(Comparator.comparing(ReleaseSummaryDto::createdAt,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(MAX_EVENTS_PER_SOURCE_PER_APP)
                    .map(r -> new TeamActivityEventDto(
                            "release", app.id, app.name, r.version(),
                            r.createdAt(), "Released", "System", null))
                    .forEach(events::add);
        } catch (Exception e) {
            LOG.warnf("Release activity unavailable for app %s: %s", app.name, e.getMessage());
            errors.add("Release activity unavailable for " + app.name);
        }

        try {
            List<DeploymentHistoryDto> deployments =
                    deploymentService.listDeployments(teamId, app.id, null);
            deployments.stream()
                    .sorted(Comparator.comparing(DeploymentHistoryDto::startedAt,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(MAX_EVENTS_PER_SOURCE_PER_APP)
                    .map(d -> new TeamActivityEventDto(
                            "deployment", app.id, app.name, d.releaseVersion(),
                            d.startedAt(), d.status(),
                            d.deployedBy() != null ? d.deployedBy() : "System",
                            d.environmentName()))
                    .forEach(events::add);
        } catch (Exception e) {
            LOG.warnf("Deployment activity unavailable for app %s: %s", app.name, e.getMessage());
            errors.add("Deployment activity unavailable for " + app.name);
        }

        return events;
    }

    // ── Internal aggregation result holders ─────────────────────────────

    private <T> T joinSafely(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }

    record HealthAggregation(List<ApplicationHealthSummaryDto> summaries, String error) {}
    record DoraAggregation(DoraMetricsDto metrics, String error) {}
    record ActivityAggregation(List<TeamActivityEventDto> events, String error) {}
}
