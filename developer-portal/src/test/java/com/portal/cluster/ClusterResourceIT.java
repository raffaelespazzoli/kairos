package com.portal.cluster;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class ClusterResourceIT {

    // --- AC #2: POST creates cluster → 201 ---

    @Test
    @TestSecurity(user = "admin@example.com", roles = "admin")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "platform"),
            @Claim(key = "role", value = "admin")
    })
    void createClusterReturns201() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "ocp-dev-01", "apiServerUrl": "https://api.ocp-dev-01.example.com:6443"}
                        """)
                .when().post("/api/v1/admin/clusters")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("name", equalTo("ocp-dev-01"))
                .body("apiServerUrl", equalTo("https://api.ocp-dev-01.example.com:6443"))
                .body("createdAt", notNullValue())
                .body("updatedAt", notNullValue());
    }

    // --- AC #3: GET lists clusters → 200 ---

    @Test
    @TestSecurity(user = "admin@example.com", roles = "admin")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "platform"),
            @Claim(key = "role", value = "admin")
    })
    void listClustersReturns200() {
        // Create a cluster first
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "ocp-list-test", "apiServerUrl": "https://api.list-test.example.com:6443"}
                        """)
                .when().post("/api/v1/admin/clusters")
                .then().statusCode(201);

        given()
                .when().get("/api/v1/admin/clusters")
                .then()
                .statusCode(200)
                .body("$.size()", greaterThanOrEqualTo(1))
                .body("name", hasItem("ocp-list-test"))
                .body("apiServerUrl", hasItem("https://api.list-test.example.com:6443"));
    }

    // --- AC #4: PUT updates cluster → 200 ---

    @Test
    @TestSecurity(user = "admin@example.com", roles = "admin")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "platform"),
            @Claim(key = "role", value = "admin")
    })
    void updateClusterReturns200() {
        int clusterId = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "ocp-update-test", "apiServerUrl": "https://api.update.example.com:6443"}
                        """)
                .when().post("/api/v1/admin/clusters")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "ocp-updated", "apiServerUrl": "https://api.updated.example.com:6443"}
                        """)
                .when().put("/api/v1/admin/clusters/" + clusterId)
                .then()
                .statusCode(200)
                .body("name", equalTo("ocp-updated"))
                .body("apiServerUrl", equalTo("https://api.updated.example.com:6443"));
    }

    // --- AC #5: DELETE removes cluster → 204 ---

    @Test
    @TestSecurity(user = "admin@example.com", roles = "admin")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "platform"),
            @Claim(key = "role", value = "admin")
    })
    void deleteClusterReturns204() {
        int clusterId = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "ocp-delete-test", "apiServerUrl": "https://api.delete.example.com:6443"}
                        """)
                .when().post("/api/v1/admin/clusters")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .when().delete("/api/v1/admin/clusters/" + clusterId)
                .then().statusCode(204);

        // Verify it's gone
        given()
                .when().get("/api/v1/admin/clusters")
                .then()
                .statusCode(200)
                .body("name", not(hasItem("ocp-delete-test")));
    }

    // --- AC #6: Duplicate name → 400 ---

    @Test
    @TestSecurity(user = "admin@example.com", roles = "admin")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "platform"),
            @Claim(key = "role", value = "admin")
    })
    void duplicateNameReturns400() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "ocp-dup-test", "apiServerUrl": "https://api.dup1.example.com:6443"}
                        """)
                .when().post("/api/v1/admin/clusters")
                .then().statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "ocp-dup-test", "apiServerUrl": "https://api.dup2.example.com:6443"}
                        """)
                .when().post("/api/v1/admin/clusters")
                .then()
                .statusCode(400)
                .body("error", equalTo("validation-error"))
                .body("detail", containsString("already exists"));
    }

    // --- AC #7: Non-admin access denied → 403 ---

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "payments"),
            @Claim(key = "role", value = "member")
    })
    void memberDeniedAccess() {
        given()
                .when().get("/api/v1/admin/clusters")
                .then()
                .statusCode(403)
                .body("error", equalTo("forbidden"));
    }

    @Test
    @TestSecurity(user = "lead@example.com", roles = "lead")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "payments"),
            @Claim(key = "role", value = "lead")
    })
    void leadDeniedAccess() {
        given()
                .when().get("/api/v1/admin/clusters")
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
    void memberDeniedPostAccess() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "blocked-cluster", "apiServerUrl": "https://api.blocked.example.com:6443"}
                        """)
                .when().post("/api/v1/admin/clusters")
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
    void memberDeniedPutAccess() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "blocked", "apiServerUrl": "https://api.blocked.example.com:6443"}
                        """)
                .when().put("/api/v1/admin/clusters/1")
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
    void memberDeniedDeleteAccess() {
        given()
                .when().delete("/api/v1/admin/clusters/1")
                .then()
                .statusCode(403)
                .body("error", equalTo("forbidden"));
    }

    @Test
    @TestSecurity(user = "lead@example.com", roles = "lead")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "payments"),
            @Claim(key = "role", value = "lead")
    })
    void leadDeniedPostAccess() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "blocked-cluster", "apiServerUrl": "https://api.blocked.example.com:6443"}
                        """)
                .when().post("/api/v1/admin/clusters")
                .then()
                .statusCode(403)
                .body("error", equalTo("forbidden"));
    }

    @Test
    @TestSecurity(user = "lead@example.com", roles = "lead")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "payments"),
            @Claim(key = "role", value = "lead")
    })
    void leadDeniedPutAccess() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "blocked", "apiServerUrl": "https://api.blocked.example.com:6443"}
                        """)
                .when().put("/api/v1/admin/clusters/1")
                .then()
                .statusCode(403)
                .body("error", equalTo("forbidden"));
    }

    @Test
    @TestSecurity(user = "lead@example.com", roles = "lead")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "payments"),
            @Claim(key = "role", value = "lead")
    })
    void leadDeniedDeleteAccess() {
        given()
                .when().delete("/api/v1/admin/clusters/1")
                .then()
                .statusCode(403)
                .body("error", equalTo("forbidden"));
    }

    // --- Non-existent cluster → 404 ---

    @Test
    @TestSecurity(user = "admin@example.com", roles = "admin")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "platform"),
            @Claim(key = "role", value = "admin")
    })
    void updateNonExistentClusterReturns404() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "ghost", "apiServerUrl": "https://api.ghost.example.com:6443"}
                        """)
                .when().put("/api/v1/admin/clusters/999999")
                .then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "admin@example.com", roles = "admin")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "platform"),
            @Claim(key = "role", value = "admin")
    })
    void deleteNonExistentClusterReturns404() {
        given()
                .when().delete("/api/v1/admin/clusters/999999")
                .then().statusCode(404);
    }

    // --- Validation: missing/invalid fields → 400 ---

    @Test
    @TestSecurity(user = "admin@example.com", roles = "admin")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "platform"),
            @Claim(key = "role", value = "admin")
    })
    void missingNameReturns400() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "", "apiServerUrl": "https://api.example.com:6443"}
                        """)
                .when().post("/api/v1/admin/clusters")
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "admin@example.com", roles = "admin")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "platform"),
            @Claim(key = "role", value = "admin")
    })
    void nonHttpsUrlReturns400() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "ocp-insecure", "apiServerUrl": "http://api.insecure.example.com:6443"}
                        """)
                .when().post("/api/v1/admin/clusters")
                .then()
                .statusCode(400);
    }
}
