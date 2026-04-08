package com.portal.release;

public record CreateReleaseRequest(
    String buildId,
    String version
) {}
