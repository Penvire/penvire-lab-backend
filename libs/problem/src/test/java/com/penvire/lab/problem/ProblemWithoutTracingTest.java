package com.penvire.lab.problem;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(ProblemWithoutTracingTest.DisabledTracing.class)
class ProblemWithoutTracingTest {

  public static class DisabledTracing implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of("quarkus.otel.sdk.disabled", "true");
    }
  }

  @Test
  void returnsProblemDetailsWithoutInventingATraceId() {
    given()
        .when()
        .get("/test/problems/conflict")
        .then()
        .statusCode(409)
        .contentType("application/problem+json")
        .header("X-Trace-Id", nullValue())
        .body("status", equalTo(409))
        .body("$", not(hasKey("traceId")));
  }
}
