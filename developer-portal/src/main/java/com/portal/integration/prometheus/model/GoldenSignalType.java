package com.portal.integration.prometheus.model;

public enum GoldenSignalType {
    LATENCY_P50,
    LATENCY_P95,
    LATENCY_P99,
    TRAFFIC_RATE,
    ERROR_RATE,
    SATURATION_CPU,
    SATURATION_MEMORY
}
