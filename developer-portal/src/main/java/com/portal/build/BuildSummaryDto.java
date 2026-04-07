package com.portal.build;

import java.time.Instant;

public record BuildSummaryDto(
    String buildId,
    String status,
    Instant startedAt,
    String applicationName,
    String tektonDeepLink
) {}
