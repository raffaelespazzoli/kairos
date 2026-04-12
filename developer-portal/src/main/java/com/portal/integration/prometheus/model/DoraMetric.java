package com.portal.integration.prometheus.model;

import java.util.List;

public record DoraMetric(
        DoraMetricType type,
        double currentValue,
        double previousValue,
        TrendDirection trend,
        String unit,
        List<TimeSeriesPoint> timeSeries
) {
}
