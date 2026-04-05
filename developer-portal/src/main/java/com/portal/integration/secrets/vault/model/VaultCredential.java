package com.portal.integration.secrets.vault.model;

import java.time.Instant;

/**
 * Internal representation of a Vault Kubernetes secret engine credential response.
 * Maps the JSON returned by {@code POST /v1/infra/{cluster}/kubernetes-secret-engine/creds/{role}}.
 * Not exposed via REST — used only within the Vault adapter layer.
 */
public class VaultCredential {

    private final String serviceAccountToken;
    private final int leaseDuration;
    private final String serviceAccountName;
    private final String serviceAccountNamespace;
    private final Instant createdAt;

    public VaultCredential(String serviceAccountToken, int leaseDuration,
                           String serviceAccountName, String serviceAccountNamespace) {
        this.serviceAccountToken = serviceAccountToken;
        this.leaseDuration = leaseDuration;
        this.serviceAccountName = serviceAccountName;
        this.serviceAccountNamespace = serviceAccountNamespace;
        this.createdAt = Instant.now();
    }

    public String getServiceAccountToken() {
        return serviceAccountToken;
    }

    public int getLeaseDuration() {
        return leaseDuration;
    }

    public String getServiceAccountName() {
        return serviceAccountName;
    }

    public String getServiceAccountNamespace() {
        return serviceAccountNamespace;
    }

    public Instant getExpiresAt() {
        return createdAt.plusSeconds(leaseDuration);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
