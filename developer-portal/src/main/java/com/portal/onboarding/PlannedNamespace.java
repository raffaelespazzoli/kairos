package com.portal.onboarding;

public record PlannedNamespace(
    String name,
    String clusterName,
    String environmentName,
    boolean isBuild
) {}
