package com.portal.environment;

import java.time.Instant;

public record EnvironmentChainEntryDto(
    String environmentName,
    String clusterName,
    String namespace,
    int promotionOrder,
    String status,
    String deployedVersion,
    Instant lastDeployedAt,
    String argocdDeepLink,
    String vaultDeepLink,
    String grafanaDeepLink
) {}
