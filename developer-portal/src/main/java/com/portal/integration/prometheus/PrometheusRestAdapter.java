package com.portal.integration.prometheus;

import com.fasterxml.jackson.databind.JsonNode;
import com.portal.integration.PortalIntegrationException;
import com.portal.integration.prometheus.model.GoldenSignal;
import com.portal.integration.prometheus.model.GoldenSignalType;
import com.portal.integration.prometheus.model.HealthSignalsResult;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Production Prometheus adapter that executes externalized PromQL queries
 * against the Prometheus HTTP API and parses instant query vector results.
 */
@ApplicationScoped
@IfBuildProperty(name = "portal.prometheus.provider", stringValue = "prometheus", enableIfMissing = true)
public class PrometheusRestAdapter implements PrometheusAdapter {

    private static final Logger LOG = Logger.getLogger(PrometheusRestAdapter.class);

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

    record QueryResult(double value, boolean hasResult) {
        static final QueryResult NO_RESULT = new QueryResult(0.0, false);
    }

    private record QuerySpec(String template, String name, String unit, GoldenSignalType type, double multiplier) {
    }
}
