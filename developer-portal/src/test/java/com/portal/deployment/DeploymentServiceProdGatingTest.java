package com.portal.deployment;

import com.portal.application.Application;
import com.portal.auth.PortalAuthorizationException;
import com.portal.auth.TeamContext;
import com.portal.cluster.Cluster;
import com.portal.environment.Environment;
import com.portal.integration.git.GitProvider;
import com.portal.team.Team;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeploymentServiceProdGatingTest {

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

    private Team team;
    private Application app;
    private Environment devEnv;
    private Environment prodEnv;

    @BeforeAll
    void setUpData() {
        team = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "prod-gate-team";
            t.oidcGroupId = "prod-gate-team";
            t.persist();
            t.flush();
            return t;
        });

        Cluster cluster = QuarkusTransaction.requiringNew().call(() -> {
            Cluster c = new Cluster();
            c.name = "prod-gate-cluster";
            c.apiServerUrl = "https://api.prod-gate-cluster.example.com:6443";
            c.persist();
            c.flush();
            return c;
        });

        app = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "prod-gate-app";
            a.teamId = team.id;
            a.gitRepoUrl = "https://github.com/org/prod-gate-app.git";
            a.runtimeType = "quarkus";
            a.buildClusterId = cluster.id;
            a.buildNamespace = "prod-gate-team-app-build";
            a.persist();
            a.flush();
            return a;
        });

        devEnv = QuarkusTransaction.requiringNew().call(() -> {
            Environment e = new Environment();
            e.name = "Dev";
            e.applicationId = app.id;
            e.clusterId = cluster.id;
            e.namespace = "prod-gate-team-app-dev";
            e.promotionOrder = 1;
            e.isProduction = false;
            e.persist();
            e.flush();
            return e;
        });

        prodEnv = QuarkusTransaction.requiringNew().call(() -> {
            Environment e = new Environment();
            e.name = "Prod";
            e.applicationId = app.id;
            e.clusterId = cluster.id;
            e.namespace = "prod-gate-team-app-prod";
            e.promotionOrder = 2;
            e.isProduction = true;
            e.persist();
            e.flush();
            return e;
        });
    }

    private void stubGitForSuccess() {
        when(teamContext.getTeamIdentifier()).thenReturn("developer");
        when(gitProvider.readFile(anyString(), anyString(), anyString()))
                .thenReturn(SAMPLE_VALUES_YAML);
    }

    @Test
    void memberDeployToProductionThrowsAuthorizationException() {
        when(teamContext.getRole()).thenReturn("member");

        PortalAuthorizationException ex = assertThrows(PortalAuthorizationException.class,
                () -> deploymentService.deployRelease(
                        team.id, app.id,
                        new DeployRequest("v1.0.0", prodEnv.id)));

        assertEquals("member", ex.getRole());
        assertEquals("deployments", ex.getResource());
        assertEquals("deploy-prod", ex.getAction());
    }

    @Test
    void leadDeployToProductionSucceeds() {
        when(teamContext.getRole()).thenReturn("lead");
        stubGitForSuccess();

        DeploymentStatusDto result = deploymentService.deployRelease(
                team.id, app.id,
                new DeployRequest("v1.0.0", prodEnv.id));

        assertEquals("v1.0.0", result.releaseVersion());
        assertEquals("Prod", result.environmentName());
    }

    @Test
    void adminDeployToProductionSucceeds() {
        when(teamContext.getRole()).thenReturn("admin");
        stubGitForSuccess();

        DeploymentStatusDto result = deploymentService.deployRelease(
                team.id, app.id,
                new DeployRequest("v2.0.0", prodEnv.id));

        assertEquals("v2.0.0", result.releaseVersion());
        assertEquals("Prod", result.environmentName());
    }

    @Test
    void memberDeployToNonProductionSucceeds() {
        when(teamContext.getRole()).thenReturn("member");
        stubGitForSuccess();

        DeploymentStatusDto result = deploymentService.deployRelease(
                team.id, app.id,
                new DeployRequest("v1.0.0", devEnv.id));

        assertEquals("v1.0.0", result.releaseVersion());
        assertEquals("Dev", result.environmentName());
    }

    @Test
    void leadDeployToNonProductionSucceeds() {
        when(teamContext.getRole()).thenReturn("lead");
        stubGitForSuccess();

        DeploymentStatusDto result = deploymentService.deployRelease(
                team.id, app.id,
                new DeployRequest("v1.0.0", devEnv.id));

        assertEquals("v1.0.0", result.releaseVersion());
        assertEquals("Dev", result.environmentName());
    }
}
