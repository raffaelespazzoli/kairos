package com.portal.integration.prometheus.model;

/**
 * A single golden signal metric value returned from Prometheus.
 *
 * @param name  human-readable metric name (e.g. "Latency P95")
 * @param value the numeric value of the metric
 * @param unit  the unit of measurement (e.g. "ms", "req/s", "%")
 * @param type  the golden signal classification
 */
public record GoldenSignal(String name, double value, String unit, GoldenSignalType type) {
}
