package com.portal.integration.prometheus;

import com.portal.integration.prometheus.model.GoldenSignal;
import com.portal.integration.prometheus.model.GoldenSignalType;
import com.portal.integration.prometheus.model.HealthSignalsResult;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dev-mode adapter returning deterministic mock golden signal data.
 * Mirrors the DevArgoCdAdapter pattern: first distinct namespace → Healthy,
 * second → Degraded (elevated saturation), rest → Healthy.
 * Namespace call order is tracked so repeated calls for the same namespace
 * return a stable result.
 */
@ApplicationScoped
@IfBuildProperty(name = "portal.prometheus.provider", stringValue = "dev")
public class DevPrometheusAdapter implements PrometheusAdapter {

    private final AtomicInteger counter = new AtomicInteger(0);
    private final ConcurrentHashMap<String, Integer> namespaceIndex = new ConcurrentHashMap<>();

    @Override
    public HealthSignalsResult getGoldenSignals(String namespace) {
        int index = namespaceIndex.computeIfAbsent(namespace, k -> counter.getAndIncrement());

        if (index == 1) {
            return degradedSignals();
        }
        return healthySignals();
    }

    private HealthSignalsResult healthySignals() {
        return new HealthSignalsResult(List.of(
                new GoldenSignal("Latency P50", 45.0, "ms", GoldenSignalType.LATENCY_P50),
                new GoldenSignal("Latency P95", 245.0, "ms", GoldenSignalType.LATENCY_P95),
                new GoldenSignal("Latency P99", 890.0, "ms", GoldenSignalType.LATENCY_P99),
                new GoldenSignal("Traffic Rate", 42.5, "req/s", GoldenSignalType.TRAFFIC_RATE),
                new GoldenSignal("Error Rate", 0.3, "%", GoldenSignalType.ERROR_RATE),
                new GoldenSignal("CPU Saturation", 45.0, "%", GoldenSignalType.SATURATION_CPU),
                new GoldenSignal("Memory Saturation", 62.0, "%", GoldenSignalType.SATURATION_MEMORY)
        ), true);
    }

    private HealthSignalsResult degradedSignals() {
        return new HealthSignalsResult(List.of(
                new GoldenSignal("Latency P50", 80.0, "ms", GoldenSignalType.LATENCY_P50),
                new GoldenSignal("Latency P95", 400.0, "ms", GoldenSignalType.LATENCY_P95),
                new GoldenSignal("Latency P99", 1200.0, "ms", GoldenSignalType.LATENCY_P99),
                new GoldenSignal("Traffic Rate", 30.0, "req/s", GoldenSignalType.TRAFFIC_RATE),
                new GoldenSignal("Error Rate", 1.0, "%", GoldenSignalType.ERROR_RATE),
                new GoldenSignal("CPU Saturation", 92.0, "%", GoldenSignalType.SATURATION_CPU),
                new GoldenSignal("Memory Saturation", 65.0, "%", GoldenSignalType.SATURATION_MEMORY)
        ), true);
    }
}
