package com.portal.health;

import com.portal.application.Application;
import com.portal.integration.prometheus.PrometheusAdapter;
import com.portal.integration.prometheus.PrometheusConfig;
import com.portal.integration.prometheus.model.DoraMetric;
import com.portal.integration.prometheus.model.DoraMetricType;
import com.portal.integration.prometheus.model.DoraMetricsResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@ApplicationScoped
public class DoraService {

    @Inject
    PrometheusAdapter prometheusAdapter;

    @Inject
    PrometheusConfig prometheusConfig;

    public DoraMetricsDto getDoraMetrics(Long teamId, Long appId, String timeRange) {
        Application app = requireTeamApplication(teamId, appId);

        String effectiveRange = (timeRange == null || timeRange.isBlank())
                ? prometheusConfig.doraDefaultRange()
                : timeRange;

        DoraMetricsResult result = prometheusAdapter.getDoraMetrics(app.name, effectiveRange);

        List<DoraMetricDto> metricDtos = result.metrics().stream()
                .map(this::toDto)
                .toList();

        return new DoraMetricsDto(metricDtos, effectiveRange, result.hasData());
    }

    private DoraMetricDto toDto(DoraMetric metric) {
        double percentageChange = calculatePercentageChange(metric.currentValue(), metric.previousValue());
        String unit = resolveUnit(metric);

        List<TimeSeriesPointDto> timeSeries = metric.timeSeries().stream()
                .map(p -> new TimeSeriesPointDto(p.timestamp(), p.value()))
                .toList();

        return new DoraMetricDto(
                metric.type(),
                metric.currentValue(),
                metric.previousValue(),
                metric.trend(),
                percentageChange,
                unit,
                timeSeries
        );
    }

    double calculatePercentageChange(double current, double previous) {
        if (previous == 0 && current == 0) {
            return 0.0;
        }
        if (previous == 0) {
            return 100.0;
        }
        double change = ((current - previous) / Math.abs(previous)) * 100;
        return Math.max(-100.0, Math.min(change, 999.0));
    }

    /**
     * Resolves display unit: time-based metrics use "h" if >= 1 hour, "m" otherwise.
     */
    String resolveUnit(DoraMetric metric) {
        return switch (metric.type()) {
            case DEPLOYMENT_FREQUENCY -> "/wk";
            case CHANGE_FAILURE_RATE -> "%";
            case LEAD_TIME -> metric.currentValue() >= 1.0 ? "h" : "m";
            case MTTR -> metric.currentValue() >= 60.0 ? "h" : "m";
        };
    }

    private Application requireTeamApplication(Long teamId, Long appId) {
        Application app = Application.findById(appId);
        if (app == null || !app.teamId.equals(teamId)) {
            throw new NotFoundException();
        }
        return app;
    }
}
