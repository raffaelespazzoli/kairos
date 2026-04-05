package com.portal.integration.secrets;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.ConcurrentHashMap;

/**
 * TTL-aware in-memory cache over the active {@link SecretManagerAdapter}.
 * Prevents redundant calls to the secret manager for the same (cluster, role) pair
 * and proactively refreshes credentials approaching TTL expiry.
 *
 * <p>Thread-safe: uses {@link ConcurrentHashMap#compute} to prevent thundering-herd
 * duplicate fetches for the same cache key.</p>
 *
 * <p>Credentials exist only in memory — never written to database or filesystem (NFR6).</p>
 */
@ApplicationScoped
public class SecretManagerCredentialProvider {

    @Inject
    SecretManagerAdapter adapter;

    private final ConcurrentHashMap<String, ClusterCredential> cache = new ConcurrentHashMap<>();

    /**
     * Returns cached credentials if valid and not approaching expiry.
     * On cache miss or approaching expiry, fetches fresh credentials from the adapter.
     */
    public ClusterCredential getCredentials(String clusterName, String role) {
        String cacheKey = clusterName + "::" + role;

        ClusterCredential cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired() && !cached.isApproachingExpiry()) {
            return cached;
        }

        return cache.compute(cacheKey, (key, existing) -> {
            if (existing != null && !existing.isExpired() && !existing.isApproachingExpiry()) {
                return existing;
            }
            return adapter.getCredentials(clusterName, role);
        });
    }

    // Visible for testing
    void clearCache() {
        cache.clear();
    }
}
