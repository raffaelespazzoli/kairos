package com.portal.health;

import com.portal.integration.prometheus.model.DoraMetricType;
import com.portal.integration.prometheus.model.TrendDirection;

import java.util.List;

public record DoraMetricDto(
        DoraMetricType type,
        double currentValue,
        double previousValue,
        TrendDirection trend,
        double percentageChange,
        String unit,
        List<TimeSeriesPointDto> timeSeries
) {
}
