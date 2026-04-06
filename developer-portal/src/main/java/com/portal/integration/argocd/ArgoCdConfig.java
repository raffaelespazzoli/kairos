package com.portal.integration.argocd;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/**
 * ArgoCD integration configuration. The {@code provider} property selects
 * which {@link ArgoCdAdapter} implementation is activated at build time.
 */
@ConfigMapping(prefix = "portal.argocd")
public interface ArgoCdConfig {

    @WithDefault("argocd")
    String provider();

    String url();

    Optional<String> token();
}
