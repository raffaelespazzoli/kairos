package com.portal.dashboard;

import com.portal.health.DoraMetricsDto;

import java.util.List;

public record TeamDashboardDto(
        List<ApplicationHealthSummaryDto> applications,
        DoraMetricsDto dora,
        List<TeamActivityEventDto> recentActivity,
        String healthError,
        String doraError,
        String activityError) {
}
