package com.portal.integration.tekton;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Optional;

@ConfigMapping(prefix = "portal.tekton")
public interface TektonConfig {

    @WithDefault("tekton")
    String provider();

    @WithName("dashboard-url")
    Optional<String> dashboardUrl();
}
