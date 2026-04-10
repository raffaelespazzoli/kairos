package com.portal.deployment;

import com.portal.application.Application;
import com.portal.auth.TeamContext;
import com.portal.cluster.Cluster;
import com.portal.environment.Environment;
import com.portal.integration.PortalIntegrationException;
import com.portal.integration.git.GitProvider;
import com.portal.team.Team;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeploymentServiceTest {

    private static final String SAMPLE_VALUES_YAML = """
            image:
              repository: registry.example.com/team/orders-api
              tag: v1.4.1
              pullPolicy: IfNotPresent
            replicaCount: 2
            """;

    @Inject
    DeploymentService deploymentService;

    @InjectMock
    GitProvider gitProvider;

    @InjectMock
    TeamContext teamContext;

    private Team testTeam;
    private Team otherTeam;
    private Application testApp;
    private Application crossTeamApp;
    private Environment devEnv;
    private Environment qaEnv;
    private Cluster cluster;

    @BeforeAll
    void setUpData() {
        testTeam = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "deploy-svc-team";
            t.oidcGroupId = "deploy-svc-team";
            t.persist();
            t.flush();
            return t;
        });

        otherTeam = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "deploy-svc-other";
            t.oidcGroupId = "deploy-svc-other";
            t.persist();
            t.flush();
            return t;
        });

        cluster = QuarkusTransaction.requiringNew().call(() -> {
            Cluster c = new Cluster();
            c.name = "deploy-svc-ocp-dev";
            c.apiServerUrl = "https://api.deploy-svc-dev.example.com:6443";
            c.persist();
            c.flush();
            return c;
        });

        testApp = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "deploy-svc-orders";
            a.teamId = testTeam.id;
            a.gitRepoUrl = "https://github.com/org/orders.git";
            a.runtimeType = "quarkus";
            a.buildClusterId = cluster.id;
            a.buildNamespace = "deploy-svc-team-orders-build";
            a.persist();
            a.flush();
            return a;
        });

        crossTeamApp = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "deploy-svc-other-app";
            a.teamId = otherTeam.id;
            a.gitRepoUrl = "https://github.com/org/other.git";
            a.runtimeType = "quarkus";
            a.buildClusterId = cluster.id;
            a.buildNamespace = "other-team-other-app-build";
            a.persist();
            a.flush();
            return a;
        });

        devEnv = QuarkusTransaction.requiringNew().call(() -> {
            Environment e = new Environment();
            e.name = "Dev";
            e.applicationId = testApp.id;
            e.clusterId = cluster.id;
            e.namespace = "deploy-svc-team-orders-dev";
            e.promotionOrder = 1;
            e.persist();
            e.flush();
            return e;
        });

        qaEnv = QuarkusTransaction.requiringNew().call(() -> {
            Environment e = new Environment();
            e.name = "QA";
            e.applicationId = testApp.id;
            e.clusterId = cluster.id;
            e.namespace = "deploy-svc-team-orders-qa";
            e.promotionOrder = 2;
            e.persist();
            e.flush();
            return e;
        });
    }

    @Test
    void deployReleaseCommitsUpdatedYamlAndReturnsStatus() {
        when(teamContext.getTeamIdentifier()).thenReturn("marco");
        when(gitProvider.readFile(eq(testApp.gitRepoUrl), eq("main"),
                eq(".helm/run/values-run-dev.yaml")))
                .thenReturn(SAMPLE_VALUES_YAML);

        DeploymentStatusDto result = deploymentService.deployRelease(
                testTeam.id, testApp.id,
                new DeployRequest("v1.4.2", devEnv.id));

        assertEquals("v1.4.2", result.releaseVersion());
        assertEquals("Dev", result.environmentName());
        assertEquals("Deploying", result.status());
        assertNotNull(result.deploymentId());
        assertNotNull(result.startedAt());

        verify(gitProvider).commitFiles(
                eq(testApp.gitRepoUrl),
                eq("main"),
                argThat(files -> {
                    String content = files.get(".helm/run/values-run-dev.yaml");
                    return content != null && content.contains("tag: v1.4.2");
                }),
                contains("deploy: v1.4.2 to dev"));
    }

    @Test
    void deployReleaseCommitMessageIncludesDeployedByTrailer() {
        when(teamContext.getTeamIdentifier()).thenReturn("marco");
        when(gitProvider.readFile(anyString(), anyString(), anyString()))
                .thenReturn(SAMPLE_VALUES_YAML);

        deploymentService.deployRelease(testTeam.id, testApp.id,
                new DeployRequest("v1.4.2", devEnv.id));

        verify(gitProvider).commitFiles(anyString(), anyString(), anyMap(),
                argThat(msg -> msg.contains("Deployed-By: marco")));
    }

    @Test
    void deployReleaseUsesCorrectValuesPathForEnvironment() {
        when(teamContext.getTeamIdentifier()).thenReturn("marco");
        when(gitProvider.readFile(eq(testApp.gitRepoUrl), eq("main"),
                eq(".helm/run/values-run-qa.yaml")))
                .thenReturn(SAMPLE_VALUES_YAML);

        deploymentService.deployRelease(testTeam.id, testApp.id,
                new DeployRequest("v2.0.0", qaEnv.id));

        verify(gitProvider).readFile(testApp.gitRepoUrl, "main", ".helm/run/values-run-qa.yaml");
        verify(gitProvider).commitFiles(eq(testApp.gitRepoUrl), eq("main"),
                argThat(files -> files.containsKey(".helm/run/values-run-qa.yaml")),
                contains("deploy: v2.0.0 to qa"));
    }

    @Test
    void deployReleaseThrows404ForCrossTeamApp() {
        assertThrows(NotFoundException.class,
                () -> deploymentService.deployRelease(
                        testTeam.id, crossTeamApp.id,
                        new DeployRequest("v1.0.0", devEnv.id)));
    }

    @Test
    void deployReleaseThrows404ForNonExistentApp() {
        assertThrows(NotFoundException.class,
                () -> deploymentService.deployRelease(
                        testTeam.id, 999999L,
                        new DeployRequest("v1.0.0", devEnv.id)));
    }

    @Test
    void deployReleaseThrows404ForEnvironmentFromDifferentApp() {
        Environment otherAppEnv = QuarkusTransaction.requiringNew().call(() -> {
            Environment e = new Environment();
            e.name = "Dev";
            e.applicationId = crossTeamApp.id;
            e.clusterId = cluster.id;
            e.namespace = "other-app-dev";
            e.promotionOrder = 1;
            e.persist();
            e.flush();
            return e;
        });

        assertThrows(NotFoundException.class,
                () -> deploymentService.deployRelease(
                        testTeam.id, testApp.id,
                        new DeployRequest("v1.0.0", otherAppEnv.id)));
    }

    @Test
    void deployReleaseThrows404ForNonExistentEnvironment() {
        assertThrows(NotFoundException.class,
                () -> deploymentService.deployRelease(
                        testTeam.id, testApp.id,
                        new DeployRequest("v1.0.0", 999999L)));
    }

    @Test
    void deployReleasePropagatesGitReadFailure() {
        when(gitProvider.readFile(anyString(), anyString(), anyString()))
                .thenThrow(new PortalIntegrationException("git", "readFile",
                        "Git server returned HTTP 500"));

        PortalIntegrationException ex = assertThrows(PortalIntegrationException.class,
                () -> deploymentService.deployRelease(
                        testTeam.id, testApp.id,
                        new DeployRequest("v1.0.0", devEnv.id)));
        assertEquals("git", ex.getSystem());
        assertEquals("deploy-release", ex.getOperation());
        assertTrue(ex.getMessage().contains("could not read"));
    }

    @Test
    void deployReleasePropagatesGitCommitFailure() {
        when(teamContext.getTeamIdentifier()).thenReturn("marco");
        when(gitProvider.readFile(anyString(), anyString(), anyString()))
                .thenReturn(SAMPLE_VALUES_YAML);
        doThrow(new PortalIntegrationException("git", "commitFiles",
                "Git server returned HTTP 500"))
                .when(gitProvider).commitFiles(anyString(), anyString(), anyMap(), anyString());

        PortalIntegrationException ex = assertThrows(PortalIntegrationException.class,
                () -> deploymentService.deployRelease(
                        testTeam.id, testApp.id,
                        new DeployRequest("v1.0.0", devEnv.id)));
        assertEquals("git", ex.getSystem());
        assertEquals("deploy-release", ex.getOperation());
        assertTrue(ex.getMessage().contains("could not commit"));
    }

    @Test
    void deployReleaseThrows400ForMissingImageTag() {
        when(gitProvider.readFile(anyString(), anyString(), anyString()))
                .thenReturn("replicaCount: 2\n");

        assertThrows(IllegalArgumentException.class,
                () -> deploymentService.deployRelease(
                        testTeam.id, testApp.id,
                        new DeployRequest("v1.0.0", devEnv.id)));
    }
}
