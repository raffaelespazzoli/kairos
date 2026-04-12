package com.portal.health;

import com.portal.application.Application;
import com.portal.deeplink.DeepLinkService;
import com.portal.environment.Environment;
import com.portal.integration.PortalIntegrationException;
import com.portal.integration.prometheus.PrometheusAdapter;
import com.portal.integration.prometheus.PrometheusConfig;
import com.portal.integration.prometheus.model.GoldenSignal;
import com.portal.integration.prometheus.model.GoldenSignalType;
import com.portal.integration.prometheus.model.HealthSignalsResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@ApplicationScoped
public class HealthService {

    private static final Logger LOG = Logger.getLogger(HealthService.class);

    @Inject
    PrometheusAdapter prometheusAdapter;

    @Inject
    DeepLinkService deepLinkService;

    @Inject
    PrometheusConfig prometheusConfig;

    /**
     * Retrieves health status for all environments of an application, querying
     * Prometheus in parallel. Per-environment failures are isolated — a single
     * failing namespace does not fail the entire request.
     */
    public HealthResponse getApplicationHealth(Long teamId, Long appId) {
        Application app = requireTeamApplication(teamId, appId);

        List<Environment> environments = Environment.findByApplicationOrderByPromotionOrder(app.id);

        List<CompletableFuture<EnvironmentHealthDto>> futures = environments.stream()
                .map(env -> CompletableFuture.supplyAsync(() -> fetchEnvironmentHealth(env)))
                .toList();

        List<EnvironmentHealthDto> results = futures.stream()
                .map(this::joinSafely)
                .toList();

        return new HealthResponse(results);
    }

    private EnvironmentHealthDto fetchEnvironmentHealth(Environment env) {
        String grafanaLink = deepLinkService.generateGrafanaLink(env.namespace).orElse(null);

        try {
            HealthSignalsResult result = prometheusAdapter.getGoldenSignals(env.namespace);
            HealthStatus status = deriveHealthStatus(result);
            HealthStatusDto healthDto = new HealthStatusDto(status, result.signals(), env.namespace);
            return new EnvironmentHealthDto(env.name, healthDto, grafanaLink, null);
        } catch (PortalIntegrationException e) {
            LOG.warnf("Prometheus query failed for namespace %s: %s", env.namespace, e.getMessage());
            return new EnvironmentHealthDto(env.name, null, grafanaLink, e.getMessage());
        }
    }

    HealthStatus deriveHealthStatus(HealthSignalsResult result) {
        if (!result.hasData()) {
            return HealthStatus.NO_DATA;
        }

        double errorRate = findSignalValue(result.signals(), GoldenSignalType.ERROR_RATE);
        double cpuSaturation = findSignalValue(result.signals(), GoldenSignalType.SATURATION_CPU);
        double memorySaturation = findSignalValue(result.signals(), GoldenSignalType.SATURATION_MEMORY);

        PrometheusConfig.Thresholds thresholds = prometheusConfig.thresholds();

        if (errorRate > thresholds.errorRate()) {
            return HealthStatus.UNHEALTHY;
        }
        if (cpuSaturation > thresholds.saturation() || memorySaturation > thresholds.saturation()) {
            return HealthStatus.DEGRADED;
        }
        return HealthStatus.HEALTHY;
    }

    private double findSignalValue(List<GoldenSignal> signals, GoldenSignalType type) {
        return signals.stream()
                .filter(s -> s.type() == type)
                .mapToDouble(GoldenSignal::value)
                .findFirst()
                .orElse(0.0);
    }

    private EnvironmentHealthDto joinSafely(CompletableFuture<EnvironmentHealthDto> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }

    private Application requireTeamApplication(Long teamId, Long appId) {
        Application app = Application.findById(appId);
        if (app == null || !app.teamId.equals(teamId)) {
            throw new NotFoundException();
        }
        return app;
    }
}
