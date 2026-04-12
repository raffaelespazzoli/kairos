package com.portal.health;

import com.portal.integration.prometheus.model.GoldenSignal;

import java.util.List;

/**
 * Translates golden signal metrics into a portal health assessment for a single namespace.
 *
 * @param status        overall health: HEALTHY, UNHEALTHY, DEGRADED, or NO_DATA
 * @param goldenSignals the individual metric values
 * @param namespace     the Kubernetes namespace these metrics are scoped to
 */
public record HealthStatusDto(HealthStatus status, List<GoldenSignal> goldenSignals, String namespace) {
}
