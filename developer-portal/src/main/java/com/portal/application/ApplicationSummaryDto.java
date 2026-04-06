package com.portal.application;

import java.time.Instant;

public record ApplicationSummaryDto(
    Long id,
    String name,
    String runtimeType,
    Instant onboardedAt,
    String onboardingPrUrl
) {
    public static ApplicationSummaryDto from(Application entity) {
        return new ApplicationSummaryDto(
            entity.id,
            entity.name,
            entity.runtimeType,
            entity.onboardedAt,
            entity.onboardingPrUrl
        );
    }
}
