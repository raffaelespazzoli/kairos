package com.portal.health;

import java.util.List;

public record DoraMetricsDto(List<DoraMetricDto> metrics, String timeRange, boolean hasData) {
}
