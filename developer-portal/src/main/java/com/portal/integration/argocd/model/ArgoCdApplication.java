package com.portal.integration.argocd.model;

/**
 * Minimal representation of an ArgoCD Application for internal adapter use.
 */
public record ArgoCdApplication(
    String name,
    ArgoCdSyncStatus status
) {}
