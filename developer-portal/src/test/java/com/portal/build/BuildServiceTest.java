package com.portal.build;

import com.portal.application.Application;
import com.portal.cluster.Cluster;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BuildServiceTest {

    @Inject
    BuildService buildService;

    @InjectMock
    TektonAdapter tektonAdapter;

    @InjectMock
    SecretManagerCredentialProvider credentialProvider;

    private Team testTeam;
    private Team otherTeam;
    private Application appWithBuildConfig;
    private Application appWithoutBuildConfig;
    private Application crossTeamApp;
    private Cluster buildCluster;

    @BeforeAll
    void setUpData() {
        testTeam = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "build-svc-team";
            t.oidcGroupId = "build-svc-team";
            t.persist();
            t.flush();
            return t;
        });

        otherTeam = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "build-svc-other";
            t.oidcGroupId = "build-svc-other";
            t.persist();
            t.flush();
            return t;
        });

        buildCluster = QuarkusTransaction.requiringNew().call(() -> {
            Cluster c = new Cluster();
            c.name = "build-svc-ocp-dev";
            c.apiServerUrl = "https://api.build-svc-dev.example.com:6443";
            c.persist();
            c.flush();
            return c;
        });

        appWithBuildConfig = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "build-svc-payments";
            a.teamId = testTeam.id;
            a.gitRepoUrl = "https://github.com/org/payments.git";
            a.runtimeType = "quarkus";
            a.buildClusterId = buildCluster.id;
            a.buildNamespace = "build-svc-team-payments-build";
            a.persist();
            a.flush();
            return a;
        });

        appWithoutBuildConfig = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "build-svc-legacy";
            a.teamId = testTeam.id;
            a.gitRepoUrl = "https://github.com/org/legacy.git";
            a.runtimeType = "spring-boot";
            a.persist();
            a.flush();
            return a;
        });

        crossTeamApp = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "build-svc-other-app";
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

    @Test
    void triggerBuildCallsAdapterWithCorrectParams() {
        ClusterCredential credential = ClusterCredential.of("test-vault-token", 3600);
        when(credentialProvider.getCredentials("build-svc-ocp-dev", "portal"))
                .thenReturn(credential);

        BuildSummaryDto expected = new BuildSummaryDto(
                "build-svc-payments-abc12", "Building", Instant.now(),
                "build-svc-payments", "https://tekton.example.com/#/pipelineruns/build-svc-payments-abc12");
        when(tektonAdapter.triggerBuild(
                eq("build-svc-payments"),
                eq("build-svc-team-payments-build"),
                eq("https://api.build-svc-dev.example.com:6443"),
                eq("test-vault-token")))
                .thenReturn(expected);

        BuildSummaryDto result = buildService.triggerBuild(testTeam.id, appWithBuildConfig.id);

        assertEquals("build-svc-payments-abc12", result.buildId());
        assertEquals("Building", result.status());
        assertEquals("build-svc-payments", result.applicationName());

        verify(tektonAdapter).triggerBuild(
                "build-svc-payments",
                "build-svc-team-payments-build",
                "https://api.build-svc-dev.example.com:6443",
                "test-vault-token");
    }

    @Test
    void triggerBuildThrows404ForCrossTeamApp() {
        assertThrows(NotFoundException.class,
                () -> buildService.triggerBuild(testTeam.id, crossTeamApp.id));
    }

    @Test
    void triggerBuildThrows404ForNonExistentApp() {
        assertThrows(NotFoundException.class,
                () -> buildService.triggerBuild(testTeam.id, 999999L));
    }

    @Test
    void triggerBuildThrowsWhenBuildConfigMissing() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> buildService.triggerBuild(testTeam.id, appWithoutBuildConfig.id));
        assertTrue(ex.getMessage().contains("build configuration"));
    }
}
