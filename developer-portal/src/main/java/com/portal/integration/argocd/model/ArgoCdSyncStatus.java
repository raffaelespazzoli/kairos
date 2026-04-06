package com.portal.integration.argocd.model;

import java.time.Instant;
import java.util.Optional;

/**
 * Parsed subset of ArgoCD Application status.
 * Internal to the adapter — never exposed outside the argocd package.
 */
public record ArgoCdSyncStatus(
    String syncStatus,
    String healthStatus,
    Optional<String> deployedVersion,
    Optional<Instant> operationFinishedAt
) {}
