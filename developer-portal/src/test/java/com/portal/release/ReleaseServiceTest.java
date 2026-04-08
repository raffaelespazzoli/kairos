package com.portal.release;

import com.portal.application.Application;
import com.portal.build.BuildDetailDto;
import com.portal.build.BuildService;
import com.portal.cluster.Cluster;
import com.portal.integration.PortalIntegrationException;
import com.portal.integration.git.GitProvider;
import com.portal.integration.registry.RegistryAdapter;
import com.portal.integration.secrets.ClusterCredential;
import com.portal.integration.secrets.SecretManagerCredentialProvider;
import com.portal.integration.tekton.TektonAdapter;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReleaseServiceTest {

    @Inject
    ReleaseService releaseService;

    @InjectMock
    TektonAdapter tektonAdapter;

    @InjectMock
    SecretManagerCredentialProvider credentialProvider;

    @InjectMock
    GitProvider gitProvider;

    @InjectMock
    RegistryAdapter registryAdapter;

    private Team testTeam;
    private Team otherTeam;
    private Application testApp;
    private Application crossTeamApp;
    private Cluster buildCluster;

    @BeforeAll
    void setUpData() {
        testTeam = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "release-svc-team";
            t.oidcGroupId = "release-svc-team";
            t.persist();
            t.flush();
            return t;
        });

        otherTeam = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "release-svc-other";
            t.oidcGroupId = "release-svc-other";
            t.persist();
            t.flush();
            return t;
        });

        buildCluster = QuarkusTransaction.requiringNew().call(() -> {
            Cluster c = new Cluster();
            c.name = "release-svc-ocp-dev";
            c.apiServerUrl = "https://api.release-svc-dev.example.com:6443";
            c.persist();
            c.flush();
            return c;
        });

        testApp = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "release-svc-payments";
            a.teamId = testTeam.id;
            a.gitRepoUrl = "https://github.com/org/payments.git";
            a.runtimeType = "quarkus";
            a.buildClusterId = buildCluster.id;
            a.buildNamespace = "release-svc-team-payments-build";
            a.persist();
            a.flush();
            return a;
        });

        crossTeamApp = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "release-svc-other-app";
            a.teamId = otherTeam.id;
            a.gitRepoUrl = "https://github.com/org/other.git";
            a.runtimeType = "quarkus";
            a.buildClusterId = buildCluster.id;
            a.buildNamespace = "other-team-other-app-build";
            a.persist();
            a.flush();
            return a;
        });
    }

    private void stubCredentials() {
        when(credentialProvider.getCredentials(anyString(), anyString()))
                .thenReturn(ClusterCredential.of("test-token", 3600));
    }

    private void stubPassedBuild(String buildId) {
        stubCredentials();
        when(tektonAdapter.getBuildDetail(
                eq(buildId), anyString(), anyString(), anyString()))
                .thenReturn(new BuildDetailDto(
                        buildId, "Passed",
                        Instant.now().minusSeconds(600), Instant.now().minusSeconds(300),
                        "5m 0s", "release-svc-payments",
                        "registry.example.com/team/app:abc1234",
                        "abc1234def567890", null, null, null, null));
    }

    @Test
    void createReleaseOrchestatesGitTagAndRegistryTag() {
        stubPassedBuild("run-release-1");

        ReleaseSummaryDto result = releaseService.createRelease(
                testTeam.id, testApp.id,
                new CreateReleaseRequest("run-release-1", "v1.4.2"));

        assertEquals("v1.4.2", result.version());
        assertEquals("run-release-1", result.buildId());
        assertEquals("abc1234def567890", result.commitSha());
        assertEquals("registry.example.com/team/app:abc1234", result.imageReference());
        assertNotNull(result.createdAt());

        verify(gitProvider).createTag(
                "https://github.com/org/payments.git",
                "abc1234def567890",
                "v1.4.2");
        verify(registryAdapter).tagImage(
                "registry.example.com/team/app:abc1234",
                "v1.4.2");
    }

    @Test
    void createReleaseRejectsBuildWithNoImageReference() {
        stubCredentials();
        when(tektonAdapter.getBuildDetail(
                eq("run-no-image"), anyString(), anyString(), anyString()))
                .thenReturn(new BuildDetailDto(
                        "run-no-image", "Passed",
                        Instant.now().minusSeconds(600), Instant.now().minusSeconds(300),
                        "5m 0s", "release-svc-payments",
                        null, "abc1234", null, null, null, null));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> releaseService.createRelease(
                        testTeam.id, testApp.id,
                        new CreateReleaseRequest("run-no-image", "v1.0.0")));
        assertTrue(ex.getMessage().contains("container image"));
        verify(gitProvider, never()).createTag(anyString(), anyString(), anyString());
        verify(registryAdapter, never()).tagImage(anyString(), anyString());
    }

    @Test
    void createReleaseIsRetrySafeWhenGitTagAlreadyExists() {
        stubPassedBuild("run-retry-1");
        doThrow(new PortalIntegrationException("git", "createTag",
                "Release tag already exists \u2014 choose a different version"))
                .when(gitProvider).createTag(anyString(), anyString(), anyString());

        ReleaseSummaryDto result = releaseService.createRelease(
                testTeam.id, testApp.id,
                new CreateReleaseRequest("run-retry-1", "v1.4.2"));

        assertEquals("v1.4.2", result.version());
        verify(registryAdapter).tagImage(
                "registry.example.com/team/app:abc1234", "v1.4.2");
    }

    @Test
    void createReleaseRethrowsNonIdempotentGitErrors() {
        stubPassedBuild("run-git-err");
        doThrow(new PortalIntegrationException("git", "createTag",
                "Git server returned HTTP 500 for create tag v1.0.0"))
                .when(gitProvider).createTag(anyString(), anyString(), anyString());

        assertThrows(PortalIntegrationException.class,
                () -> releaseService.createRelease(
                        testTeam.id, testApp.id,
                        new CreateReleaseRequest("run-git-err", "v1.0.0")));
        verify(registryAdapter, never()).tagImage(anyString(), anyString());
    }

    @Test
    void createReleaseRejectsNonPassedBuild() {
        stubCredentials();
        when(tektonAdapter.getBuildDetail(
                eq("run-failed"), anyString(), anyString(), anyString()))
                .thenReturn(new BuildDetailDto(
                        "run-failed", "Failed",
                        Instant.now(), null, null, "release-svc-payments",
                        null, null, "Run Tests", "Test failed", null, null));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> releaseService.createRelease(
                        testTeam.id, testApp.id,
                        new CreateReleaseRequest("run-failed", "v1.0.0")));
        assertTrue(ex.getMessage().contains("Failed"));
    }

    @Test
    void createReleaseThrows404ForCrossTeamApp() {
        assertThrows(NotFoundException.class,
                () -> releaseService.createRelease(
                        testTeam.id, crossTeamApp.id,
                        new CreateReleaseRequest("run-1", "v1.0.0")));
    }

    @Test
    void createReleaseThrows404ForNonExistentApp() {
        assertThrows(NotFoundException.class,
                () -> releaseService.createRelease(
                        testTeam.id, 999999L,
                        new CreateReleaseRequest("run-1", "v1.0.0")));
    }
}
