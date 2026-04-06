package com.portal.onboarding;

public record PlannedArgoCdApp(
    String name,
    String clusterName,
    String namespace,
    String chartPath,
    String valuesFile,
    boolean isBuild
) {}
