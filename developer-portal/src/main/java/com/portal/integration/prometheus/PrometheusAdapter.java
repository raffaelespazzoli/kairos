package com.portal.integration.prometheus;

import com.portal.integration.prometheus.model.HealthSignalsResult;

/**
 * Adapter for querying Prometheus golden signal metrics.
 * Translates Prometheus responses to portal domain types.
 * Implementations are selected at build time via {@code portal.prometheus.provider}.
 */
public interface PrometheusAdapter {

    /**
     * Queries golden signal metrics (latency, traffic, errors, saturation) for a namespace.
     *
     * @param namespace the Kubernetes namespace to scope queries to
     * @return the golden signal values and a flag indicating whether data exists
     */
    HealthSignalsResult getGoldenSignals(String namespace);
}
