package com.portal.integration.registry;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/**
 * Configuration for container registry provider selection and authentication.
 */
@ConfigMapping(prefix = "portal.registry")
public interface RegistryConfig {

    @WithDefault("oci")
    String provider();

    Optional<String> url();

    Optional<String> token();
}
