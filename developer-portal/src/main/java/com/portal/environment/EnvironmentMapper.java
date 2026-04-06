package com.portal.environment;

import com.portal.cluster.Cluster;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EnvironmentMapper {

    private EnvironmentMapper() {}

    public static List<EnvironmentChainEntryDto> merge(
            List<Environment> environments,
            List<EnvironmentStatusDto> statuses,
            Map<Long, String> clusterNames) {

        Map<String, EnvironmentStatusDto> statusByEnv = statuses.stream()
                .collect(Collectors.toMap(
                        EnvironmentStatusDto::environmentName, s -> s));

        return environments.stream()
                .map(env -> {
                    EnvironmentStatusDto status = statusByEnv.get(env.name);
                    return new EnvironmentChainEntryDto(
                            env.name,
                            clusterNames.getOrDefault(env.clusterId, null),
                            env.namespace,
                            env.promotionOrder,
                            status != null ? status.status().name() : "UNKNOWN",
                            status != null ? status.deployedVersion() : null,
                            status != null ? status.lastDeployedAt() : null,
                            status != null ? status.argocdDeepLink() : null);
                })
                .toList();
    }
}
