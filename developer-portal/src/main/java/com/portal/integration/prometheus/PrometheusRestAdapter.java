package com.portal.integration.prometheus;

import com.fasterxml.jackson.databind.JsonNode;
import com.portal.integration.PortalIntegrationException;
import com.portal.integration.prometheus.model.DoraMetric;
import com.portal.integration.prometheus.model.DoraMetricType;
import com.portal.integration.prometheus.model.DoraMetricsResult;
import com.portal.integration.prometheus.model.GoldenSignal;
import com.portal.integration.prometheus.model.GoldenSignalType;
import com.portal.integration.prometheus.model.HealthSignalsResult;
import com.portal.integration.prometheus.model.TimeSeriesPoint;
import com.portal.integration.prometheus.model.TrendDirection;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Production Prometheus adapter that executes externalized PromQL queries
 * against the Prometheus HTTP API and parses instant query vector results.
 */
@ApplicationScoped
@IfBuildProperty(name = "portal.prometheus.provider", stringValue = "prometheus", enableIfMissing = true)
public class PrometheusRestAdapter implements PrometheusAdapter {

    private static final Logger LOG = Logger.getLogger(PrometheusRestAdapter.class);
    private static final double TREND_THRESHOLD = 0.05;

    @Inject
    @RestClient
    PrometheusRestClient restClient;

    @Inject
    PrometheusConfig config;

    @Override
    public HealthSignalsResult getGoldenSignals(String namespace) {
        String interval = config.queryInterval();
        PrometheusConfig.Queries queries = config.queries();

        List<QuerySpec> specs = List.of(
                new QuerySpec(queries.latencyP50(), "Latency P50", "ms", GoldenSignalType.LATENCY_P50, 1000.0),
                new QuerySpec(queries.latencyP95(), "Latency P95", "ms", GoldenSignalType.LATENCY_P95, 1000.0),
                new QuerySpec(queries.latencyP99(), "Latency P99", "ms", GoldenSignalType.LATENCY_P99, 1000.0),
                new QuerySpec(queries.trafficRate(), "Traffic Rate", "req/s", GoldenSignalType.TRAFFIC_RATE, 1.0),
                new QuerySpec(queries.errorRate(), "Error Rate", "%", GoldenSignalType.ERROR_RATE, 1.0),
                new QuerySpec(queries.saturationCpu(), "CPU Saturation", "%", GoldenSignalType.SATURATION_CPU, 1.0),
                new QuerySpec(queries.saturationMemory(), "Memory Saturation", "%", GoldenSignalType.SATURATION_MEMORY, 1.0)
        );

        List<GoldenSignal> signals = new ArrayList<>();
        boolean anyData = false;

        for (QuerySpec spec : specs) {
            String promql = substituteParams(spec.template, namespace, interval);
            QueryResult qr = executeQuery(promql, spec);
            if (qr.hasResult) {
                anyData = true;
            }
            signals.add(new GoldenSignal(spec.name, qr.value * spec.multiplier, spec.unit, spec.type));
        }

        return new HealthSignalsResult(signals, anyData);
    }

    @Override
    public DoraMetricsResult getDoraMetrics(String appName, String timeRange) {
        PrometheusConfig.DoraQueries doraQueries = config.doraQueries();
        String step = config.doraStepInterval();

        List<DoraQuerySpec> specs = List.of(
                new DoraQuerySpec(doraQueries.deploymentFrequency(), DoraMetricType.DEPLOYMENT_FREQUENCY, "/wk", true),
                new DoraQuerySpec(doraQueries.leadTime(), DoraMetricType.LEAD_TIME, "h", false),
                new DoraQuerySpec(doraQueries.changeFailureRate(), DoraMetricType.CHANGE_FAILURE_RATE, "%", false),
                new DoraQuerySpec(doraQueries.mttr(), DoraMetricType.MTTR, "m", false)
        );

        List<CompletableFuture<DoraMetric>> futures = specs.stream()
                .map(spec -> CompletableFuture.supplyAsync(() ->
                        fetchDoraMetric(spec, appName, timeRange, step)))
                .toList();

        List<DoraMetric> metrics;
        try {
            metrics = futures.stream()
                    .map(PrometheusRestAdapter::joinDora)
                    .toList();
        } catch (PortalIntegrationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new PortalIntegrationException("prometheus", "getDoraMetrics",
                    "Delivery metrics unavailable — metrics system is unreachable", null, e);
        }

        boolean hasData = metrics.stream()
                .anyMatch(m -> !m.timeSeries().isEmpty() && m.timeSeries().size() >= 7);

        return new DoraMetricsResult(metrics, hasData);
    }

    private static final int MIN_DATAPOINTS_FOR_TREND = 7;

    private DoraMetric fetchDoraMetric(DoraQuerySpec spec, String appName, String timeRange, String step) {
        String template = spec.template;
        String promql = substituteDoraParams(template, appName, timeRange);
        String offsetPromql = "(" + promql + ") offset " + timeRange;

        CompletableFuture<Double> previousFuture = CompletableFuture.supplyAsync(() ->
                executeDoraInstantQuery(offsetPromql, spec));
        CompletableFuture<List<TimeSeriesPoint>> timeSeriesFuture = CompletableFuture.supplyAsync(() ->
                executeDoraRangeQuery(promql, timeRange, step, spec));

        double previousValue = joinDora(previousFuture);
        List<TimeSeriesPoint> timeSeries = joinDora(timeSeriesFuture);

        double currentValue = timeSeries.isEmpty() ? 0.0 : timeSeries.get(timeSeries.size() - 1).value();

        if (timeSeries.size() < MIN_DATAPOINTS_FOR_TREND) {
            return new DoraMetric(spec.type, 0.0, 0.0, TrendDirection.STABLE, spec.unit, timeSeries);
        }

        TrendDirection trend = calculateTrend(currentValue, previousValue, spec.higherIsBetter);

        return new DoraMetric(spec.type, currentValue, previousValue, trend, spec.unit, timeSeries);
    }

    private double executeDoraInstantQuery(String promql, DoraQuerySpec spec) {
        try {
            JsonNode response = restClient.query(promql);
            QueryResult qr = parseVectorValue(response);
            return qr.value;
        } catch (WebApplicationException e) {
            int status = e.getResponse().getStatus();
            if (status == 400 || status == 422) {
                LOG.warnf("Prometheus returned %d for DORA query [%s]: %s — returning zero for %s",
                        status, promql, e.getMessage(), spec.type);
                return 0.0;
            }
            throw new PortalIntegrationException("prometheus", "getDoraMetrics",
                    "Delivery metrics unavailable — metrics system is unreachable", null, e);
        } catch (PortalIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new PortalIntegrationException("prometheus", "getDoraMetrics",
                    "Delivery metrics unavailable — metrics system is unreachable", null, e);
        }
    }

    private List<TimeSeriesPoint> executeDoraRangeQuery(String promql, String timeRange, String step,
                                                         DoraQuerySpec spec) {
        long now = Instant.now().getEpochSecond();
        long rangeSeconds = parseDurationToSeconds(timeRange);
        long start = now - rangeSeconds;

        try {
            JsonNode response = restClient.queryRange(promql,
                    String.valueOf(start), String.valueOf(now), step);
            return parseMatrixValues(response);
        } catch (WebApplicationException e) {
            int status = e.getResponse().getStatus();
            if (status == 400 || status == 422) {
                LOG.warnf("Prometheus returned %d for DORA range query [%s]: %s — returning empty series for %s",
                        status, promql, e.getMessage(), spec.type);
                return List.of();
            }
            throw new PortalIntegrationException("prometheus", "getDoraMetrics",
                    "Delivery metrics unavailable — metrics system is unreachable", null, e);
        } catch (PortalIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new PortalIntegrationException("prometheus", "getDoraMetrics",
                    "Delivery metrics unavailable — metrics system is unreachable", null, e);
        }
    }

    List<TimeSeriesPoint> parseMatrixValues(JsonNode response) {
        JsonNode result = response.path("data").path("result");
        if (!result.isArray() || result.isEmpty()) {
            return List.of();
        }
        if (result.size() > 1) {
            LOG.warnf("DORA range query returned %d series, using first only", result.size());
        }
        JsonNode values = result.get(0).path("values");
        if (!values.isArray()) {
            return List.of();
        }
        List<TimeSeriesPoint> points = new ArrayList<>();
        for (JsonNode pair : values) {
            if (!pair.isArray() || pair.size() < 2) {
                continue;
            }
            long timestamp = pair.get(0).asLong();
            String raw = pair.get(1).asText("0");
            try {
                double val = Double.parseDouble(raw);
                if (Double.isNaN(val) || Double.isInfinite(val)) {
                    continue;
                }
                points.add(new TimeSeriesPoint(timestamp, val));
            } catch (NumberFormatException e) {
                // skip unparseable
            }
        }
        return points;
    }

    TrendDirection calculateTrend(double current, double previous, boolean higherIsBetter) {
        if (previous == 0 && current == 0) {
            return TrendDirection.STABLE;
        }
        if (previous == 0) {
            return higherIsBetter ? TrendDirection.IMPROVING : TrendDirection.DECLINING;
        }
        double changeRatio = (current - previous) / Math.abs(previous);
        if (Math.abs(changeRatio) < TREND_THRESHOLD) {
            return TrendDirection.STABLE;
        }
        if (higherIsBetter) {
            return current > previous ? TrendDirection.IMPROVING : TrendDirection.DECLINING;
        } else {
            return current < previous ? TrendDirection.IMPROVING : TrendDirection.DECLINING;
        }
    }

    String substituteDoraParams(String template, String appName, String range) {
        return template
                .replace("{appName}", appName)
                .replace("{range}", range);
    }

    long parseDurationToSeconds(String duration) {
        if (duration == null || duration.length() < 2) {
            return 30L * 24 * 3600;
        }
        char unit = duration.charAt(duration.length() - 1);
        long value;
        try {
            value = Long.parseLong(duration.substring(0, duration.length() - 1));
        } catch (NumberFormatException e) {
            LOG.warnf("Unparseable DORA duration '%s', falling back to 30d default", duration);
            return 30L * 24 * 3600;
        }
        return switch (unit) {
            case 'd' -> value * 24 * 3600;
            case 'h' -> value * 3600;
            case 'm' -> value * 60;
            case 's' -> value;
            default -> value * 24 * 3600;
        };
    }

    String substituteParams(String template, String namespace, String interval) {
        return template
                .replace("{namespace}", namespace)
                .replace("{interval}", interval);
    }

    private QueryResult executeQuery(String promql, QuerySpec spec) {
        try {
            JsonNode response = restClient.query(promql);
            return parseVectorValue(response);
        } catch (WebApplicationException e) {
            int status = e.getResponse().getStatus();
            if (status == 400 || status == 422) {
                LOG.warnf("Prometheus returned %d for query [%s]: %s — returning zero for %s",
                        status, promql, e.getMessage(), spec.name);
                return QueryResult.NO_RESULT;
            }
            throw new PortalIntegrationException("prometheus", "getGoldenSignals",
                    "Health data unavailable — metrics system is unreachable", null, e);
        } catch (PortalIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new PortalIntegrationException("prometheus", "getGoldenSignals",
                    "Health data unavailable — metrics system is unreachable", null, e);
        }
    }

    /**
     * Parses the scalar value from a Prometheus instant query vector result.
     * Expected path: {@code data.result[0].value[1]} (string representation of numeric value).
     * Returns {@code hasResult=false} if the result array is empty (no matching time series);
     * returns {@code hasResult=true} with the parsed value otherwise (including zero).
     */
    QueryResult parseVectorValue(JsonNode response) {
        JsonNode result = response.path("data").path("result");
        if (!result.isArray() || result.isEmpty()) {
            return QueryResult.NO_RESULT;
        }
        JsonNode value = result.get(0).path("value");
        if (!value.isArray() || value.size() < 2) {
            return QueryResult.NO_RESULT;
        }
        String raw = value.get(1).asText("0");
        try {
            double parsed = Double.parseDouble(raw);
            double safe = Double.isNaN(parsed) || Double.isInfinite(parsed) ? 0.0 : parsed;
            return new QueryResult(safe, true);
        } catch (NumberFormatException e) {
            return QueryResult.NO_RESULT;
        }
    }

    private static <T> T joinDora(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof PortalIntegrationException pie) {
                throw pie;
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }

    record QueryResult(double value, boolean hasResult) {
        static final QueryResult NO_RESULT = new QueryResult(0.0, false);
    }

    private record QuerySpec(String template, String name, String unit, GoldenSignalType type, double multiplier) {
    }

    private record DoraQuerySpec(String template, DoraMetricType type, String unit, boolean higherIsBetter) {
    }
}
