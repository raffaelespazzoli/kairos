package com.portal.onboarding;

import jakarta.validation.constraints.NotBlank;

public record ValidateRepoRequest(@NotBlank String gitRepoUrl) {}
