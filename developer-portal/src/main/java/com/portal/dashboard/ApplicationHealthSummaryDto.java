package com.portal.dashboard;

import java.util.List;

public record ApplicationHealthSummaryDto(
        Long applicationId,
        String applicationName,
        String runtimeType,
        List<DashboardEnvironmentEntryDto> environments) {
}
