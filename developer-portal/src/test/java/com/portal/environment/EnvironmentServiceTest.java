package com.portal.environment;

import com.portal.application.Application;
import com.portal.cluster.Cluster;
import com.portal.integration.PortalIntegrationException;
import com.portal.integration.argocd.ArgoCdAdapter;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EnvironmentServiceTest {

    @Inject
    EnvironmentService environmentService;

    @InjectMock
    ArgoCdAdapter argoCdAdapter;

    private Team team;
    private Team otherTeam;
    private Application app;
    private Application crossTeamApp;
    private Cluster cluster;

    @BeforeAll
    void setUpData() {
        team = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "env-svc-team";
            t.oidcGroupId = "env-svc-team";
            t.persist();
            t.flush();
            return t;
        });

        otherTeam = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "env-svc-other";
            t.oidcGroupId = "env-svc-other";
            t.persist();
            t.flush();
            return t;
        });

        cluster = QuarkusTransaction.requiringNew().call(() -> {
            Cluster c = new Cluster();
            c.name = "env-svc-ocp-dev";
            c.apiServerUrl = "https://api.env-svc-dev.example.com:6443";
            c.persist();
            c.flush();
            return c;
        });

        app = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "env-svc-payments";
            a.teamId = team.id;
            a.gitRepoUrl = "https://github.com/org/payments.git";
            a.runtimeType = "quarkus";
            a.persist();
            a.flush();
            return a;
        });

        crossTeamApp = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "env-svc-other-app";
            a.teamId = otherTeam.id;
            a.gitRepoUrl = "https://github.com/org/other.git";
            a.runtimeType = "spring-boot";
            a.persist();
            a.flush();
            return a;
        });

        QuarkusTransaction.requiringNew().run(() -> {
            Environment dev = new Environment();
            dev.name = "dev";
            dev.applicationId = app.id;
            dev.clusterId = cluster.id;
            dev.namespace = "payments-dev";
            dev.promotionOrder = 0;
            dev.persist();

            Environment staging = new Environment();
            staging.name = "staging";
            staging.applicationId = app.id;
            staging.clusterId = cluster.id;
            staging.namespace = "payments-staging";
            staging.promotionOrder = 1;
            staging.persist();

            Environment prod = new Environment();
            prod.name = "prod";
            prod.applicationId = app.id;
            prod.clusterId = cluster.id;
            prod.namespace = "payments-prod";
            prod.promotionOrder = 2;
            prod.persist();
        });
    }

    @Test
    void getEnvironmentChainMergesStatusesCorrectly() {
        when(argoCdAdapter.getEnvironmentStatuses(anyString(), anyList()))
                .thenReturn(List.of(
                        new EnvironmentStatusDto("dev", PortalEnvironmentStatus.HEALTHY,
                                "v1.4.2", Instant.now().minusSeconds(3600),
                                "env-svc-payments-run-dev",
                                "https://argocd/applications/env-svc-payments-run-dev"),
                        new EnvironmentStatusDto("staging", PortalEnvironmentStatus.DEPLOYING,
                                "v1.4.2", null,
                                "env-svc-payments-run-staging",
                                "https://argocd/applications/env-svc-payments-run-staging"),
                        new EnvironmentStatusDto("prod", PortalEnvironmentStatus.NOT_DEPLOYED,
                                null, null,
                                "env-svc-payments-run-prod",
                                "https://argocd/applications/env-svc-payments-run-prod")
                ));

        EnvironmentChainResponse response = environmentService.getEnvironmentChain(team.id, app.id);

        assertNull(response.argocdError());
        assertEquals(3, response.environments().size());

        EnvironmentChainEntryDto dev = response.environments().get(0);
        assertEquals("dev", dev.environmentName());
        assertEquals("env-svc-ocp-dev", dev.clusterName());
        assertEquals("payments-dev", dev.namespace());
        assertEquals(0, dev.promotionOrder());
        assertEquals("HEALTHY", dev.status());
        assertEquals("v1.4.2", dev.deployedVersion());
        assertNotNull(dev.lastDeployedAt());

        EnvironmentChainEntryDto staging = response.environments().get(1);
        assertEquals("staging", staging.environmentName());
        assertEquals("DEPLOYING", staging.status());

        EnvironmentChainEntryDto prod = response.environments().get(2);
        assertEquals("prod", prod.environmentName());
        assertEquals("NOT_DEPLOYED", prod.status());
        assertNull(prod.deployedVersion());
    }

    @Test
    void getEnvironmentChainReturnsPartialDataOnArgoCdFailure() {
        when(argoCdAdapter.getEnvironmentStatuses(anyString(), anyList()))
                .thenThrow(new PortalIntegrationException("argocd", "getEnvironmentStatus",
                        "Deployment status unavailable — ArgoCD is unreachable"));

        EnvironmentChainResponse response = environmentService.getEnvironmentChain(team.id, app.id);

        assertNotNull(response.argocdError());
        assertEquals("Deployment status unavailable — ArgoCD is unreachable", response.argocdError());
        assertEquals(3, response.environments().size());

        for (EnvironmentChainEntryDto entry : response.environments()) {
            assertEquals("UNKNOWN", entry.status());
            assertNull(entry.deployedVersion());
            assertNull(entry.lastDeployedAt());
            assertNotNull(entry.namespace());
            assertEquals("env-svc-ocp-dev", entry.clusterName());
        }
    }

    @Test
    void getEnvironmentChainThrowsNotFoundForMissingApp() {
        assertThrows(NotFoundException.class,
                () -> environmentService.getEnvironmentChain(team.id, 999999L));
    }

    @Test
    void getEnvironmentChainThrowsNotFoundForCrossTeamAccess() {
        assertThrows(NotFoundException.class,
                () -> environmentService.getEnvironmentChain(team.id, crossTeamApp.id));
    }
}
