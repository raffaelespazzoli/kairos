package com.portal.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CasbinEnforcerTest {

    private CasbinEnforcer enforcer;

    @BeforeEach
    void setUp() {
        enforcer = new CasbinEnforcer();
        enforcer.init();
    }

    // --- Member permissions ---

    @Test
    void memberCanReadTeams() {
        assertTrue(enforcer.enforce("member", "teams", "read"));
    }

    @Test
    void memberCanReadApplications() {
        assertTrue(enforcer.enforce("member", "applications", "read"));
    }

    @Test
    void memberCanOnboardApplications() {
        assertTrue(enforcer.enforce("member", "applications", "onboard"));
    }

    @Test
    void memberCanReadEnvironments() {
        assertTrue(enforcer.enforce("member", "environments", "read"));
    }

    @Test
    void memberCanReadBuilds() {
        assertTrue(enforcer.enforce("member", "builds", "read"));
    }

    @Test
    void memberCanTriggerBuilds() {
        assertTrue(enforcer.enforce("member", "builds", "trigger"));
    }

    @Test
    void memberCanReadReleases() {
        assertTrue(enforcer.enforce("member", "releases", "read"));
    }

    @Test
    void memberCanCreateReleases() {
        assertTrue(enforcer.enforce("member", "releases", "create"));
    }

    @Test
    void memberCanReadDeployments() {
        assertTrue(enforcer.enforce("member", "deployments", "read"));
    }

    @Test
    void memberCanDeployToNonProd() {
        assertTrue(enforcer.enforce("member", "deployments", "deploy"));
    }

    @Test
    void memberCanReadHealth() {
        assertTrue(enforcer.enforce("member", "health", "read"));
    }

    @Test
    void memberCanReadDora() {
        assertTrue(enforcer.enforce("member", "dora", "read"));
    }

    @Test
    void memberCanReadDashboard() {
        assertTrue(enforcer.enforce("member", "dashboard", "read"));
    }

    // --- Member denied actions ---

    @Test
    void memberCannotDeployToProd() {
        assertFalse(enforcer.enforce("member", "deployments", "deploy-prod"));
    }

    @Test
    void memberCanReadClusters() {
        assertTrue(enforcer.enforce("member", "clusters", "read"));
    }

    @Test
    void memberCannotModifyClusters() {
        assertFalse(enforcer.enforce("member", "clusters", "create"));
        assertFalse(enforcer.enforce("member", "clusters", "update"));
        assertFalse(enforcer.enforce("member", "clusters", "delete"));
    }

    // --- Lead permissions (inherits member + production deploy) ---

    @Test
    void leadCanDeployToProd() {
        assertTrue(enforcer.enforce("lead", "deployments", "deploy-prod"));
    }

    @Test
    void leadInheritsMemberReadPermissions() {
        assertTrue(enforcer.enforce("lead", "applications", "read"));
        assertTrue(enforcer.enforce("lead", "builds", "read"));
        assertTrue(enforcer.enforce("lead", "deployments", "read"));
        assertTrue(enforcer.enforce("lead", "health", "read"));
    }

    @Test
    void leadInheritsMemberWritePermissions() {
        assertTrue(enforcer.enforce("lead", "applications", "onboard"));
        assertTrue(enforcer.enforce("lead", "builds", "trigger"));
        assertTrue(enforcer.enforce("lead", "releases", "create"));
        assertTrue(enforcer.enforce("lead", "deployments", "deploy"));
    }

    @Test
    void leadCannotCrudClusters() {
        assertFalse(enforcer.enforce("lead", "clusters", "create"));
        assertFalse(enforcer.enforce("lead", "clusters", "update"));
        assertFalse(enforcer.enforce("lead", "clusters", "delete"));
    }

    // --- Admin permissions (inherits lead + cluster CRUD) ---

    @Test
    void adminCanCrudClusters() {
        assertTrue(enforcer.enforce("admin", "clusters", "create"));
        assertTrue(enforcer.enforce("admin", "clusters", "read"));
        assertTrue(enforcer.enforce("admin", "clusters", "update"));
        assertTrue(enforcer.enforce("admin", "clusters", "delete"));
    }

    @Test
    void adminInheritsAllLeadPermissions() {
        assertTrue(enforcer.enforce("admin", "deployments", "deploy-prod"));
        assertTrue(enforcer.enforce("admin", "deployments", "deploy"));
        assertTrue(enforcer.enforce("admin", "applications", "read"));
        assertTrue(enforcer.enforce("admin", "builds", "trigger"));
    }

    // --- Unknown role denied ---

    @Test
    void unknownRoleDenied() {
        assertFalse(enforcer.enforce("guest", "applications", "read"));
        assertFalse(enforcer.enforce("", "applications", "read"));
    }

    // --- Non-existent resource/action denied ---

    @Test
    void nonExistentResourceDenied() {
        assertFalse(enforcer.enforce("admin", "nonexistent", "read"));
    }

    @Test
    void nonExistentActionDenied() {
        assertFalse(enforcer.enforce("admin", "applications", "nonexistent"));
    }
}
