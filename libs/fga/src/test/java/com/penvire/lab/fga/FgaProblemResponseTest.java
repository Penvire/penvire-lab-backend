package com.penvire.lab.fga;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FgaProblemResponseTest {

  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";

  @Test
  void mapsFgaUnavailableExceptionToSafeServiceUnavailableProblem() {
    given()
        .header("traceparent", TRACEPARENT)
        .when()
        .get("/test/fga/unavailable")
        .then()
        .statusCode(503)
        .contentType("application/problem+json")
        .header("X-Trace-Id", TRACE_ID)
        .body("type", equalTo("urn:penvire:problem:fga.unavailable"))
        .body("status", equalTo(503))
        .body("title", equalTo("Authorization service unavailable"))
        .body("traceId", equalTo(TRACE_ID))
        .body(not(containsString("OpenFGA check")))
        .body(not(containsString("HTTP 503")))
        .body("$", not(hasKey("detail")))
        .body("$", not(hasKey("stackTrace")));
  }

  @Test
  void mapsPlainFgaExceptionToAuthorizationErrorProblem() {
    given()
        .header("traceparent", TRACEPARENT)
        .when()
        .get("/test/fga/error")
        .then()
        .statusCode(500)
        .contentType("application/problem+json")
        .header("X-Trace-Id", TRACE_ID)
        .body("type", equalTo("urn:penvire:problem:fga.error"))
        .body("title", equalTo("Authorization error"))
        .body("status", equalTo(500))
        .body("traceId", equalTo(TRACE_ID))
        .body(not(containsString("unknown relation")))
        .body(not(containsString("FgaException")))
        .body("$", not(hasKey("stackTrace")));
  }
}
