package com.portal.deployment;

import com.portal.application.Application;
import com.portal.auth.TeamContext;
import com.portal.cluster.Cluster;
import com.portal.deeplink.DeepLinkService;
import com.portal.environment.Environment;
import com.portal.environment.EnvironmentStatusDto;
import com.portal.environment.PortalEnvironmentStatus;
import com.portal.integration.PortalIntegrationException;
import com.portal.integration.argocd.ArgoCdAdapter;
import com.portal.integration.git.GitProvider;
import com.portal.integration.git.model.GitCommit;
import com.portal.team.Team;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeploymentServiceHistoryTest {

    @Inject
    DeploymentService deploymentService;

    @InjectMock
    GitProvider gitProvider;

    @InjectMock
    ArgoCdAdapter argoCdAdapter;

    @InjectMock
    DeepLinkService deepLinkService;

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
            t.name = "hist-svc-team";
            t.oidcGroupId = "hist-svc-team";
            t.persist();
            t.flush();
            return t;
        });

        otherTeam = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "hist-svc-other";
            t.oidcGroupId = "hist-svc-other";
            t.persist();
            t.flush();
            return t;
        });

        cluster = QuarkusTransaction.requiringNew().call(() -> {
            Cluster c = new Cluster();
            c.name = "hist-svc-ocp-dev";
            c.apiServerUrl = "https://api.hist-svc-dev.example.com:6443";
            c.persist();
            c.flush();
            return c;
        });

        testApp = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "hist-svc-orders";
            a.teamId = testTeam.id;
            a.gitRepoUrl = "https://github.com/org/orders.git";
            a.runtimeType = "quarkus";
            a.buildClusterId = cluster.id;
            a.buildNamespace = "hist-svc-team-orders-build";
            a.persist();
            a.flush();
            return a;
        });

        crossTeamApp = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "hist-svc-other-app";
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
            e.namespace = "hist-svc-team-orders-dev";
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
            e.namespace = "hist-svc-team-orders-qa";
            e.promotionOrder = 2;
            e.persist();
            e.flush();
            return e;
        });
    }

    private List<GitCommit> threeDeployCommits(String envName) {
        return List.of(
            new GitCommit("sha1abc", "dev-user",
                    Instant.parse("2026-04-09T15:00:00Z"),
                    "deploy: v1.4.2 to " + envName + "\n\nDeployed-By: marco"),
            new GitCommit("sha2bcd", "dev-user",
                    Instant.parse("2026-04-08T12:00:00Z"),
                    "deploy: v1.4.1 to " + envName + "\n\nDeployed-By: anna"),
            new GitCommit("sha3cde", "dev-user",
                    Instant.parse("2026-04-07T09:00:00Z"),
                    "deploy: v1.4.0 to " + envName + "\n\nDeployed-By: marco")
        );
    }

    private EnvironmentStatusDto healthyStatus(String envName) {
        return new EnvironmentStatusDto(envName, PortalEnvironmentStatus.HEALTHY,
                "v1.4.2", Instant.parse("2026-04-09T15:05:00Z"),
                "hist-svc-orders-run-" + envName.toLowerCase(),
                "https://argocd/applications/hist-svc-orders-run-" + envName.toLowerCase(),
                null);
    }

    @Test
    void listByEnvironmentReturnsCorrectEntries() {
        when(deepLinkService.generateArgoCdLink(anyString())).thenReturn(Optional.of("https://argocd/app"));
        when(gitProvider.listCommits(eq(testApp.gitRepoUrl),
                eq(".helm/run/values-run-dev.yaml"), eq(25)))
                .thenReturn(threeDeployCommits("dev"));
        when(argoCdAdapter.getEnvironmentStatuses(eq(testApp.name), anyList()))
                .thenReturn(List.of(healthyStatus("Dev")));

        List<DeploymentHistoryDto> result = deploymentService.listDeployments(
                testTeam.id, testApp.id, devEnv.id);

        assertEquals(3, result.size());
        assertEquals("v1.4.2", result.get(0).releaseVersion());
        assertEquals("v1.4.1", result.get(1).releaseVersion());
        assertEquals("v1.4.0", result.get(2).releaseVersion());
        assertEquals("sha1abc", result.get(0).deploymentId());
    }

    @Test
    void listAllEnvironmentsMergesAndSortsByTimestamp() {
        when(deepLinkService.generateArgoCdLink(anyString())).thenReturn(Optional.of("https://argocd/app"));
        when(gitProvider.listCommits(eq(testApp.gitRepoUrl),
                eq(".helm/run/values-run-dev.yaml"), eq(25)))
                .thenReturn(List.of(
                        new GitCommit("sha-dev1", "user", Instant.parse("2026-04-09T15:00:00Z"),
                                "deploy: v1.4.2 to dev\n\nDeployed-By: marco")));
        when(gitProvider.listCommits(eq(testApp.gitRepoUrl),
                eq(".helm/run/values-run-qa.yaml"), eq(25)))
                .thenReturn(List.of(
                        new GitCommit("sha-qa1", "user", Instant.parse("2026-04-09T16:00:00Z"),
                                "deploy: v1.4.1 to qa\n\nDeployed-By: anna")));
        when(argoCdAdapter.getEnvironmentStatuses(eq(testApp.name), anyList()))
                .thenReturn(List.of(healthyStatus("Dev")));

        List<DeploymentHistoryDto> result = deploymentService.listDeployments(
                testTeam.id, testApp.id, null);

        assertEquals(2, result.size());
        assertEquals("sha-qa1", result.get(0).deploymentId());
        assertEquals("sha-dev1", result.get(1).deploymentId());
    }

    @Test
    void commitMessageParsesVersionAndDeployedBy() {
        when(deepLinkService.generateArgoCdLink(anyString())).thenReturn(Optional.of("https://argocd/app"));
        when(gitProvider.listCommits(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(
                        new GitCommit("sha1", "git-author",
                                Instant.parse("2026-04-09T15:00:00Z"),
                                "deploy: v1.4.2 to dev\n\nDeployed-By: marco")));
        when(argoCdAdapter.getEnvironmentStatuses(anyString(), anyList()))
                .thenReturn(List.of(healthyStatus("Dev")));

        List<DeploymentHistoryDto> result = deploymentService.listDeployments(
                testTeam.id, testApp.id, devEnv.id);

        assertEquals(1, result.size());
        assertEquals("v1.4.2", result.get(0).releaseVersion());
        assertEquals("marco", result.get(0).deployedBy());
    }

    @Test
    void commitMessageWithoutTrailerFallsBackToAuthor() {
        when(deepLinkService.generateArgoCdLink(anyString())).thenReturn(Optional.of("https://argocd/app"));
        when(gitProvider.listCommits(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(
                        new GitCommit("sha1", "git-author",
                                Instant.parse("2026-04-09T15:00:00Z"),
                                "deploy: v1.4.2 to dev")));
        when(argoCdAdapter.getEnvironmentStatuses(anyString(), anyList()))
                .thenReturn(List.of(healthyStatus("Dev")));

        List<DeploymentHistoryDto> result = deploymentService.listDeployments(
                testTeam.id, testApp.id, devEnv.id);

        assertEquals("git-author", result.get(0).deployedBy());
    }

    @Test
    void nonDeployCommitIsSkipped() {
        when(deepLinkService.generateArgoCdLink(anyString())).thenReturn(Optional.of("https://argocd/app"));
        when(gitProvider.listCommits(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(
                        new GitCommit("sha1", "user",
                                Instant.parse("2026-04-09T15:00:00Z"),
                                "fix: typo in values")));
        when(argoCdAdapter.getEnvironmentStatuses(anyString(), anyList()))
                .thenReturn(List.of(healthyStatus("Dev")));

        List<DeploymentHistoryDto> result = deploymentService.listDeployments(
                testTeam.id, testApp.id, devEnv.id);

        assertTrue(result.isEmpty());
    }

    @Test
    void enrichmentHealthySetStatusDeployedAndCompletedAt() {
        when(deepLinkService.generateArgoCdLink(anyString())).thenReturn(Optional.of("https://argocd/app"));
        when(gitProvider.listCommits(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(
                        new GitCommit("sha1", "user",
                                Instant.parse("2026-04-09T15:00:00Z"),
                                "deploy: v1.4.2 to dev\n\nDeployed-By: marco")));
        when(argoCdAdapter.getEnvironmentStatuses(anyString(), anyList()))
                .thenReturn(List.of(new EnvironmentStatusDto(
                        "Dev", PortalEnvironmentStatus.HEALTHY,
                        "v1.4.2", Instant.parse("2026-04-09T15:05:00Z"),
                        "app-run-dev", "https://argocd/app", null)));

        List<DeploymentHistoryDto> result = deploymentService.listDeployments(
                testTeam.id, testApp.id, devEnv.id);

        assertEquals("Deployed", result.get(0).status());
        assertEquals(Instant.parse("2026-04-09T15:05:00Z"), result.get(0).completedAt());
    }

    @Test
    void enrichmentUnhealthySetsStatusFailed() {
        when(deepLinkService.generateArgoCdLink(anyString())).thenReturn(Optional.of("https://argocd/app"));
        when(gitProvider.listCommits(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(
                        new GitCommit("sha1", "user",
                                Instant.parse("2026-04-09T15:00:00Z"),
                                "deploy: v1.4.2 to dev\n\nDeployed-By: marco")));
        when(argoCdAdapter.getEnvironmentStatuses(anyString(), anyList()))
                .thenReturn(List.of(new EnvironmentStatusDto(
                        "Dev", PortalEnvironmentStatus.UNHEALTHY,
                        "v1.4.2", Instant.parse("2026-04-09T15:05:00Z"),
                        "app-run-dev", "https://argocd/app", null)));

        List<DeploymentHistoryDto> result = deploymentService.listDeployments(
                testTeam.id, testApp.id, devEnv.id);

        assertEquals("Failed", result.get(0).status());
    }

    @Test
    void enrichmentDeployingSetsStatusDeploying() {
        when(deepLinkService.generateArgoCdLink(anyString())).thenReturn(Optional.of("https://argocd/app"));
        when(gitProvider.listCommits(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(
                        new GitCommit("sha1", "user",
                                Instant.parse("2026-04-09T15:00:00Z"),
                                "deploy: v1.4.2 to dev\n\nDeployed-By: marco")));
        when(argoCdAdapter.getEnvironmentStatuses(anyString(), anyList()))
                .thenReturn(List.of(new EnvironmentStatusDto(
                        "Dev", PortalEnvironmentStatus.DEPLOYING,
                        null, null, "app-run-dev", "https://argocd/app", null)));

        List<DeploymentHistoryDto> result = deploymentService.listDeployments(
                testTeam.id, testApp.id, devEnv.id);

        assertEquals("Deploying", result.get(0).status());
        assertNull(result.get(0).completedAt());
    }

    @Test
    void enrichmentNotDeployedSetsStatusDeploying() {
        when(deepLinkService.generateArgoCdLink(anyString())).thenReturn(Optional.of("https://argocd/app"));
        when(gitProvider.listCommits(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(
                        new GitCommit("sha1", "user",
                                Instant.parse("2026-04-09T15:00:00Z"),
                                "deploy: v1.4.2 to dev\n\nDeployed-By: marco")));
        when(argoCdAdapter.getEnvironmentStatuses(anyString(), anyList()))
                .thenReturn(List.of(new EnvironmentStatusDto(
                        "Dev", PortalEnvironmentStatus.NOT_DEPLOYED,
                        null, null, "app-run-dev", "https://argocd/app", null)));

        List<DeploymentHistoryDto> result = deploymentService.listDeployments(
                testTeam.id, testApp.id, devEnv.id);

        assertEquals("Deploying", result.get(0).status());
        assertNull(result.get(0).completedAt());
    }

    @Test
    void enrichmentArgoCdUnreachableDefaultsToDeploying() {
        when(deepLinkService.generateArgoCdLink(anyString())).thenReturn(Optional.of("https://argocd/app"));
        when(gitProvider.listCommits(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(
                        new GitCommit("sha1", "user",
                                Instant.parse("2026-04-09T15:00:00Z"),
                                "deploy: v1.4.2 to dev\n\nDeployed-By: marco")));
        when(argoCdAdapter.getEnvironmentStatuses(anyString(), anyList()))
                .thenThrow(new PortalIntegrationException("argocd", "getStatus", "Connection refused"));

        List<DeploymentHistoryDto> result = deploymentService.listDeployments(
                testTeam.id, testApp.id, devEnv.id);

        assertEquals(1, result.size());
        assertEquals("Deploying", result.get(0).status());
    }

    @Test
    void olderDeploymentsDefaultToDeployedStatus() {
        when(deepLinkService.generateArgoCdLink(anyString())).thenReturn(Optional.of("https://argocd/app"));
        when(gitProvider.listCommits(anyString(), anyString(), anyInt()))
                .thenReturn(threeDeployCommits("dev"));
        when(argoCdAdapter.getEnvironmentStatuses(anyString(), anyList()))
                .thenReturn(List.of(healthyStatus("Dev")));

        List<DeploymentHistoryDto> result = deploymentService.listDeployments(
                testTeam.id, testApp.id, devEnv.id);

        assertEquals("Deployed", result.get(1).status());
        assertEquals("Deployed", result.get(2).status());
    }

    @Test
    void singleEnvironmentGitFailurePropagatesAsIntegrationException() {
        when(gitProvider.listCommits(anyString(), anyString(), anyInt()))
                .thenThrow(new PortalIntegrationException("git", "listCommits", "Git server returned HTTP 500"));

        PortalIntegrationException ex = assertThrows(PortalIntegrationException.class,
                () -> deploymentService.listDeployments(
                        testTeam.id, testApp.id, devEnv.id));
        assertEquals("git", ex.getSystem());
        assertTrue(ex.getMessage().contains("Deployment history unavailable"));
    }

    @Test
    void allEnvironmentsGitFailureGracefullyDegrades() {
        when(deepLinkService.generateArgoCdLink(anyString())).thenReturn(Optional.of("https://argocd/app"));
        when(gitProvider.listCommits(eq(testApp.gitRepoUrl),
                eq(".helm/run/values-run-dev.yaml"), eq(25)))
                .thenThrow(new PortalIntegrationException("git", "listCommits", "Git server returned HTTP 500"));
        when(gitProvider.listCommits(eq(testApp.gitRepoUrl),
                eq(".helm/run/values-run-qa.yaml"), eq(25)))
                .thenReturn(List.of(
                        new GitCommit("sha-qa1", "user", Instant.parse("2026-04-09T16:00:00Z"),
                                "deploy: v1.4.1 to qa\n\nDeployed-By: anna")));
        when(argoCdAdapter.getEnvironmentStatuses(anyString(), anyList()))
                .thenReturn(List.of(healthyStatus("QA")));

        List<DeploymentHistoryDto> result = deploymentService.listDeployments(
                testTeam.id, testApp.id, null);

        assertEquals(1, result.size());
        assertEquals("sha-qa1", result.get(0).deploymentId());
    }

    @Test
    void teamOwnershipListForAppInDifferentTeamReturns404() {
        assertThrows(NotFoundException.class,
                () -> deploymentService.listDeployments(
                        testTeam.id, crossTeamApp.id, null));
    }

    @Test
    void environmentOwnershipEnvironmentFromDifferentAppReturns404() {
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
                () -> deploymentService.listDeployments(
                        testTeam.id, testApp.id, otherAppEnv.id));
    }
}
