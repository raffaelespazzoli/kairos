package com.portal.integration.secrets;

/**
 * Abstraction for retrieving cluster credentials from an external secret manager.
 * Implementations handle the protocol-specific details (e.g., Vault HTTP API).
 * The active implementation is selected at build time via {@code portal.secrets.provider}.
 */
public interface SecretManagerAdapter {

    /**
     * Retrieves credentials for authenticating to the given cluster with the specified role.
     *
     * @param clusterName the target cluster identifier (interpolated into the secret path)
     * @param role the role to assume on the target cluster
     * @return a {@link ClusterCredential} containing the bearer token and TTL
     * @throws com.portal.integration.PortalIntegrationException if the secret manager is unreachable or returns an error
     */
    ClusterCredential getCredentials(String clusterName, String role);
}
