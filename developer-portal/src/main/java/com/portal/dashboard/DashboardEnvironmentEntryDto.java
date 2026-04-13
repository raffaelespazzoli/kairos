package com.portal.dashboard;

import java.time.Instant;

public record DashboardEnvironmentEntryDto(
        String environmentName,
        String status,
        String deployedVersion,
        Instant lastDeploymentAt,
        String statusDetail) {
}
