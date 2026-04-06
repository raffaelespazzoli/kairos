package com.portal.integration.argocd;

import com.portal.environment.Environment;
import com.portal.environment.EnvironmentStatusDto;
import com.portal.environment.PortalEnvironmentStatus;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;

/**
 * Dev-mode adapter returning mock environment status data.
 * First environment (promotion_order 0) returns HEALTHY with a version,
 * second returns DEPLOYING, rest return NOT_DEPLOYED.
 */
@ApplicationScoped
@IfBuildProperty(name = "portal.argocd.provider", stringValue = "dev")
public class DevArgoCdAdapter implements ArgoCdAdapter {

    @Override
    public List<EnvironmentStatusDto> getEnvironmentStatuses(String appName,
            List<Environment> environments) {
        return environments.stream()
                .map(env -> {
                    int order = env.promotionOrder;
                    PortalEnvironmentStatus status = switch (order) {
                        case 0 -> PortalEnvironmentStatus.HEALTHY;
                        case 1 -> PortalEnvironmentStatus.DEPLOYING;
                        default -> PortalEnvironmentStatus.NOT_DEPLOYED;
                    };
                    String version = status == PortalEnvironmentStatus.HEALTHY ? "v1.2.3" : null;
                    Instant deployedAt = status == PortalEnvironmentStatus.HEALTHY
                            ? Instant.now().minusSeconds(7200) : null;

                    return new EnvironmentStatusDto(
                            env.name, status, version, deployedAt,
                            appName + "-run-" + env.name.toLowerCase(),
                            "https://dev-argocd/applications/" + appName + "-run-" + env.name.toLowerCase(),
                            null);
                })
                .toList();
    }
}
