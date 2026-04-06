package com.portal.onboarding;

import java.util.List;
import java.util.Map;

public record OnboardingPlanResult(
    String appName,
    String teamName,
    List<PlannedNamespace> namespaces,
    List<PlannedArgoCdApp> argoCdApps,
    List<String> promotionChain,
    Map<String, String> generatedManifests
) {}
