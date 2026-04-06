package com.portal.environment;

import com.portal.application.Application;
import com.portal.cluster.Cluster;
import com.portal.team.Team;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class EnvironmentEntityIT {

    private Team createTeam(String name, String oidcGroupId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Team team = new Team();
            team.name = name;
            team.oidcGroupId = oidcGroupId;
            team.persist();
            team.flush();
            return team;
        });
    }

    private Cluster createCluster(String name, String apiServerUrl) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Cluster cluster = new Cluster();
            cluster.name = name;
            cluster.apiServerUrl = apiServerUrl;
            cluster.persist();
            cluster.flush();
            return cluster;
        });
    }

    private Application createApplication(String name, Long teamId, String gitRepoUrl) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Application app = new Application();
            app.name = name;
            app.teamId = teamId;
            app.gitRepoUrl = gitRepoUrl;
            app.runtimeType = "quarkus";
            app.persist();
            app.flush();
            return app;
        });
    }

    private Environment createEnvironment(String name, Long applicationId, Long clusterId,
                                          String namespace, int promotionOrder) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Environment env = new Environment();
            env.name = name;
            env.applicationId = applicationId;
            env.clusterId = clusterId;
            env.namespace = namespace;
            env.promotionOrder = promotionOrder;
            env.persist();
            env.flush();
            return env;
        });
    }

    @Test
    void persistAndRetrieveEnvironment() {
        Team team = createTeam("env-persist-team", "env-persist-oidc");
        Cluster cluster = createCluster("env-persist-cluster", "https://api.env-persist.example.com:6443");
        Application app = createApplication("env-persist-app", team.id, "https://github.com/org/app.git");

        Environment env = createEnvironment("dev", app.id, cluster.id, "myapp-dev", 0);

        assertNotNull(env.id);
        assertNotNull(env.createdAt);
        assertNotNull(env.updatedAt);

        Environment found = QuarkusTransaction.requiringNew().call(() -> Environment.findById(env.id));

        assertNotNull(found);
        assertEquals("dev", found.name);
        assertEquals(app.id, found.applicationId);
        assertEquals(cluster.id, found.clusterId);
        assertEquals("myapp-dev", found.namespace);
        assertEquals(0, found.promotionOrder);
    }

    @Test
    void findByApplicationOrderByPromotionOrderReturnsSorted() {
        Team team = createTeam("env-sorted-team", "env-sorted-oidc");
        Cluster cluster = createCluster("env-sorted-cluster", "https://api.env-sorted.example.com:6443");
        Application app = createApplication("env-sorted-app", team.id, "https://github.com/org/sorted.git");

        createEnvironment("prod", app.id, cluster.id, "sorted-prod", 2);
        createEnvironment("dev", app.id, cluster.id, "sorted-dev", 0);
        createEnvironment("staging", app.id, cluster.id, "sorted-staging", 1);

        List<Environment> chain = QuarkusTransaction.requiringNew()
                .call(() -> Environment.findByApplicationOrderByPromotionOrder(app.id));

        assertEquals(3, chain.size());
        assertEquals("dev", chain.get(0).name);
        assertEquals(0, chain.get(0).promotionOrder);
        assertEquals("staging", chain.get(1).name);
        assertEquals(1, chain.get(1).promotionOrder);
        assertEquals("prod", chain.get(2).name);
        assertEquals(2, chain.get(2).promotionOrder);
    }

    @Test
    void uniqueConstraintRejectsDuplicateNamePerApplication() {
        Team team = createTeam("env-dupname-team", "env-dupname-oidc");
        Cluster cluster = createCluster("env-dupname-cluster", "https://api.env-dupname.example.com:6443");
        Application app = createApplication("env-dupname-app", team.id, "https://github.com/org/dupname.git");

        createEnvironment("dev", app.id, cluster.id, "dupname-dev-1", 0);

        assertThrows(PersistenceException.class, () ->
                createEnvironment("dev", app.id, cluster.id, "dupname-dev-2", 1)
        );
    }

    @Test
    void uniqueConstraintRejectsDuplicatePromotionOrderPerApplication() {
        Team team = createTeam("env-duporder-team", "env-duporder-oidc");
        Cluster cluster = createCluster("env-duporder-cluster", "https://api.env-duporder.example.com:6443");
        Application app = createApplication("env-duporder-app", team.id, "https://github.com/org/duporder.git");

        createEnvironment("dev", app.id, cluster.id, "duporder-dev", 0);

        assertThrows(PersistenceException.class, () ->
                createEnvironment("staging", app.id, cluster.id, "duporder-staging", 0)
        );
    }

    @Test
    void promotionChainWithMultipleEnvironments() {
        Team team = createTeam("env-chain-team", "env-chain-oidc");
        Cluster devCluster = createCluster("env-chain-dev-cluster", "https://api.chain-dev.example.com:6443");
        Cluster prodCluster = createCluster("env-chain-prod-cluster", "https://api.chain-prod.example.com:6443");
        Application app = createApplication("env-chain-app", team.id, "https://github.com/org/chain.git");

        createEnvironment("dev", app.id, devCluster.id, "chain-dev", 0);
        createEnvironment("qa", app.id, devCluster.id, "chain-qa", 1);
        createEnvironment("staging", app.id, prodCluster.id, "chain-staging", 2);
        createEnvironment("prod", app.id, prodCluster.id, "chain-prod", 3);

        List<Environment> chain = QuarkusTransaction.requiringNew()
                .call(() -> Environment.findByApplicationOrderByPromotionOrder(app.id));

        assertEquals(4, chain.size());
        for (int i = 0; i < chain.size(); i++) {
            assertEquals(i, chain.get(i).promotionOrder);
        }
        assertEquals("dev", chain.get(0).name);
        assertEquals("qa", chain.get(1).name);
        assertEquals("staging", chain.get(2).name);
        assertEquals("prod", chain.get(3).name);

        assertEquals(devCluster.id, chain.get(0).clusterId);
        assertEquals(devCluster.id, chain.get(1).clusterId);
        assertEquals(prodCluster.id, chain.get(2).clusterId);
        assertEquals(prodCluster.id, chain.get(3).clusterId);
    }
}
