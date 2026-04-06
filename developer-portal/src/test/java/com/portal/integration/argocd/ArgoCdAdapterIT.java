package com.portal.integration.argocd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portal.environment.Environment;
import com.portal.environment.EnvironmentStatusDto;
import com.portal.environment.PortalEnvironmentStatus;
import com.portal.integration.PortalIntegrationException;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
class ArgoCdAdapterIT {

    @InjectMock
    @RestClient
    ArgoCdRestClient restClient;

    @Inject
    ArgoCdAdapter adapter;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void adapterTranslatesHealthyResponse() {
        JsonNode response = buildArgoCdResponse("Synced", "Healthy",
                "registry.example.com/app:v2.0.0", "2026-04-05T10:30:00Z");
        when(restClient.getApplication(eq("test-app-run-dev"), anyString()))
                .thenReturn(response);

        Environment env = buildEnv("dev", 0);
        List<EnvironmentStatusDto> results =
                adapter.getEnvironmentStatuses("test-app", List.of(env));

        assertEquals(1, results.size());
        EnvironmentStatusDto dto = results.get(0);
        assertEquals(PortalEnvironmentStatus.HEALTHY, dto.status());
        assertEquals("v2.0.0", dto.deployedVersion());
        assertEquals("dev", dto.environmentName());
        assertEquals("test-app-run-dev", dto.argocdAppName());
        assertTrue(dto.argocdDeepLink().contains("test-app-run-dev"));
    }

    @Test
    void adapterTranslatesDeployingResponse() {
        JsonNode response = buildArgoCdResponse("OutOfSync", "Progressing",
                "registry/app:v3.0.0", null);
        when(restClient.getApplication(eq("test-app-run-qa"), anyString()))
                .thenReturn(response);

        Environment env = buildEnv("qa", 1);
        List<EnvironmentStatusDto> results =
                adapter.getEnvironmentStatuses("test-app", List.of(env));

        assertEquals(PortalEnvironmentStatus.DEPLOYING, results.get(0).status());
    }

    @Test
    void adapterHandles404AsNotDeployed() {
        when(restClient.getApplication(anyString(), anyString()))
                .thenThrow(new WebApplicationException(404));

        Environment env = buildEnv("dev", 0);
        List<EnvironmentStatusDto> results =
                adapter.getEnvironmentStatuses("test-app", List.of(env));

        assertEquals(PortalEnvironmentStatus.NOT_DEPLOYED, results.get(0).status());
    }

    @Test
    void adapterPropagatesConnectionError() {
        when(restClient.getApplication(anyString(), anyString()))
                .thenThrow(new ProcessingException("Connection refused"));

        Environment env = buildEnv("dev", 0);
        PortalIntegrationException ex = assertThrows(PortalIntegrationException.class,
                () -> adapter.getEnvironmentStatuses("test-app", List.of(env)));

        assertEquals("argocd", ex.getSystem());
    }

    private JsonNode buildArgoCdResponse(String syncStatus, String healthStatus,
            String image, String finishedAt) {
        ObjectNode root = objectMapper.createObjectNode();
        root.putObject("metadata").put("name", "test-app");

        ObjectNode status = root.putObject("status");
        status.putObject("sync").put("status", syncStatus);
        status.putObject("health").put("status", healthStatus);

        if (finishedAt != null) {
            status.putObject("operationState").put("finishedAt", finishedAt);
        }

        if (image != null) {
            status.putObject("summary").putArray("images").add(image);
        }

        return root;
    }

    private Environment buildEnv(String name, int order) {
        Environment env = new Environment();
        env.name = name;
        env.promotionOrder = order;
        return env;
    }
}
