package com.portal.integration.secrets.vault;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Vault-specific configuration properties under {@code portal.secrets.vault.*}.
 */
@ConfigMapping(prefix = "portal.secrets.vault")
public interface VaultConfig {

    String url();

    @WithDefault("/infra/{cluster}/kubernetes-secret-engine/creds/{role}")
    String credentialPathTemplate();

    @WithDefault("portal")
    String authRole();

    @WithDefault("auth/kubernetes")
    String authMountPath();
}
