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
 * Uses 1-based promotion_order (matching seed data): first env is HEALTHY,
 * second is DEPLOYING, rest are NOT_DEPLOYED.
 */
@ApplicationScoped
@IfBuildProperty(name = "portal.argocd.provider", stringValue = "dev")
public class DevArgoCdAdapter implements ArgoCdAdapter {

    @Override
    public List<EnvironmentStatusDto> getEnvironmentStatuses(String appName,
            List<Environment> environments) {
        int minOrder = environments.stream()
                .mapToInt(e -> e.promotionOrder)
                .min()
                .orElse(1);

        return environments.stream()
                .map(env -> {
                    int relative = env.promotionOrder - minOrder;
                    PortalEnvironmentStatus status = switch (relative) {
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
