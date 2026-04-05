package com.portal.integration.secrets;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Top-level secret manager configuration. The {@code provider} property selects
 * which {@link SecretManagerAdapter} implementation is activated at build time.
 */
@ConfigMapping(prefix = "portal.secrets")
public interface SecretManagerConfig {

    @WithDefault("vault")
    String provider();
}
