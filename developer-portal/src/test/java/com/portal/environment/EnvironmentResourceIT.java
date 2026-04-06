package com.portal.environment;

import com.portal.application.Application;
import com.portal.cluster.Cluster;
import com.portal.integration.PortalIntegrationException;
import com.portal.integration.argocd.ArgoCdAdapter;
import com.portal.team.Team;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Instant;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EnvironmentResourceIT {

    @InjectMock
    ArgoCdAdapter argoCdAdapter;

    private Team testTeam;
    private Team otherTeam;
    private Application testApp;

    @BeforeAll
    void setUpData() {
        testTeam = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "envres-test-team";
            t.oidcGroupId = "envres-test-team";
            t.persist();
            t.flush();
            return t;
        });

        otherTeam = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "envres-other-team";
            t.oidcGroupId = "envres-other-team";
            t.persist();
            t.flush();
            return t;
        });

        Cluster cluster = QuarkusTransaction.requiringNew().call(() -> {
            Cluster c = new Cluster();
            c.name = "envres-ocp-dev";
            c.apiServerUrl = "https://api.envres-dev.example.com:6443";
            c.persist();
            c.flush();
            return c;
        });

        testApp = QuarkusTransaction.requiringNew().call(() -> {
            Application a = new Application();
            a.name = "envres-payments";
            a.teamId = testTeam.id;
            a.gitRepoUrl = "https://github.com/org/payments.git";
            a.runtimeType = "quarkus";
            a.persist();
            a.flush();
            return a;
        });

        QuarkusTransaction.requiringNew().run(() -> {
            Environment dev = new Environment();
            dev.name = "dev";
            dev.applicationId = testApp.id;
            dev.clusterId = cluster.id;
            dev.namespace = "payments-dev";
            dev.promotionOrder = 0;
            dev.persist();

            Environment staging = new Environment();
            staging.name = "staging";
            staging.applicationId = testApp.id;
            staging.clusterId = cluster.id;
            staging.namespace = "payments-staging";
            staging.promotionOrder = 1;
            staging.persist();

            Environment prod = new Environment();
            prod.name = "prod";
            prod.applicationId = testApp.id;
            prod.clusterId = cluster.id;
            prod.namespace = "payments-prod";
            prod.promotionOrder = 2;
            prod.persist();
        });
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "envres-test-team"),
            @Claim(key = "role", value = "member")
    })
    void getEnvironmentChainReturnsData() {
        when(argoCdAdapter.getEnvironmentStatuses(anyString(), anyList()))
                .thenReturn(List.of(
                        new EnvironmentStatusDto("dev", PortalEnvironmentStatus.HEALTHY,
                                "v1.4.2", Instant.now().minusSeconds(3600),
                                "envres-payments-run-dev",
                                "https://argocd/applications/envres-payments-run-dev", null),
                        new EnvironmentStatusDto("staging", PortalEnvironmentStatus.DEPLOYING,
                                "v1.4.2", null,
                                "envres-payments-run-staging",
                                "https://argocd/applications/envres-payments-run-staging", null),
                        new EnvironmentStatusDto("prod", PortalEnvironmentStatus.NOT_DEPLOYED,
                                null, null,
                                "envres-payments-run-prod",
                                "https://argocd/applications/envres-payments-run-prod", null)
                ));

        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications/{appId}/environments",
                        testTeam.id, testApp.id)
                .then()
                .statusCode(200)
                .body("environments.size()", equalTo(3))
                .body("argocdError", nullValue())
                .body("environments[0].environmentName", equalTo("dev"))
                .body("environments[0].status", equalTo("HEALTHY"))
                .body("environments[0].deployedVersion", equalTo("v1.4.2"))
                .body("environments[0].clusterName", equalTo("envres-ocp-dev"))
                .body("environments[0].namespace", equalTo("payments-dev"))
                .body("environments[0].vaultDeepLink",
                        equalTo("https://vault.test.example.com/ui/vault/secrets/applications/envres-test-team/envres-test-team-envres-payments-dev/static-secrets"))
                .body("environments[0].grafanaDeepLink", nullValue())
                .body("environments[1].environmentName", equalTo("staging"))
                .body("environments[1].status", equalTo("DEPLOYING"))
                .body("environments[1].vaultDeepLink",
                        equalTo("https://vault.test.example.com/ui/vault/secrets/applications/envres-test-team/envres-test-team-envres-payments-staging/static-secrets"))
                .body("environments[1].grafanaDeepLink", nullValue())
                .body("environments[2].environmentName", equalTo("prod"))
                .body("environments[2].status", equalTo("NOT_DEPLOYED"))
                .body("environments[2].vaultDeepLink",
                        equalTo("https://vault.test.example.com/ui/vault/secrets/applications/envres-test-team/envres-test-team-envres-payments-prod/static-secrets"))
                .body("environments[2].grafanaDeepLink", nullValue());
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "envres-other-team"),
            @Claim(key = "role", value = "member")
    })
    void crossTeamAccessReturns404() {
        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications/{appId}/environments",
                        testTeam.id, testApp.id)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "envres-test-team"),
            @Claim(key = "role", value = "member")
    })
    void argoCdFailureReturnsPartialDataWithWarning() {
        when(argoCdAdapter.getEnvironmentStatuses(anyString(), anyList()))
                .thenThrow(new PortalIntegrationException("argocd", "getEnvironmentStatus",
                        "Deployment status unavailable — ArgoCD is unreachable"));

        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications/{appId}/environments",
                        testTeam.id, testApp.id)
                .then()
                .statusCode(200)
                .body("environments.size()", equalTo(3))
                .body("argocdError", containsString("ArgoCD is unreachable"))
                .body("environments[0].status", equalTo("UNKNOWN"))
                .body("environments[0].environmentName", equalTo("dev"))
                .body("environments[0].clusterName", equalTo("envres-ocp-dev"))
                .body("environments[0].namespace", equalTo("payments-dev"));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "envres-test-team"),
            @Claim(key = "role", value = "member")
    })
    void nonExistentAppReturns404() {
        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications/{appId}/environments",
                        testTeam.id, 999999L)
                .then()
                .statusCode(404);
    }
}
