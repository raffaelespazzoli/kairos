package com.portal.build;

import java.time.Instant;

public record BuildDetailDto(
    String buildId,
    String status,
    Instant startedAt,
    Instant completedAt,
    String duration,
    String applicationName,
    String imageReference,
    String failedStageName,
    String errorSummary,
    String currentStage,
    String tektonDeepLink
) {}
