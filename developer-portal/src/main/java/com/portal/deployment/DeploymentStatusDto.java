package com.portal.deployment;

import java.time.Instant;

public record DeploymentStatusDto(
    String deploymentId,
    String releaseVersion,
    String environmentName,
    String status,
    Instant startedAt
) {}
