package com.portal.health;

/**
 * Health data for a single environment. When Prometheus fails for this environment,
 * {@code healthStatus} is null and {@code error} contains the failure message.
 *
 * @param environmentName  display name of the environment
 * @param healthStatus     health assessment (null if query failed)
 * @param grafanaDeepLink  link to Grafana dashboard for this namespace (null if not configured)
 * @param error            error message if health query failed for this environment (null on success)
 */
public record EnvironmentHealthDto(String environmentName, HealthStatusDto healthStatus,
                                   String grafanaDeepLink, String error) {
}
