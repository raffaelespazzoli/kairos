package com.portal.environment;

/**
 * Portal domain status for an environment — developer-facing, not ArgoCD terminology.
 */
public enum PortalEnvironmentStatus {
    HEALTHY,
    UNHEALTHY,
    DEPLOYING,
    NOT_DEPLOYED
}
