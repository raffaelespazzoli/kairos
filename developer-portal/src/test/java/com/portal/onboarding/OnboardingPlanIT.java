package com.portal.onboarding;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.http.ContentType;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
class OnboardingPlanIT {

    @InjectMock
    OnboardingService onboardingService;

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "default"),
            @Claim(key = "role", value = "member")
    })
    void planReturns200WithNamespacesAndArgoCdApps() {
        when(onboardingService.buildPlan(anyString(), any())).thenReturn(new OnboardingPlanResult(
                "my-app", "default",
                List.of(
                        new PlannedNamespace("default-my-app-build", "ocp-dev-01", "build", true),
                        new PlannedNamespace("default-my-app-dev", "ocp-dev-01", "dev", false),
                        new PlannedNamespace("default-my-app-qa", "ocp-qa-01", "qa", false),
                        new PlannedNamespace("default-my-app-prod", "ocp-prod-01", "prod", false)
                ),
                List.of(
                        new PlannedArgoCdApp("my-app-build", "ocp-dev-01", "default-my-app-build",
                                ".helm/build", "values-build.yaml", true),
                        new PlannedArgoCdApp("my-app-run-dev", "ocp-dev-01", "default-my-app-dev",
                                ".helm/run", "values-run-dev.yaml", false),
                        new PlannedArgoCdApp("my-app-run-qa", "ocp-qa-01", "default-my-app-qa",
                                ".helm/run", "values-run-qa.yaml", false),
                        new PlannedArgoCdApp("my-app-run-prod", "ocp-prod-01", "default-my-app-prod",
                                ".helm/run", "values-run-prod.yaml", false)
                ),
                List.of("dev", "qa", "prod"),
                Map.of("ocp-dev-01/default-my-app-build/namespace.yaml", "---")
        ));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "gitRepoUrl": "https://github.com/team/app",
                            "appName": "my-app",
                            "runtimeType": "Quarkus/Java",
                            "detectedEnvironments": ["dev", "qa", "prod"],
                            "environmentClusterMap": {"dev": 1, "qa": 2, "prod": 3},
                            "buildClusterId": 1
                        }
                        """)
                .when()
                .post("/api/v1/teams/{teamId}/applications/onboard/plan", "default")
                .then()
                .statusCode(200)
                .body("appName", equalTo("my-app"))
                .body("teamName", equalTo("default"))
                .body("namespaces.size()", is(4))
                .body("argoCdApps.size()", is(4))
                .body("promotionChain", contains("dev", "qa", "prod"))
                .body("namespaces[0].name", equalTo("default-my-app-build"))
                .body("namespaces[0].isBuild", is(true));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "default"),
            @Claim(key = "role", value = "member")
    })
    void planWithInvalidClusterReturns404() {
        when(onboardingService.buildPlan(anyString(), any()))
                .thenThrow(new NotFoundException("Cluster with ID 999 not found"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "gitRepoUrl": "https://github.com/team/app",
                            "appName": "my-app",
                            "runtimeType": "Quarkus/Java",
                            "detectedEnvironments": ["dev"],
                            "environmentClusterMap": {"dev": 999},
                            "buildClusterId": 999
                        }
                        """)
                .when()
                .post("/api/v1/teams/{teamId}/applications/onboard/plan", "default")
                .then()
                .statusCode(404);
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
                        {
                            "gitRepoUrl": "https://github.com/team/app",
                            "appName": "my-app",
                            "runtimeType": "Quarkus/Java",
                            "detectedEnvironments": ["dev"],
                            "environmentClusterMap": {"dev": 1},
                            "buildClusterId": 1
                        }
                        """)
                .when()
                .post("/api/v1/teams/{teamId}/applications/onboard/plan", "other-team")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "default"),
            @Claim(key = "role", value = "member")
    })
    void planWithMissingFieldsReturns400() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "gitRepoUrl": "",
                            "appName": "",
                            "runtimeType": "",
                            "detectedEnvironments": null,
                            "environmentClusterMap": null,
                            "buildClusterId": null
                        }
                        """)
                .when()
                .post("/api/v1/teams/{teamId}/applications/onboard/plan", "default")
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "lead@example.com", roles = "lead")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "default"),
            @Claim(key = "role", value = "lead")
    })
    void leadCanBuildPlan() {
        when(onboardingService.buildPlan(anyString(), any())).thenReturn(new OnboardingPlanResult(
                "app", "default", List.of(), List.of(), List.of(), Map.of()));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "gitRepoUrl": "https://github.com/team/app",
                            "appName": "app",
                            "runtimeType": "Java",
                            "detectedEnvironments": [],
                            "environmentClusterMap": {},
                            "buildClusterId": 1
                        }
                        """)
                .when()
                .post("/api/v1/teams/{teamId}/applications/onboard/plan", "default")
                .then()
                .statusCode(200);
    }
}
