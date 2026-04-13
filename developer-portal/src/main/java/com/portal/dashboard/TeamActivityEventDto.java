package com.portal.dashboard;

import java.time.Instant;

public record TeamActivityEventDto(
        String eventType,
        Long applicationId,
        String applicationName,
        String reference,
        Instant timestamp,
        String status,
        String actor,
        String environmentName) {
}
