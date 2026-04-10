package com.portal.integration.git;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/**
 * Configuration for Git provider selection and authentication.
 * The {@code provider} property selects which {@link GitProvider} implementation
 * the {@link GitProviderFactory} produces at runtime.
 */
@ConfigMapping(prefix = "portal.git")
public interface GitProviderConfig {

    @WithDefault("github")
    String provider();

    String token();

    String infraRepoUrl();

    Optional<String> apiUrl();

    @WithDefault("main")
    String defaultBranch();
}
