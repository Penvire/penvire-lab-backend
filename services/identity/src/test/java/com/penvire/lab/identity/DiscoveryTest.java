package com.penvire.lab.identity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(DiscoveryTest.LocalIssuer.class)
class DiscoveryTest {
  private static final String PORT = "18081";
  private static final String ISSUER = "http://localhost:" + PORT;

  @Test
  void discoversTheConfiguredIssuerAndFollowsItsJwksUri() {
    String jwksUri =
        given()
            .when()
            .get("/.well-known/openid-configuration")
            .then()
            .statusCode(200)
            .contentType("application/json")
            .body("issuer", equalTo(ISSUER))
            .body("jwks_uri", not(blankOrNullString()))
            .extract()
            .path("jwks_uri");

    URI keys = URI.create(jwksUri);
    assertTrue(keys.isAbsolute(), "jwks_uri must be absolute");
    assertEquals(URI.create(ISSUER + "/"), keys.resolve("/"));

    given()
        .redirects()
        .follow(false)
        .when()
        .get(keys)
        .then()
        .statusCode(200)
        .contentType("application/json")
        .body("keys", instanceOf(List.class));
  }

  public static class LocalIssuer implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of("quarkus.http.test-port", PORT, "penvire.identity.issuer", ISSUER);
    }
  }
}
