package com.portal.deployment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DeployRequest(
    @NotBlank String releaseVersion,
    @NotNull Long environmentId
) {}
