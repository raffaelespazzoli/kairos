package com.portal.integration.secrets;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * No-op adapter used during development and testing when Vault is unavailable.
 * Returns a static placeholder credential. Activated when
 * {@code portal.secrets.provider=dev} is set.
 */
@ApplicationScoped
@IfBuildProperty(name = "portal.secrets.provider", stringValue = "dev")
public class DevSecretManagerAdapter implements SecretManagerAdapter {

    @Override
    public ClusterCredential getCredentials(String clusterName, String role) {
        return ClusterCredential.of("dev-token-" + clusterName, 3600);
    }
}
