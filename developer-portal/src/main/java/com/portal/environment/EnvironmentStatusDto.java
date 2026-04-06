package com.portal.environment;

import java.time.Instant;

/**
 * Live status of a single environment, translated from ArgoCD domain to portal domain language.
 * Used by EnvironmentService/EnvironmentResource in Story 2.8 and beyond.
 */
public record EnvironmentStatusDto(
    String environmentName,
    PortalEnvironmentStatus status,
    String deployedVersion,
    Instant lastDeployedAt,
    String argocdAppName,
    String argocdDeepLink,
    String grafanaDeepLink
) {}
