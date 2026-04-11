package com.portal.environment;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EnvironmentMapper {

    private EnvironmentMapper() {}

    public static List<EnvironmentChainEntryDto> merge(
            List<Environment> environments,
            List<EnvironmentStatusDto> statuses,
            Map<Long, String> clusterNames,
            Map<String, String> vaultLinks) {

        Map<String, EnvironmentStatusDto> statusByEnv = statuses.stream()
                .collect(Collectors.toMap(
                        EnvironmentStatusDto::environmentName, s -> s));

        return environments.stream()
                .map(env -> {
                    EnvironmentStatusDto status = statusByEnv.get(env.name);
                    String vaultLink = vaultLinks.getOrDefault(env.name, null);
                    return new EnvironmentChainEntryDto(
                            env.name,
                            clusterNames.getOrDefault(env.clusterId, null),
                            env.namespace,
                            env.promotionOrder,
                            status != null ? status.status().name() : "UNKNOWN",
                            status != null ? status.deployedVersion() : null,
                            status != null ? status.lastDeployedAt() : null,
                            status != null ? status.argocdDeepLink() : null,
                            vaultLink != null && !vaultLink.isEmpty() ? vaultLink : null,
                            status != null ? status.grafanaDeepLink() : null,
                            env.id,
                            Boolean.TRUE.equals(env.isProduction));
                })
                .toList();
    }
}
