package com.portal.integration.argocd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portal.environment.Environment;
import com.portal.environment.EnvironmentStatusDto;
import com.portal.environment.PortalEnvironmentStatus;
import com.portal.integration.PortalIntegrationException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArgoCdRestAdapterTest {

    private ArgoCdRestAdapter adapter;
    private ArgoCdRestClient mockClient;
    private ArgoCdConfig mockConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        mockClient = mock(ArgoCdRestClient.class);
        mockConfig = mock(ArgoCdConfig.class);
        when(mockConfig.url()).thenReturn("https://argocd.example.com");
        when(mockConfig.token()).thenReturn(Optional.of("test-token"));

        adapter = new ArgoCdRestAdapter();
        Field clientField = ArgoCdRestAdapter.class.getDeclaredField("restClient");
        clientField.setAccessible(true);
        clientField.set(adapter, mockClient);
        Field configField = ArgoCdRestAdapter.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(adapter, mockConfig);
    }

    @Test
    void healthySyncedReturnsHealthyStatus() {
        JsonNode response = buildArgoCdResponse("Synced", "Healthy",
                "registry.example.com/team/my-app:v1.0.0", "2026-04-05T10:30:00Z");
        when(mockClient.getApplication(eq("my-app-run-dev"), anyString()))
                .thenReturn(response);

        Environment env = buildEnv("dev", 0);
        List<EnvironmentStatusDto> results =
                adapter.getEnvironmentStatuses("my-app", List.of(env));

        assertEquals(1, results.size());
        EnvironmentStatusDto dto = results.get(0);
        assertEquals(PortalEnvironmentStatus.HEALTHY, dto.status());
        assertEquals("dev", dto.environmentName());
        assertEquals("v1.0.0", dto.deployedVersion());
        assertNotNull(dto.lastDeployedAt());
        assertEquals("my-app-run-dev", dto.argocdAppName());
        assertEquals("https://argocd.example.com/applications/my-app-run-dev", dto.argocdDeepLink());
        assertNull(dto.grafanaDeepLink());
    }

    @Test
    void syncedDegradedReturnsUnhealthyStatus() {
        JsonNode response = buildArgoCdResponse("Synced", "Degraded",
                "registry/app:v2.0.0", null);
        when(mockClient.getApplication(eq("my-app-run-qa"), anyString()))
                .thenReturn(response);

        Environment env = buildEnv("qa", 1);
        List<EnvironmentStatusDto> results =
                adapter.getEnvironmentStatuses("my-app", List.of(env));

        assertEquals(PortalEnvironmentStatus.UNHEALTHY, results.get(0).status());
    }

    @Test
    void syncedMissingReturnsUnhealthyStatus() {
        JsonNode response = buildArgoCdResponse("Synced", "Missing", null, null);
        when(mockClient.getApplication(eq("my-app-run-prod"), anyString()))
                .thenReturn(response);

        Environment env = buildEnv("prod", 2);
        List<EnvironmentStatusDto> results =
                adapter.getEnvironmentStatuses("my-app", List.of(env));

        assertEquals(PortalEnvironmentStatus.UNHEALTHY, results.get(0).status());
    }

    @Test
    void outOfSyncReturnsDeployingStatus() {
        JsonNode response = buildArgoCdResponse("OutOfSync", "Healthy",
                "registry/app:v3.0.0", null);
        when(mockClient.getApplication(eq("my-app-run-dev"), anyString()))
                .thenReturn(response);

        Environment env = buildEnv("dev", 0);
        List<EnvironmentStatusDto> results =
                adapter.getEnvironmentStatuses("my-app", List.of(env));

        assertEquals(PortalEnvironmentStatus.DEPLOYING, results.get(0).status());
    }

    @Test
    void progressingHealthReturnsDeployingStatus() {
        JsonNode response = buildArgoCdResponse("Synced", "Progressing",
                "registry/app:v1.0.0", null);
        when(mockClient.getApplication(eq("my-app-run-dev"), anyString()))
                .thenReturn(response);

        Environment env = buildEnv("dev", 0);
        List<EnvironmentStatusDto> results =
                adapter.getEnvironmentStatuses("my-app", List.of(env));

        assertEquals(PortalEnvironmentStatus.DEPLOYING, results.get(0).status());
    }

    @Test
    void suspendedHealthReturnsDeployingStatus() {
        JsonNode response = buildArgoCdResponse("Synced", "Suspended",
                "registry/app:v1.0.0", null);
        when(mockClient.getApplication(eq("my-app-run-dev"), anyString()))
                .thenReturn(response);

        Environment env = buildEnv("dev", 0);
        List<EnvironmentStatusDto> results =
                adapter.getEnvironmentStatuses("my-app", List.of(env));

        assertEquals(PortalEnvironmentStatus.DEPLOYING, results.get(0).status());
    }

    @Test
    void notFoundReturnsNotDeployed() {
        when(mockClient.getApplication(anyString(), anyString()))
                .thenThrow(new WebApplicationException(404));

        Environment env = buildEnv("dev", 0);
        List<EnvironmentStatusDto> results =
                adapter.getEnvironmentStatuses("my-app", List.of(env));

        EnvironmentStatusDto dto = results.get(0);
        assertEquals(PortalEnvironmentStatus.NOT_DEPLOYED, dto.status());
        assertNull(dto.deployedVersion());
        assertNull(dto.lastDeployedAt());
        assertNull(dto.grafanaDeepLink());
    }

    @Test
    void unknownUnknownReturnsNotDeployed() {
        JsonNode response = buildArgoCdResponse("Unknown", "Unknown", null, null);
        when(mockClient.getApplication(eq("my-app-run-dev"), anyString()))
                .thenReturn(response);

        Environment env = buildEnv("dev", 0);
        List<EnvironmentStatusDto> results =
                adapter.getEnvironmentStatuses("my-app", List.of(env));

        assertEquals(PortalEnvironmentStatus.NOT_DEPLOYED, results.get(0).status());
    }

    @Test
    void connectionFailureThrowsPortalIntegrationException() {
        when(mockClient.getApplication(anyString(), anyString()))
                .thenThrow(new ProcessingException("Connection refused"));

        Environment env = buildEnv("dev", 0);
        PortalIntegrationException ex = assertThrows(PortalIntegrationException.class,
                () -> adapter.getEnvironmentStatuses("my-app", List.of(env)));

        assertEquals("argocd", ex.getSystem());
        assertEquals("getEnvironmentStatus", ex.getOperation());
        assertTrue(ex.getDeepLink().contains("argocd.example.com"));
        assertTrue(ex.getMessage().contains("unreachable"));
    }

    @Test
    void nonNotFoundHttpErrorThrowsPortalIntegrationException() {
        when(mockClient.getApplication(anyString(), anyString()))
                .thenThrow(new WebApplicationException(500));

        Environment env = buildEnv("dev", 0);
        PortalIntegrationException ex = assertThrows(PortalIntegrationException.class,
                () -> adapter.getEnvironmentStatuses("my-app", List.of(env)));

        assertEquals("argocd", ex.getSystem());
        assertTrue(ex.getMessage().contains("returned an error"));
    }

    @Test
    void deployedVersionExtractedFromImageTag() {
        JsonNode response = buildArgoCdResponse("Synced", "Healthy",
                "registry.example.com/team/payment-svc:v4.5.6-rc1", null);
        when(mockClient.getApplication(eq("app-run-dev"), anyString()))
                .thenReturn(response);

        Environment env = buildEnv("dev", 0);
        List<EnvironmentStatusDto> results =
                adapter.getEnvironmentStatuses("app", List.of(env));

        assertEquals("v4.5.6-rc1", results.get(0).deployedVersion());
    }

    @Test
    void noImagesReturnsNullVersion() {
        JsonNode response = buildArgoCdResponse("Synced", "Healthy", null, null);
        when(mockClient.getApplication(eq("app-run-dev"), anyString()))
                .thenReturn(response);

        Environment env = buildEnv("dev", 0);
        List<EnvironmentStatusDto> results =
                adapter.getEnvironmentStatuses("app", List.of(env));

        assertNull(results.get(0).deployedVersion());
    }

    @Test
    void deepLinkUrlConstructedCorrectly() {
        JsonNode response = buildArgoCdResponse("Synced", "Healthy",
                "registry/app:v1.0.0", null);
        when(mockClient.getApplication(eq("my-app-run-staging"), anyString()))
                .thenReturn(response);

        Environment env = buildEnv("staging", 1);
        List<EnvironmentStatusDto> results =
                adapter.getEnvironmentStatuses("my-app", List.of(env));

        assertEquals("https://argocd.example.com/applications/my-app-run-staging",
                results.get(0).argocdDeepLink());
    }

    @Test
    void multipleEnvironmentsQueriedAndAggregated() {
        JsonNode healthyResponse = buildArgoCdResponse("Synced", "Healthy",
                "registry/app:v1.0.0", "2026-04-05T10:30:00Z");
        JsonNode deployingResponse = buildArgoCdResponse("OutOfSync", "Progressing",
                "registry/app:v2.0.0", null);
        JsonNode unknownResponse = buildArgoCdResponse("Unknown", "Unknown", null, null);

        when(mockClient.getApplication(eq("my-app-run-dev"), anyString()))
                .thenReturn(healthyResponse);
        when(mockClient.getApplication(eq("my-app-run-qa"), anyString()))
                .thenReturn(deployingResponse);
        when(mockClient.getApplication(eq("my-app-run-prod"), anyString()))
                .thenReturn(unknownResponse);

        List<Environment> envs = List.of(
                buildEnv("dev", 0),
                buildEnv("qa", 1),
                buildEnv("prod", 2));

        List<EnvironmentStatusDto> results =
                adapter.getEnvironmentStatuses("my-app", envs);

        assertEquals(3, results.size());
        assertEquals(PortalEnvironmentStatus.HEALTHY, results.get(0).status());
        assertEquals(PortalEnvironmentStatus.DEPLOYING, results.get(1).status());
        assertEquals(PortalEnvironmentStatus.NOT_DEPLOYED, results.get(2).status());
    }

    @Test
    void authHeaderUsesConfiguredBearerToken() {
        JsonNode response = buildArgoCdResponse("Synced", "Healthy",
                "registry/app:v1.0.0", null);
        when(mockClient.getApplication(eq("my-app-run-dev"), eq("Bearer test-token")))
                .thenReturn(response);

        Environment env = buildEnv("dev", 0);
        List<EnvironmentStatusDto> results =
                adapter.getEnvironmentStatuses("my-app", List.of(env));

        assertEquals(1, results.size());
        assertEquals(PortalEnvironmentStatus.HEALTHY, results.get(0).status());
    }

    @Test
    void emptyTokenProducesEmptyAuthHeader() {
        when(mockConfig.token()).thenReturn(Optional.empty());

        JsonNode response = buildArgoCdResponse("Synced", "Healthy",
                "registry/app:v1.0.0", null);
        when(mockClient.getApplication(eq("my-app-run-dev"), eq("")))
                .thenReturn(response);

        Environment env = buildEnv("dev", 0);
        List<EnvironmentStatusDto> results =
                adapter.getEnvironmentStatuses("my-app", List.of(env));

        assertEquals(1, results.size());
    }

    @Test
    void translateStatusCoversAllMappings() {
        assertEquals(PortalEnvironmentStatus.HEALTHY,
                ArgoCdRestAdapter.translateStatus("Synced", "Healthy"));
        assertEquals(PortalEnvironmentStatus.UNHEALTHY,
                ArgoCdRestAdapter.translateStatus("Synced", "Degraded"));
        assertEquals(PortalEnvironmentStatus.UNHEALTHY,
                ArgoCdRestAdapter.translateStatus("Synced", "Missing"));
        assertEquals(PortalEnvironmentStatus.DEPLOYING,
                ArgoCdRestAdapter.translateStatus("OutOfSync", "Healthy"));
        assertEquals(PortalEnvironmentStatus.DEPLOYING,
                ArgoCdRestAdapter.translateStatus("OutOfSync", "Progressing"));
        assertEquals(PortalEnvironmentStatus.DEPLOYING,
                ArgoCdRestAdapter.translateStatus("Synced", "Progressing"));
        assertEquals(PortalEnvironmentStatus.DEPLOYING,
                ArgoCdRestAdapter.translateStatus("Synced", "Suspended"));
        assertEquals(PortalEnvironmentStatus.NOT_DEPLOYED,
                ArgoCdRestAdapter.translateStatus("Unknown", "Unknown"));
        assertEquals(PortalEnvironmentStatus.UNHEALTHY,
                ArgoCdRestAdapter.translateStatus("OutOfSync", "Degraded"));
    }

    @Test
    void digestPinnedImageReturnsNullVersion() {
        JsonNode response = buildArgoCdResponse("Synced", "Healthy",
                "registry.example.com/team/app@sha256:abc123def456", null);
        when(mockClient.getApplication(eq("app-run-dev"), anyString()))
                .thenReturn(response);

        Environment env = buildEnv("dev", 0);
        List<EnvironmentStatusDto> results =
                adapter.getEnvironmentStatuses("app", List.of(env));

        assertNull(results.get(0).deployedVersion());
    }

    @Test
    void registryWithPortExtractsCorrectTag() {
        JsonNode response = buildArgoCdResponse("Synced", "Healthy",
                "registry.example.com:5000/team/app:v2.0.0", null);
        when(mockClient.getApplication(eq("app-run-dev"), anyString()))
                .thenReturn(response);

        Environment env = buildEnv("dev", 0);
        List<EnvironmentStatusDto> results =
                adapter.getEnvironmentStatuses("app", List.of(env));

        assertEquals("v2.0.0", results.get(0).deployedVersion());
    }

    @Test
    void malformedFinishedAtDoesNotThrow() {
        ObjectNode root = objectMapper.createObjectNode();
        root.putObject("metadata").put("name", "test-app");
        ObjectNode status = root.putObject("status");
        status.putObject("sync").put("status", "Synced");
        status.putObject("health").put("status", "Healthy");
        status.putObject("operationState").put("finishedAt", "not-a-date");
        status.putObject("summary").putArray("images").add("registry/app:v1.0.0");

        when(mockClient.getApplication(eq("app-run-dev"), anyString()))
                .thenReturn(root);

        Environment env = buildEnv("dev", 0);
        List<EnvironmentStatusDto> results =
                adapter.getEnvironmentStatuses("app", List.of(env));

        assertEquals(PortalEnvironmentStatus.HEALTHY, results.get(0).status());
        assertEquals("v1.0.0", results.get(0).deployedVersion());
        assertNull(results.get(0).lastDeployedAt());
    }

    @Test
    void extractVersionFromImageHandlesEdgeCases() {
        assertEquals(Optional.of("v1.0.0"),
                ArgoCdRestAdapter.extractVersionFromImage("registry/app:v1.0.0"));
        assertEquals(Optional.of("v2.0.0"),
                ArgoCdRestAdapter.extractVersionFromImage("registry.io:5000/team/app:v2.0.0"));
        assertEquals(Optional.empty(),
                ArgoCdRestAdapter.extractVersionFromImage("repo/image@sha256:abc123"));
        assertEquals(Optional.empty(),
                ArgoCdRestAdapter.extractVersionFromImage(null));
        assertEquals(Optional.empty(),
                ArgoCdRestAdapter.extractVersionFromImage(""));
        assertEquals(Optional.empty(),
                ArgoCdRestAdapter.extractVersionFromImage("registry/app"));
    }

    @Test
    void argoAppNameFollowsNamingConvention() {
        JsonNode response = buildArgoCdResponse("Synced", "Healthy",
                "registry/app:v1.0.0", null);
        when(mockClient.getApplication(eq("payment-svc-run-dev"), anyString()))
                .thenReturn(response);

        Environment env = buildEnv("DEV", 0);
        List<EnvironmentStatusDto> results =
                adapter.getEnvironmentStatuses("payment-svc", List.of(env));

        assertEquals("payment-svc-run-dev", results.get(0).argocdAppName());
    }

    private JsonNode buildArgoCdResponse(String syncStatus, String healthStatus,
            String image, String finishedAt) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode metadata = root.putObject("metadata");
        metadata.put("name", "test-app");

        ObjectNode status = root.putObject("status");
        status.putObject("sync").put("status", syncStatus);
        status.putObject("health").put("status", healthStatus);

        if (finishedAt != null) {
            status.putObject("operationState").put("finishedAt", finishedAt);
        }

        if (image != null) {
            ObjectNode summary = status.putObject("summary");
            ArrayNode images = summary.putArray("images");
            images.add(image);
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
