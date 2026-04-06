package com.portal.onboarding;

import com.portal.application.Application;
import com.portal.cluster.Cluster;
import com.portal.environment.Environment;
import com.portal.integration.PortalIntegrationException;
import com.portal.integration.git.GitProvider;
import com.portal.integration.git.model.PullRequest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@QuarkusTest
class OnboardingResourceIT {

    @InjectMock
    GitProvider gitProvider;

    @Inject
    EntityManager em;

    private Long cluster1Id;
    private Long cluster2Id;
    private Long cluster3Id;

    @BeforeEach
    @Transactional
    void seedClusters() {
        Environment.deleteAll();
        Application.deleteAll();

        cluster1Id = findOrCreateCluster("ocp-dev-it", "https://api.ocp-dev-it:6443");
        cluster2Id = findOrCreateCluster("ocp-qa-it", "https://api.ocp-qa-it:6443");
        cluster3Id = findOrCreateCluster("ocp-prod-it", "https://api.ocp-prod-it:6443");
    }

    @AfterEach
    @Transactional
    void cleanup() {
        Environment.deleteAll();
        Application.deleteAll();
    }

    private Long findOrCreateCluster(String name, String apiUrl) {
        Cluster existing = Cluster.findByName(name);
        if (existing != null) {
            return existing.id;
        }
        Cluster c = new Cluster();
        c.name = name;
        c.apiServerUrl = apiUrl;
        c.persist();
        return c.id;
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "default"),
            @Claim(key = "role", value = "member")
    })
    void validateReturns200WithContractResult() {
        doNothing().when(gitProvider).validateRepoAccess(anyString());
        when(gitProvider.listDirectory(anyString(), anyString(), eq(".helm/build")))
                .thenReturn(List.of("Chart.yaml", "templates"));
        when(gitProvider.listDirectory(anyString(), anyString(), eq(".helm/run")))
                .thenReturn(List.of("Chart.yaml", "templates"));
        when(gitProvider.readFile(anyString(), anyString(), eq(".helm/values-build.yaml")))
                .thenReturn("key: value");
        when(gitProvider.listDirectory(anyString(), anyString(), eq(".helm")))
                .thenReturn(List.of("values-run-dev.yaml", "values-run-qa.yaml"));
        when(gitProvider.listDirectory(anyString(), anyString(), eq("")))
                .thenReturn(List.of("pom.xml", "src"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"gitRepoUrl": "https://github.com/team/app"}
                        """)
                .when()
                .post("/api/v1/teams/{teamId}/applications/onboard", "default")
                .then()
                .statusCode(200)
                .body("allPassed", is(true))
                .body("checks.size()", is(5))
                .body("runtimeType", equalTo("Quarkus/Java"))
                .body("detectedEnvironments", hasItems("dev", "qa"));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "default"),
            @Claim(key = "role", value = "member")
    })
    void repoUnreachableReturns502() {
        doThrow(new PortalIntegrationException("git", "validateRepoAccess",
                "Cannot access repository — check the URL and ensure the portal has read access"))
                .when(gitProvider).validateRepoAccess(anyString());

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"gitRepoUrl": "https://github.com/team/nonexistent"}
                        """)
                .when()
                .post("/api/v1/teams/{teamId}/applications/onboard", "default")
                .then()
                .statusCode(502)
                .body("error", equalTo("integration-error"));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "default"),
            @Claim(key = "role", value = "member")
    })
    void missingGitRepoUrlReturns400() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"gitRepoUrl": ""}
                        """)
                .when()
                .post("/api/v1/teams/{teamId}/applications/onboard", "default")
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "default"),
            @Claim(key = "role", value = "member")
    })
    void crossTeamAccessReturns404() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"gitRepoUrl": "https://github.com/team/app"}
                        """)
                .when()
                .post("/api/v1/teams/{teamId}/applications/onboard", "other-team")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "lead@example.com", roles = "lead")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "default"),
            @Claim(key = "role", value = "lead")
    })
    void leadCanOnboard() {
        doNothing().when(gitProvider).validateRepoAccess(anyString());
        when(gitProvider.listDirectory(anyString(), anyString(), eq(".helm/build")))
                .thenReturn(List.of("Chart.yaml"));
        when(gitProvider.listDirectory(anyString(), anyString(), eq(".helm/run")))
                .thenReturn(List.of("Chart.yaml"));
        when(gitProvider.readFile(anyString(), anyString(), eq(".helm/values-build.yaml")))
                .thenReturn("");
        when(gitProvider.listDirectory(anyString(), anyString(), eq(".helm")))
                .thenReturn(List.of("values-run-dev.yaml"));
        when(gitProvider.listDirectory(anyString(), anyString(), eq("")))
                .thenReturn(List.of("pom.xml"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"gitRepoUrl": "https://github.com/team/app"}
                        """)
                .when()
                .post("/api/v1/teams/{teamId}/applications/onboard", "default")
                .then()
                .statusCode(200)
                .body("allPassed", is(true));
    }

    @Test
    @TestSecurity(user = "admin@example.com", roles = "admin")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "default"),
            @Claim(key = "role", value = "admin")
    })
    void adminCanOnboard() {
        doNothing().when(gitProvider).validateRepoAccess(anyString());
        when(gitProvider.listDirectory(anyString(), anyString(), eq(".helm/build")))
                .thenReturn(List.of("Chart.yaml"));
        when(gitProvider.listDirectory(anyString(), anyString(), eq(".helm/run")))
                .thenReturn(List.of("Chart.yaml"));
        when(gitProvider.readFile(anyString(), anyString(), eq(".helm/values-build.yaml")))
                .thenReturn("");
        when(gitProvider.listDirectory(anyString(), anyString(), eq(".helm")))
                .thenReturn(List.of("values-run-dev.yaml"));
        when(gitProvider.listDirectory(anyString(), anyString(), eq("")))
                .thenReturn(List.of("pom.xml"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"gitRepoUrl": "https://github.com/team/app"}
                        """)
                .when()
                .post("/api/v1/teams/{teamId}/applications/onboard", "default")
                .then()
                .statusCode(200)
                .body("allPassed", is(true));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "default"),
            @Claim(key = "role", value = "member")
    })
    void confirmReturns201WithPersistedApplication() {
        when(gitProvider.createPullRequest(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new PullRequest("https://git.example.com/pr/1", 1, "Onboard"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "appName": "my-app",
                            "gitRepoUrl": "https://github.com/team/app",
                            "runtimeType": "Quarkus/Java",
                            "detectedEnvironments": ["dev", "qa", "prod"],
                            "environmentClusterMap": {"dev": %d, "qa": %d, "prod": %d},
                            "buildClusterId": %d
                        }
                        """.formatted(cluster1Id, cluster2Id, cluster3Id, cluster1Id))
                .when()
                .post("/api/v1/teams/{teamId}/applications/onboard/confirm", "default")
                .then()
                .statusCode(201)
                .body("applicationName", is("my-app"))
                .body("onboardingPrUrl", is("https://git.example.com/pr/1"))
                .body("namespacesCreated", greaterThanOrEqualTo(3))
                .body("argoCdAppsCreated", greaterThanOrEqualTo(3))
                .body("promotionChain", hasItems("dev", "qa", "prod"));

        List<Application> apps = Application.list("name", "my-app");
        assertThat(apps, hasSize(1));
        assertThat(apps.get(0).onboardingPrUrl, is("https://git.example.com/pr/1"));
        assertThat(apps.get(0).onboardedAt, notNullValue());

        List<Environment> envs = Environment.findByApplicationOrderByPromotionOrder(apps.get(0).id);
        assertThat(envs, hasSize(3));
        assertThat(envs.get(0).name, is("dev"));
        assertThat(envs.get(0).promotionOrder, is(0));
        assertThat(envs.get(1).name, is("qa"));
        assertThat(envs.get(2).name, is("prod"));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "default"),
            @Claim(key = "role", value = "member")
    })
    void confirmGitFailureReturns502() {
        doThrow(new PortalIntegrationException("git", "createBranch", "Git server error"))
                .when(gitProvider).createBranch(anyString(), anyString(), anyString());

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "appName": "fail-app",
                            "gitRepoUrl": "https://github.com/team/app",
                            "runtimeType": "Quarkus/Java",
                            "detectedEnvironments": ["dev"],
                            "environmentClusterMap": {"dev": %d},
                            "buildClusterId": %d
                        }
                        """.formatted(cluster1Id, cluster1Id))
                .when()
                .post("/api/v1/teams/{teamId}/applications/onboard/confirm", "default")
                .then()
                .statusCode(502)
                .body("error", is("integration-error"));

        List<Application> apps = Application.list("name", "fail-app");
        assertThat(apps, hasSize(0));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "default"),
            @Claim(key = "role", value = "member")
    })
    void confirmCrossTeamAccessReturns404() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "appName": "my-app",
                            "gitRepoUrl": "https://github.com/team/app",
                            "runtimeType": "Quarkus/Java",
                            "detectedEnvironments": ["dev"],
                            "environmentClusterMap": {"dev": %d},
                            "buildClusterId": %d
                        }
                        """.formatted(cluster1Id, cluster1Id))
                .when()
                .post("/api/v1/teams/{teamId}/applications/onboard/confirm", "other-team")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "default"),
            @Claim(key = "role", value = "member")
    })
    void confirmDuplicateAppNameReturns409() {
        when(gitProvider.createPullRequest(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new PullRequest("https://git.example.com/pr/1", 1, "Onboard"));

        String body = """
                {
                    "appName": "dup-app",
                    "gitRepoUrl": "https://github.com/team/app",
                    "runtimeType": "Quarkus/Java",
                    "detectedEnvironments": ["dev"],
                    "environmentClusterMap": {"dev": %d},
                    "buildClusterId": %d
                }
                """.formatted(cluster1Id, cluster1Id);

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/v1/teams/{teamId}/applications/onboard/confirm", "default")
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/v1/teams/{teamId}/applications/onboard/confirm", "default")
                .then()
                .statusCode(409)
                .body("error", is("conflict"));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "default"),
            @Claim(key = "role", value = "member")
    })
    void someChecksFailed() {
        doNothing().when(gitProvider).validateRepoAccess(anyString());
        when(gitProvider.listDirectory(anyString(), anyString(), eq(".helm/build")))
                .thenReturn(List.of("templates"));
        when(gitProvider.listDirectory(anyString(), anyString(), eq(".helm/run")))
                .thenReturn(List.of("Chart.yaml"));
        when(gitProvider.readFile(anyString(), anyString(), eq(".helm/values-build.yaml")))
                .thenReturn("");
        when(gitProvider.listDirectory(anyString(), anyString(), eq(".helm")))
                .thenReturn(List.of("values-run-dev.yaml"));
        when(gitProvider.listDirectory(anyString(), anyString(), eq("")))
                .thenReturn(List.of("pom.xml"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"gitRepoUrl": "https://github.com/team/app"}
                        """)
                .when()
                .post("/api/v1/teams/{teamId}/applications/onboard", "default")
                .then()
                .statusCode(200)
                .body("allPassed", is(false))
                .body("checks.size()", is(5))
                .body("checks[0].passed", is(false))
                .body("checks[0].fixInstruction", notNullValue());
    }
}
