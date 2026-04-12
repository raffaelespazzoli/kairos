package com.portal.integration.prometheus.model;

import java.util.List;

/**
 * Result of querying golden signal metrics from Prometheus for a single namespace.
 *
 * @param signals the golden signal values (may contain zeros if no data)
 * @param hasData true if Prometheus returned at least one non-empty result set
 */
public record HealthSignalsResult(List<GoldenSignal> signals, boolean hasData) {
}
