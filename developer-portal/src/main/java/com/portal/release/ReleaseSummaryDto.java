package com.portal.release;

import java.time.Instant;

public record ReleaseSummaryDto(
    String version,
    Instant createdAt,
    String buildId,
    String commitSha,
    String imageReference
) {}
