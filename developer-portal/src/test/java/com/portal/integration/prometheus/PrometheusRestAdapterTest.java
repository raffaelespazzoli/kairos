package com.portal.integration.prometheus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.integration.PortalIntegrationException;
import com.portal.integration.prometheus.model.GoldenSignalType;
import com.portal.integration.prometheus.model.HealthSignalsResult;
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
}
