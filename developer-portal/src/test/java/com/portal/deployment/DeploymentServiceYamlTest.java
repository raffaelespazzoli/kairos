package com.portal.deployment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeploymentServiceYamlTest {

    private DeploymentService service;

    @BeforeEach
    void setUp() {
        service = new DeploymentService();
    }

    @Test
    void updateImageTagUpdatesTagValue() {
        String yaml = """
                image:
                  repository: registry.example.com/team/orders-api
                  tag: v1.4.1
                  pullPolicy: IfNotPresent
                replicaCount: 2
                """;

        String result = service.updateImageTag(yaml, "v1.4.2");

        assertTrue(result.contains("tag: v1.4.2"), "tag should be updated to v1.4.2");
        assertFalse(result.contains("v1.4.1"), "old tag should not be present");
    }

    @Test
    void updateImageTagPreservesOtherKeys() {
        String yaml = """
                image:
                  repository: registry.example.com/team/orders-api
                  tag: v1.0.0
                  pullPolicy: IfNotPresent
                replicaCount: 2
                resources:
                  limits:
                    memory: 512Mi
                """;

        String result = service.updateImageTag(yaml, "v2.0.0");

        assertTrue(result.contains("repository: registry.example.com/team/orders-api"));
        assertTrue(result.contains("pullPolicy: IfNotPresent"));
        assertTrue(result.contains("replicaCount: 2"));
        assertTrue(result.contains("memory: 512Mi"));
        assertTrue(result.contains("tag: v2.0.0"));
    }

    @Test
    void updateImageTagHandlesNestedStructure() {
        String yaml = """
                image:
                  repository: registry.example.com/team/app
                  tag: v1.0.0
                service:
                  type: ClusterIP
                  port: 8080
                """;

        String result = service.updateImageTag(yaml, "v3.0.0");

        assertTrue(result.contains("tag: v3.0.0"));
        assertTrue(result.contains("type: ClusterIP"));
        assertTrue(result.contains("port: 8080"));
    }

    @Test
    void updateImageTagThrowsWhenImageSectionMissing() {
        String yaml = """
                replicaCount: 2
                service:
                  port: 8080
                """;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateImageTag(yaml, "v1.0.0"));
        assertTrue(ex.getMessage().contains("image"));
    }

    @Test
    void updateImageTagThrowsWhenTagKeyMissing() {
        String yaml = """
                image:
                  repository: registry.example.com/team/app
                  pullPolicy: IfNotPresent
                """;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateImageTag(yaml, "v1.0.0"));
        assertTrue(ex.getMessage().contains("tag"));
    }

    @Test
    void updateImageTagThrowsWhenYamlIsEmpty() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateImageTag("", "v1.0.0"));
        assertTrue(ex.getMessage().contains("empty"));
    }

    @Test
    void updateImageTagHandlesImageSectionNotBeingMap() {
        String yaml = """
                image: just-a-string
                replicaCount: 2
                """;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateImageTag(yaml, "v1.0.0"));
        assertTrue(ex.getMessage().contains("image"));
    }

    @Test
    void updateImageTagThrowsWhenYamlIsMalformed() {
        String malformedYaml = "image:\n  tag: [unclosed";

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateImageTag(malformedYaml, "v1.0.0"));
        assertTrue(ex.getMessage().contains("invalid YAML"));
    }
}
