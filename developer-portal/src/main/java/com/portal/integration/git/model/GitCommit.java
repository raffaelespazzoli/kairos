package com.portal.integration.git.model;

import java.time.Instant;

/**
 * Represents a Git commit with metadata needed for deployment history extraction.
 *
 * @param sha       the full SHA of the commit
 * @param author    the commit author name or email
 * @param timestamp the commit creation timestamp
 * @param message   the full commit message (subject + body)
 */
public record GitCommit(
    String sha,
    String author,
    Instant timestamp,
    String message
) {}
