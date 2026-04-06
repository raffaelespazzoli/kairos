package com.portal.onboarding;

import java.util.List;

public record OnboardingResultDto(
    Long applicationId,
    String applicationName,
    String onboardingPrUrl,
    int namespacesCreated,
    int argoCdAppsCreated,
    List<String> promotionChain
) {}
