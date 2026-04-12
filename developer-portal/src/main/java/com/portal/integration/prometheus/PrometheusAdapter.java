package com.portal.integration.prometheus;

import com.portal.integration.prometheus.model.DoraMetricsResult;
import com.portal.integration.prometheus.model.HealthSignalsResult;

/**
 * Adapter for querying Prometheus metrics — golden signals and DORA delivery metrics.
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

    /**
     * Queries DORA delivery metrics (deployment frequency, lead time, change failure rate, MTTR)
     * for an application across all environments.
     *
     * @param appName   the application name to scope queries to
     * @param timeRange the time range for metrics (e.g. "30d", "90d")
     * @return the DORA metric values with trends and time series data
     */
    DoraMetricsResult getDoraMetrics(String appName, String timeRange);
}
