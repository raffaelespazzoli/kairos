package com.portal.integration.prometheus.model;

import java.util.List;

public record DoraMetricsResult(List<DoraMetric> metrics, boolean hasData) {
}
