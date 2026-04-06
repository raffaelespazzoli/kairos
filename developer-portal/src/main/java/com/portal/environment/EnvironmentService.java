package com.portal.environment;

import com.portal.application.Application;
import com.portal.cluster.Cluster;
import com.portal.integration.PortalIntegrationException;
import com.portal.integration.argocd.ArgoCdAdapter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class EnvironmentService {

    @Inject
    ArgoCdAdapter argoCdAdapter;

    public EnvironmentChainResponse getEnvironmentChain(Long teamId, Long appId) {
        Application app = Application.findById(appId);
        if (app == null || !app.teamId.equals(teamId)) {
            throw new NotFoundException();
        }

        List<Environment> environments =
                Environment.findByApplicationOrderByPromotionOrder(appId);

        Map<Long, String> clusterNames = resolveClusterNames(environments);

        String argocdError = null;
        List<EnvironmentStatusDto> statuses = List.of();
        try {
            statuses = argoCdAdapter.getEnvironmentStatuses(app.name, environments);
        } catch (PortalIntegrationException e) {
            argocdError = e.getMessage();
        }

        List<EnvironmentChainEntryDto> entries =
                EnvironmentMapper.merge(environments, statuses, clusterNames);

        return new EnvironmentChainResponse(entries, argocdError);
    }

    private Map<Long, String> resolveClusterNames(List<Environment> environments) {
        Set<Long> clusterIds = environments.stream()
                .map(e -> e.clusterId)
                .collect(Collectors.toSet());

        return clusterIds.stream()
                .map(id -> Cluster.<Cluster>findById(id))
                .filter(c -> c != null)
                .collect(Collectors.toMap(c -> c.id, c -> c.name));
    }
}
