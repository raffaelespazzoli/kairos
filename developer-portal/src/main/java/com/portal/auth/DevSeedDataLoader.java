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

        Instant onboardedAt = Instant.now().minus(14, ChronoUnit.DAYS);

        Application appA = createApplication("application-a", team1.id,
                "https://github.com/team-1/application-a", "quarkus",
                "https://github.com/infra/repo/pull/42", onboardedAt);
        appA.buildClusterId = clusterDev.id;
        appA.buildNamespace = "team-1-application-a-build";

        Application appB = createApplication("application-b", team2.id,
                "https://github.com/team-2/application-b", "node",
                "https://github.com/infra/repo/pull/43",
                onboardedAt.plus(2, ChronoUnit.DAYS));
        appB.buildClusterId = clusterDev.id;
        appB.buildNamespace = "team-2-application-b-build";

        createEnvironment("dev", appA.id, clusterDev.id,
                "team-1-application-a-dev", 1);
        createEnvironment("prod", appA.id, clusterProd.id,
                "team-1-application-a-prod", 2);

        createEnvironment("dev", appB.id, clusterDev.id,
                "team-2-application-b-dev", 1);
        createEnvironment("prod", appB.id, clusterProd.id,
                "team-2-application-b-prod", 2);
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
                                    String namespace, int promotionOrder) {
        Environment e = new Environment();
        e.name = name;
        e.applicationId = appId;
        e.clusterId = clusterId;
        e.namespace = namespace;
        e.promotionOrder = promotionOrder;
        e.persist();
    }
}
