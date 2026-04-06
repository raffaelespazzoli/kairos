package com.portal.onboarding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record OnboardingPlanRequest(
    @NotBlank String gitRepoUrl,
    @NotBlank String appName,
    @NotBlank String runtimeType,
    @NotNull List<String> detectedEnvironments,
    @NotNull Map<String, Long> environmentClusterMap,
    @NotNull Long buildClusterId
) {}
