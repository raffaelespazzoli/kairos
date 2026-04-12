package com.portal.integration.prometheus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.integration.PortalIntegrationException;
import com.portal.integration.prometheus.model.DoraMetricType;
import com.portal.integration.prometheus.model.DoraMetricsResult;
import com.portal.integration.prometheus.model.GoldenSignalType;
import com.portal.integration.prometheus.model.HealthSignalsResult;
import com.portal.integration.prometheus.model.TrendDirection;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;

@QuarkusTest
class PrometheusRestAdapterTest {

    @Inject
    PrometheusAdapter prometheusAdapter;

    @InjectMock
    @RestClient
    PrometheusRestClient restClient;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonNode vectorResponse(String value) throws Exception {
        return MAPPER.readTree("""
                {
                  "status": "success",
                  "data": {
                    "resultType": "vector",
                    "result": [
                      {
                        "metric": {"namespace": "test-ns"},
                        "value": [1712345678.123, "%s"]
                      }
                    ]
                  }
                }
                """.formatted(value));
    }

    private JsonNode emptyResponse() throws Exception {
        return MAPPER.readTree("""
                {
                  "status": "success",
                  "data": {
                    "resultType": "vector",
                    "result": []
                  }
                }
                """);
    }

    @Test
    void goldenSignalsParsedCorrectly() throws Exception {
        when(restClient.query(anyString())).thenReturn(vectorResponse("0.245"));

        HealthSignalsResult result = prometheusAdapter.getGoldenSignals("test-namespace");

        assertTrue(result.hasData());
        assertEquals(7, result.signals().size());

        assertEquals(GoldenSignalType.LATENCY_P50, result.signals().get(0).type());
        assertEquals(GoldenSignalType.LATENCY_P95, result.signals().get(1).type());
        assertEquals(GoldenSignalType.LATENCY_P99, result.signals().get(2).type());
        assertEquals(GoldenSignalType.TRAFFIC_RATE, result.signals().get(3).type());
        assertEquals(GoldenSignalType.ERROR_RATE, result.signals().get(4).type());
        assertEquals(GoldenSignalType.SATURATION_CPU, result.signals().get(5).type());
        assertEquals(GoldenSignalType.SATURATION_MEMORY, result.signals().get(6).type());

        assertEquals("ms", result.signals().get(0).unit());
        assertEquals("req/s", result.signals().get(3).unit());
        assertEquals("%", result.signals().get(4).unit());
    }

    @Test
    void latencyValuesMultipliedToMilliseconds() throws Exception {
        // Prometheus returns latency in seconds (0.245s = 245ms)
        when(restClient.query(anyString())).thenReturn(vectorResponse("0.245"));

        HealthSignalsResult result = prometheusAdapter.getGoldenSignals("test-ns");

        assertEquals(245.0, result.signals().get(0).value(), 0.001);
        assertEquals(245.0, result.signals().get(1).value(), 0.001);
        assertEquals(245.0, result.signals().get(2).value(), 0.001);
    }

    @Test
    void emptyResultSetReturnsNoData() throws Exception {
        when(restClient.query(anyString())).thenReturn(emptyResponse());

        HealthSignalsResult result = prometheusAdapter.getGoldenSignals("empty-ns");

        assertFalse(result.hasData());
        assertEquals(7, result.signals().size());
        result.signals().forEach(s -> assertEquals(0.0, s.value()));
    }

    @Test
    void prometheusUnreachableThrowsIntegrationException() {
        when(restClient.query(anyString()))
                .thenThrow(new RuntimeException("Connection refused"));

        PortalIntegrationException ex = assertThrows(PortalIntegrationException.class,
                () -> prometheusAdapter.getGoldenSignals("test-ns"));

        assertEquals("prometheus", ex.getSystem());
        assertEquals("getGoldenSignals", ex.getOperation());
        assertTrue(ex.getMessage().contains("unreachable"));
    }

    @Test
    void badQueryReturnsZeroGracefully() throws Exception {
        Response mockResponse = Response.status(400).build();
        when(restClient.query(anyString()))
                .thenThrow(new WebApplicationException(mockResponse));

        HealthSignalsResult result = prometheusAdapter.getGoldenSignals("test-ns");

        assertFalse(result.hasData());
        assertEquals(7, result.signals().size());
        result.signals().forEach(s -> assertEquals(0.0, s.value()));
    }

    @Test
    void unprocessableQueryReturnsZeroGracefully() throws Exception {
        Response mockResponse = Response.status(422).build();
        when(restClient.query(anyString()))
                .thenThrow(new WebApplicationException(mockResponse));

        HealthSignalsResult result = prometheusAdapter.getGoldenSignals("test-ns");

        assertFalse(result.hasData());
    }

    @Test
    void nonClientErrorThrowsIntegrationException() {
        Response mockResponse = Response.status(503).build();
        when(restClient.query(anyString()))
                .thenThrow(new WebApplicationException(mockResponse));

        assertThrows(PortalIntegrationException.class,
                () -> prometheusAdapter.getGoldenSignals("test-ns"));
    }

    @Test
    void namespaceAndIntervalSubstitutedInQueries() throws Exception {
        when(restClient.query(contains("my-app-dev"))).thenReturn(vectorResponse("1.0"));

        HealthSignalsResult result = prometheusAdapter.getGoldenSignals("my-app-dev");

        assertTrue(result.hasData());
    }

    @Test
    void nanValueTreatedAsZeroButStillHasData() throws Exception {
        when(restClient.query(anyString())).thenReturn(vectorResponse("NaN"));

        HealthSignalsResult result = prometheusAdapter.getGoldenSignals("test-ns");

        assertTrue(result.hasData());
        result.signals().forEach(s -> assertEquals(0.0, s.value()));
    }

    @Test
    void zeroValuedNonEmptyVectorStillHasData() throws Exception {
        when(restClient.query(anyString())).thenReturn(vectorResponse("0"));

        HealthSignalsResult result = prometheusAdapter.getGoldenSignals("idle-ns");

        assertTrue(result.hasData());
        assertEquals(7, result.signals().size());
        result.signals().forEach(s -> assertEquals(0.0, s.value()));
    }

    // ── DORA Metrics Tests ──────────────────────────────────────────────

    private JsonNode rangeResponse(double... values) throws Exception {
        StringBuilder valuesJson = new StringBuilder("[");
        long baseTime = 1712345678L;
        for (int i = 0; i < values.length; i++) {
            if (i > 0) valuesJson.append(",");
            valuesJson.append("[").append(baseTime + i * 86400L)
                    .append(",\"").append(values[i]).append("\"]");
        }
        valuesJson.append("]");

        return MAPPER.readTree("""
                {
                  "status": "success",
                  "data": {
                    "resultType": "matrix",
                    "result": [
                      {
                        "metric": {"application": "test-app"},
                        "values": %s
                      }
                    ]
                  }
                }
                """.formatted(valuesJson));
    }

    private JsonNode emptyRangeResponse() throws Exception {
        return MAPPER.readTree("""
                {
                  "status": "success",
                  "data": {
                    "resultType": "matrix",
                    "result": []
                  }
                }
                """);
    }

    private void stubDoraQueries(String instantValue, double... rangeValues) throws Exception {
        when(restClient.query(anyString())).thenReturn(vectorResponse(instantValue));
        when(restClient.queryRange(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(rangeResponse(rangeValues));
    }

    @Test
    void doraMetricsParsedCorrectly() throws Exception {
        double[] series = new double[30];
        for (int i = 0; i < 30; i++) series[i] = 4.0 + i * 0.01;
        stubDoraQueries("4.2", series);

        DoraMetricsResult result = prometheusAdapter.getDoraMetrics("my-app", "30d");

        assertTrue(result.hasData());
        assertEquals(4, result.metrics().size());
        assertEquals(DoraMetricType.DEPLOYMENT_FREQUENCY, result.metrics().get(0).type());
        assertEquals(DoraMetricType.LEAD_TIME, result.metrics().get(1).type());
        assertEquals(DoraMetricType.CHANGE_FAILURE_RATE, result.metrics().get(2).type());
        assertEquals(DoraMetricType.MTTR, result.metrics().get(3).type());
    }

    @Test
    void doraPlaceholderSubstitution() throws Exception {
        double[] series = new double[30];
        for (int i = 0; i < 30; i++) series[i] = 1.0;
        when(restClient.query(contains("my-app"))).thenReturn(vectorResponse("1.0"));
        when(restClient.queryRange(contains("my-app"), anyString(), anyString(), anyString()))
                .thenReturn(rangeResponse(series));

        DoraMetricsResult result = prometheusAdapter.getDoraMetrics("my-app", "30d");

        assertTrue(result.hasData());
    }

    @Test
    void doraTrendImprovingForHigherIsBetter() {
        PrometheusRestAdapter adapter = new PrometheusRestAdapter();
        TrendDirection trend = adapter.calculateTrend(5.0, 3.0, true);
        assertEquals(TrendDirection.IMPROVING, trend);
    }

    @Test
    void doraTrendDecliningForHigherIsBetter() {
        PrometheusRestAdapter adapter = new PrometheusRestAdapter();
        TrendDirection trend = adapter.calculateTrend(3.0, 5.0, true);
        assertEquals(TrendDirection.DECLINING, trend);
    }

    @Test
    void doraTrendImprovingForLowerIsBetter() {
        PrometheusRestAdapter adapter = new PrometheusRestAdapter();
        TrendDirection trend = adapter.calculateTrend(2.0, 4.0, false);
        assertEquals(TrendDirection.IMPROVING, trend);
    }

    @Test
    void doraTrendDecliningForLowerIsBetter() {
        PrometheusRestAdapter adapter = new PrometheusRestAdapter();
        TrendDirection trend = adapter.calculateTrend(4.0, 2.0, false);
        assertEquals(TrendDirection.DECLINING, trend);
    }

    @Test
    void doraTrendStableWithinThreshold() {
        PrometheusRestAdapter adapter = new PrometheusRestAdapter();
        TrendDirection trend = adapter.calculateTrend(4.1, 4.0, true);
        assertEquals(TrendDirection.STABLE, trend);
    }

    @Test
    void doraTrendBothZeroIsStable() {
        PrometheusRestAdapter adapter = new PrometheusRestAdapter();
        TrendDirection trend = adapter.calculateTrend(0, 0, true);
        assertEquals(TrendDirection.STABLE, trend);
    }

    @Test
    void doraEmptyResultSetsReturnNoData() throws Exception {
        when(restClient.query(anyString())).thenReturn(emptyResponse());
        when(restClient.queryRange(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(emptyRangeResponse());

        DoraMetricsResult result = prometheusAdapter.getDoraMetrics("empty-app", "30d");

        assertFalse(result.hasData());
        assertEquals(4, result.metrics().size());
        result.metrics().forEach(m -> assertTrue(m.timeSeries().isEmpty()));
    }

    @Test
    void doraPrometheusUnreachableThrowsIntegrationException() {
        when(restClient.query(anyString()))
                .thenThrow(new RuntimeException("Connection refused"));

        PortalIntegrationException ex = assertThrows(PortalIntegrationException.class,
                () -> prometheusAdapter.getDoraMetrics("test-app", "30d"));

        assertEquals("prometheus", ex.getSystem());
        assertEquals("getDoraMetrics", ex.getOperation());
        assertTrue(ex.getMessage().contains("unreachable"));
    }

    @Test
    void doraBadQueryReturnsZeroGracefully() throws Exception {
        Response mockResponse = Response.status(400).build();
        when(restClient.query(anyString()))
                .thenThrow(new WebApplicationException(mockResponse));
        when(restClient.queryRange(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new WebApplicationException(mockResponse));

        DoraMetricsResult result = prometheusAdapter.getDoraMetrics("test-app", "30d");

        assertFalse(result.hasData());
        assertEquals(4, result.metrics().size());
    }

    @Test
    void doraRangeQueryParsingWithNanValues() throws Exception {
        JsonNode nanResponse = MAPPER.readTree("""
                {
                  "status": "success",
                  "data": {
                    "resultType": "matrix",
                    "result": [
                      {
                        "metric": {"application": "test-app"},
                        "values": [
                          [1712345678, "4.2"],
                          [1712432078, "NaN"],
                          [1712518478, "3.8"]
                        ]
                      }
                    ]
                  }
                }
                """);

        PrometheusRestAdapter adapter = new PrometheusRestAdapter();
        var points = adapter.parseMatrixValues(nanResponse);

        assertEquals(2, points.size());
        assertEquals(4.2, points.get(0).value(), 0.001);
        assertEquals(3.8, points.get(1).value(), 0.001);
    }

    @Test
    void parseDurationSingleCharFallsBackToDefault() {
        PrometheusRestAdapter adapter = new PrometheusRestAdapter();
        long result = adapter.parseDurationToSeconds("d");
        assertEquals(30L * 24 * 3600, result);
    }

    @Test
    void parseDurationNonNumericPrefixFallsBackToDefault() {
        PrometheusRestAdapter adapter = new PrometheusRestAdapter();
        long result = adapter.parseDurationToSeconds("abcd");
        assertEquals(30L * 24 * 3600, result);
    }

    @Test
    void parseDurationNullFallsBackToDefault() {
        PrometheusRestAdapter adapter = new PrometheusRestAdapter();
        long result = adapter.parseDurationToSeconds(null);
        assertEquals(30L * 24 * 3600, result);
    }

    @Test
    void doraPerMetricInsufficientDataZeroedWhenFewPoints() throws Exception {
        double[] fewPoints = new double[] { 1.0, 2.0, 3.0 };
        when(restClient.query(anyString())).thenReturn(vectorResponse("5.0"));
        when(restClient.queryRange(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(rangeResponse(fewPoints));

        DoraMetricsResult result = prometheusAdapter.getDoraMetrics("test-app", "30d");

        assertFalse(result.hasData());
        result.metrics().forEach(m -> {
            assertEquals(0.0, m.currentValue());
            assertEquals(0.0, m.previousValue());
            assertEquals(TrendDirection.STABLE, m.trend());
        });
    }
}
