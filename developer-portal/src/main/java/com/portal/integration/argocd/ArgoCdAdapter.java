package com.portal.integration.argocd;

import com.portal.environment.Environment;
import com.portal.environment.EnvironmentStatusDto;

import java.util.List;

/**
 * Adapter for querying ArgoCD Application sync and health status.
 * Translates ArgoCD concepts to portal domain language.
 */
public interface ArgoCdAdapter {

    /**
     * Queries ArgoCD for the live status of each environment's ArgoCD Application.
     *
     * @param appName     the portal application name (used to derive ArgoCD app names)
     * @param environments the environments to check, ordered by promotion_order
     * @return status for each environment in the same order as input
     */
    List<EnvironmentStatusDto> getEnvironmentStatuses(String appName, List<Environment> environments);
}
