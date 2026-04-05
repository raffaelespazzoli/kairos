package com.portal.cluster;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateClusterRequest(
    @NotBlank String name,
    @NotBlank @Pattern(regexp = "^https://.*") String apiServerUrl
) {}
