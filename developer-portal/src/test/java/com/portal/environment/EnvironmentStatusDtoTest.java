package com.portal.environment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentStatusDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void jsonSerializationContainsAllFields() throws Exception {
        Instant deployedAt = Instant.parse("2026-04-05T10:30:00Z");
        EnvironmentStatusDto dto = new EnvironmentStatusDto(
                "dev",
                PortalEnvironmentStatus.HEALTHY,
                "v1.2.3",
                deployedAt,
                "my-app-run-dev",
                "https://argocd.example.com/applications/my-app-run-dev",
                null);

        String json = objectMapper.writeValueAsString(dto);
        JsonNode node = objectMapper.readTree(json);

        assertEquals("dev", node.get("environmentName").asText());
        assertEquals("HEALTHY", node.get("status").asText());
        assertEquals("v1.2.3", node.get("deployedVersion").asText());
        String lastDeployedAtValue = node.get("lastDeployedAt").asText();
        assertNotNull(lastDeployedAtValue);
        assertTrue(lastDeployedAtValue.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*"),
                "lastDeployedAt should be ISO-8601 format, got: " + lastDeployedAtValue);
        assertEquals("my-app-run-dev", node.get("argocdAppName").asText());
        assertEquals("https://argocd.example.com/applications/my-app-run-dev",
                node.get("argocdDeepLink").asText());
        assertTrue(node.has("grafanaDeepLink"), "grafanaDeepLink field should be present");
        assertTrue(node.get("grafanaDeepLink").isNull());
    }

    @Test
    void statusSerializesAsString() throws Exception {
        EnvironmentStatusDto dto = new EnvironmentStatusDto(
                "qa", PortalEnvironmentStatus.DEPLOYING,
                null, null, "app-run-qa", "https://argocd/applications/app-run-qa", null);

        String json = objectMapper.writeValueAsString(dto);
        JsonNode node = objectMapper.readTree(json);

        assertEquals("DEPLOYING", node.get("status").asText());
        assertTrue(node.get("status").isTextual());
    }

    @Test
    void nullFieldsSerializeAsJsonNull() throws Exception {
        EnvironmentStatusDto dto = new EnvironmentStatusDto(
                "prod", PortalEnvironmentStatus.NOT_DEPLOYED,
                null, null, "app-run-prod", "https://argocd/applications/app-run-prod", null);

        String json = objectMapper.writeValueAsString(dto);
        JsonNode node = objectMapper.readTree(json);

        assertTrue(node.get("deployedVersion").isNull());
        assertTrue(node.get("lastDeployedAt").isNull());
        assertTrue(node.get("grafanaDeepLink").isNull());
    }

    @Test
    void allStatusValuesSerializeCorrectly() throws Exception {
        for (PortalEnvironmentStatus status : PortalEnvironmentStatus.values()) {
            EnvironmentStatusDto dto = new EnvironmentStatusDto(
                    "env", status, null, null, "app-run-env", "https://argocd/app", null);

            String json = objectMapper.writeValueAsString(dto);
            JsonNode node = objectMapper.readTree(json);

            assertEquals(status.name(), node.get("status").asText());
        }
    }
}
