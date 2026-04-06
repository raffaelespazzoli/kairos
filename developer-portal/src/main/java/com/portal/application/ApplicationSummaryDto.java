package com.portal.application;

import java.time.Instant;

public record ApplicationSummaryDto(
    Long id,
    String name,
    String runtimeType,
    Instant onboardedAt,
    String onboardingPrUrl,
    String gitRepoUrl,
    String devSpacesDeepLink
) {
    public static ApplicationSummaryDto from(Application entity, String devSpacesDeepLink) {
        return new ApplicationSummaryDto(
            entity.id,
            entity.name,
            entity.runtimeType,
            entity.onboardedAt,
            entity.onboardingPrUrl,
            entity.gitRepoUrl,
            devSpacesDeepLink
        );
    }
}
