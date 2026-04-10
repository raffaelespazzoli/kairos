package com.portal.deployment;

import java.time.Instant;

public record DeploymentHistoryDto(
    String deploymentId,
    String releaseVersion,
    String status,
    Instant startedAt,
    Instant completedAt,
    String deployedBy,
    String environmentName,
    String argocdDeepLink
) {}
