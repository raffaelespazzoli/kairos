package com.portal.integration.secrets;

import java.time.Instant;

/**
 * Credential for accessing a specific cluster, returned by any {@link SecretManagerAdapter}.
 * Contains a bearer token, its TTL, and the computed expiry time.
 */
public record ClusterCredential(
    String token,
    int ttlSeconds,
    Instant expiresAt
) {
    public static ClusterCredential of(String token, int ttlSeconds) {
        return new ClusterCredential(token, ttlSeconds, Instant.now().plusSeconds(ttlSeconds));
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Returns true when the remaining TTL is less than 20% of the original TTL,
     * indicating the credential should be proactively refreshed.
     */
    public boolean isApproachingExpiry() {
        long remainingSeconds = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        return remainingSeconds < (ttlSeconds * 0.2);
    }
}
