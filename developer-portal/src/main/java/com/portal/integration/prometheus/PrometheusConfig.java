package com.portal.integration.prometheus;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Prometheus integration configuration. The {@code provider} property selects
 * which {@link PrometheusAdapter} implementation is activated at build time.
 * Query templates use {@code {namespace}} and {@code {interval}} placeholders
 * substituted at runtime by the adapter.
 */
@ConfigMapping(prefix = "portal.prometheus")
public interface PrometheusConfig {

    @WithDefault("prometheus")
    String provider();

    String url();

    @WithDefault("5m")
    String queryInterval();

    Queries queries();

    Thresholds thresholds();

    interface Queries {
        String latencyP50();

        String latencyP95();

        String latencyP99();

        String trafficRate();

        String errorRate();

        String saturationCpu();

        String saturationMemory();
    }

    interface Thresholds {
        @WithDefault("5.0")
        double errorRate();

        @WithDefault("90.0")
        double saturation();
    }
}
