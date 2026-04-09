package com.portal.integration.git.model;

import java.time.Instant;

/**
 * Represents a Git tag with its associated commit metadata.
 *
 * @param name      the tag name (e.g., "v1.4.2")
 * @param commitSha the full SHA of the tagged commit
 * @param createdAt the tag or commit creation timestamp
 */
public record GitTag(
    String name,
    String commitSha,
    Instant createdAt
) {}
