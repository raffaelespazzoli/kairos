package com.portal.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import org.junit.jupiter.api.Test;

import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for the PermissionFilter running in the full Quarkus stack.
 * Uses {@link AuthTestStubResource} to provide route targets for admin/clusters
 * and deployment endpoints that do not exist in production code yet.
 */
@QuarkusTest
class PermissionFilterIT {

    // --- AC #3: PermissionFilter enforces Casbin on every request ---

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "payments"),
            @Claim(key = "role", value = "member")
    })
    void memberAllowedReadTeams() {
        given()
                .when().get("/api/v1/teams")
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "lead@example.com", roles = "lead")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "payments"),
            @Claim(key = "role", value = "lead")
    })
    void leadAllowedReadTeams() {
        given()
                .when().get("/api/v1/teams")
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "admin@example.com", roles = "admin")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "platform"),
            @Claim(key = "role", value = "admin")
    })
    void adminAllowedReadTeams() {
        given()
                .when().get("/api/v1/teams")
                .then()
                .statusCode(200);
    }

    // --- Members can read clusters but cannot modify them ---

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "payments"),
            @Claim(key = "role", value = "member")
    })
    void memberAllowedReadClusters() {
        given()
                .when().get("/api/v1/admin/clusters")
                .then()
                .statusCode(200);
    }

    // --- AC #5: Members cannot modify admin resources → 403 ---

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "payments"),
            @Claim(key = "role", value = "member")
    })
    void memberDeniedCreateClusters() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"test\",\"apiServerUrl\":\"https://api:6443\"}")
                .when().post("/api/v1/admin/clusters")
                .then()
                .statusCode(403)
                .body("error", equalTo("forbidden"))
                .body("message", containsString("permission"))
                .body("system", equalTo("portal"))
                .body("timestamp", notNullValue());
    }

    @Test
    @TestSecurity(user = "admin@example.com", roles = "admin")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "platform"),
            @Claim(key = "role", value = "admin")
    })
    void adminCanAccessAdminClusters() {
        given()
                .when().get("/api/v1/admin/clusters")
                .then()
                .statusCode(200);
    }

    // --- AC #6: Leads can deploy to production ---

    @Test
    @TestSecurity(user = "lead@example.com", roles = "lead")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "payments"),
            @Claim(key = "role", value = "lead")
    })
    void leadAllowedProdDeployment() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/api/v1/teams/1/applications/1/deployments?env=prod")
                .then()
                .statusCode(201);
    }

    // --- AC #7: Members cannot deploy to production → 403 ---

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "payments"),
            @Claim(key = "role", value = "member")
    })
    void memberDeniedProdDeployment() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/api/v1/teams/1/applications/1/deployments?env=prod")
                .then()
                .statusCode(403)
                .body("error", equalTo("forbidden"));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "payments"),
            @Claim(key = "role", value = "member")
    })
    void memberAllowedNonProdDeployment() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/api/v1/teams/1/applications/1/deployments")
                .then()
                .statusCode(201);
    }

    // --- Non-API path is not filtered ---

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "payments"),
            @Claim(key = "role", value = "member")
    })
    void healthEndpointNotFiltered() {
        given()
                .when().get("/q/health/ready")
                .then()
                .statusCode(anyOf(equalTo(200), equalTo(503)));
    }

    // --- AC #4: Cross-team isolation returns 404 ---

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "payments"),
            @Claim(key = "role", value = "member")
    })
    void crossTeamIsolationReturnsOnlyOwnTeamData() {
        given()
                .when().get("/api/v1/teams")
                .then()
                .statusCode(200)
                .body("$.size()", equalTo(1))
                .body("[0].oidcGroupId", equalTo("payments"));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "payments"),
            @Claim(key = "role", value = "member")
    })
    void crossTeamIdAccessReturns404() {
        given()
                .when().get("/api/v1/teams/999999")
                .then()
                .statusCode(404);
    }

    // --- Filter pipeline order verification ---

    @Test
    @TestSecurity(authorizationEnabled = true)
    void unauthenticatedRequestReturns401() {
        given()
                .when().get("/api/v1/teams")
                .then()
                .statusCode(401);
    }

    // --- Error response format (AC #3) via AuthorizationExceptionMapper ---

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "payments"),
            @Claim(key = "role", value = "member")
    })
    void forbiddenResponseFollowsStandardErrorFormat() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"test\",\"apiServerUrl\":\"https://api:6443\"}")
                .when().post("/api/v1/admin/clusters")
                .then()
                .statusCode(403)
                .body("error", equalTo("forbidden"))
                .body("message", notNullValue())
                .body("detail", notNullValue())
                .body("system", equalTo("portal"))
                .body("timestamp", notNullValue());
    }
}
