package com.portal.auth;

import com.portal.application.Application;
import com.portal.cluster.Cluster;
import com.portal.environment.Environment;
import com.portal.team.Team;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Populates the database with sample data when running in dev mode.
 * Idempotent — checks for existing records before inserting.
 */
@ApplicationScoped
@IfBuildProfile("dev")
public class DevSeedDataLoader {

    @Startup
    @Transactional
    void seed() {
        if (Cluster.count() > 0) {
            return;
        }

        Team team1 = findOrCreateTeam("team-1", "team-1");
        Team team2 = findOrCreateTeam("team-2", "team-2");

        Cluster clusterDev = createCluster("cluster-dev", "https://api.cluster-dev.example.com:6443");
        Cluster clusterProd = createCluster("cluster-prod", "https://api.cluster-prod.example.com:6443");

        Cluster clusterStaging = createCluster("cluster-staging",
                "https://api.cluster-staging.example.com:6443");

        Instant onboardedAt = Instant.now().minus(14, ChronoUnit.DAYS);

        Application appA = createApplication("checkout-api", team1.id,
                "https://github.com/team-1/checkout-api", "quarkus",
                "https://github.com/infra/repo/pull/42", onboardedAt);
        appA.buildClusterId = clusterDev.id;
        appA.buildNamespace = "team-1-checkout-api-build";

        Application appC = createApplication("inventory-service", team1.id,
                "https://github.com/team-1/inventory-service", "quarkus",
                "https://github.com/infra/repo/pull/44",
                onboardedAt.minus(3, ChronoUnit.DAYS));
        appC.buildClusterId = clusterDev.id;
        appC.buildNamespace = "team-1-inventory-service-build";

        Application appD = createApplication("storefront-ui", team1.id,
                "https://github.com/team-1/storefront-ui", "node",
                "https://github.com/infra/repo/pull/45",
                onboardedAt.plus(1, ChronoUnit.DAYS));
        appD.buildClusterId = clusterDev.id;
        appD.buildNamespace = "team-1-storefront-ui-build";

        Application appB = createApplication("payments-worker", team2.id,
                "https://github.com/team-2/payments-worker", "node",
                "https://github.com/infra/repo/pull/43",
                onboardedAt.plus(2, ChronoUnit.DAYS));
        appB.buildClusterId = clusterDev.id;
        appB.buildNamespace = "team-2-payments-worker-build";

        createEnvironment("dev", appA.id, clusterDev.id,
                "team-1-checkout-api-dev", 1, false);
        createEnvironment("staging", appA.id, clusterStaging.id,
                "team-1-checkout-api-staging", 2, false);
        createEnvironment("prod", appA.id, clusterProd.id,
                "team-1-checkout-api-prod", 3, true);

        createEnvironment("dev", appC.id, clusterDev.id,
                "team-1-inventory-service-dev", 1, false);
        createEnvironment("staging", appC.id, clusterStaging.id,
                "team-1-inventory-service-staging", 2, false);
        createEnvironment("prod", appC.id, clusterProd.id,
                "team-1-inventory-service-prod", 3, true);

        createEnvironment("dev", appD.id, clusterDev.id,
                "team-1-storefront-ui-dev", 1, false);
        createEnvironment("prod", appD.id, clusterProd.id,
                "team-1-storefront-ui-prod", 2, true);

        createEnvironment("dev", appB.id, clusterDev.id,
                "team-2-payments-worker-dev", 1, false);
        createEnvironment("prod", appB.id, clusterProd.id,
                "team-2-payments-worker-prod", 2, true);
    }

    private Team findOrCreateTeam(String name, String oidcGroupId) {
        Team existing = Team.findByOidcGroupId(oidcGroupId);
        if (existing != null) {
            return existing;
        }
        Team t = new Team();
        t.name = name;
        t.oidcGroupId = oidcGroupId;
        t.persist();
        return t;
    }

    private Cluster createCluster(String name, String apiServerUrl) {
        Cluster c = new Cluster();
        c.name = name;
        c.apiServerUrl = apiServerUrl;
        c.persist();
        return c;
    }

    private Application createApplication(String name, Long teamId, String gitRepoUrl,
                                           String runtimeType, String prUrl,
                                           Instant onboardedAt) {
        Application a = new Application();
        a.name = name;
        a.teamId = teamId;
        a.gitRepoUrl = gitRepoUrl;
        a.runtimeType = runtimeType;
        a.onboardingPrUrl = prUrl;
        a.onboardedAt = onboardedAt;
        a.persist();
        return a;
    }

    private void createEnvironment(String name, Long appId, Long clusterId,
                                    String namespace, int promotionOrder,
                                    boolean isProduction) {
        Environment e = new Environment();
        e.name = name;
        e.applicationId = appId;
        e.clusterId = clusterId;
        e.namespace = namespace;
        e.promotionOrder = promotionOrder;
        e.isProduction = isProduction;
        e.persist();
    }
}
