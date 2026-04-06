package com.portal.common;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import org.junit.jupiter.api.Test;

import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for {@link GlobalExceptionMapper}.
 * Uses ExceptionMapperTestStubResource at /api/v1/admin/test-exceptions/
 * for direct exception testing, and the real PermissionFilter path
 * for authorization exception testing.
 */
@QuarkusTest
class GlobalExceptionMapperIT {

    @Test
    @TestSecurity(user = "admin@example.com", roles = "admin")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "platform"),
            @Claim(key = "role", value = "admin")
    })
    void integrationExceptionReturns502WithStandardFormat() {
        given()
                .when().get("/api/v1/admin/test-exceptions/integration")
                .then()
                .statusCode(502)
                .body("error", equalTo("integration-error"))
                .body("message", equalTo("ArgoCD sync failed: connection timeout"))
                .body("detail", containsString("sync-application"))
                .body("system", equalTo("argocd"))
                .body("deepLink", equalTo("https://argocd.internal/applications/my-app-qa"))
                .body("timestamp", notNullValue());
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "payments"),
            @Claim(key = "role", value = "member")
    })
    void authorizationExceptionReturns403WithStandardFormat() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"test\",\"apiServerUrl\":\"https://api:6443\"}")
                .when().post("/api/v1/admin/clusters")
                .then()
                .statusCode(403)
                .body("error", equalTo("forbidden"))
                .body("message", containsString("permission"))
                .body("detail", notNullValue())
                .body("system", equalTo("portal"))
                .body("timestamp", notNullValue());
    }

    @Test
    @TestSecurity(user = "admin@example.com", roles = "admin")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "platform"),
            @Claim(key = "role", value = "admin")
    })
    void validationExceptionReturns400WithStandardFormat() {
        given()
                .when().get("/api/v1/admin/test-exceptions/validation")
                .then()
                .statusCode(400)
                .body("error", equalTo("validation-error"))
                .body("message", equalTo("Invalid request data"))
                .body("detail", containsString("appName must not be blank"))
                .body("system", equalTo("portal"))
                .body("timestamp", notNullValue());
    }

    @Test
    @TestSecurity(user = "admin@example.com", roles = "admin")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "platform"),
            @Claim(key = "role", value = "admin")
    })
    void notFoundExceptionReturns404WithStandardFormat() {
        given()
                .when().get("/api/v1/admin/test-exceptions/not-found")
                .then()
                .statusCode(404)
                .body("error", equalTo("not-found"))
                .body("message", containsString("not found"))
                .body("system", equalTo("portal"))
                .body("timestamp", notNullValue());
    }

    @Test
    @TestSecurity(user = "admin@example.com", roles = "admin")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "platform"),
            @Claim(key = "role", value = "admin")
    })
    void unexpectedExceptionReturns500WithStandardFormat() {
        given()
                .when().get("/api/v1/admin/test-exceptions/unexpected")
                .then()
                .statusCode(500)
                .body("error", equalTo("internal-error"))
                .body("message", equalTo("An unexpected error occurred"))
                .body("detail", equalTo("Something unexpected happened"))
                .body("system", equalTo("portal"))
                .body("timestamp", notNullValue());
    }
}
