package com.penvire.lab.problem;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ProblemResponseTest {

  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";

  @Test
  void returnsProblemDetailsWithMatchingTraceHeaderAndBody() {
    given()
        .header("traceparent", TRACEPARENT)
        .when()
        .get("/test/problems/conflict")
        .then()
        .statusCode(409)
        .contentType("application/problem+json")
        .header("X-Trace-Id", TRACE_ID)
        .header("Retry-After", "30")
        .body("status", equalTo(409))
        .body("type", equalTo("urn:penvire:problem:test-conflict"))
        .body("title", equalTo("Conflict"))
        .body("detail", equalTo("The requested change conflicts with current state."))
        .body("traceId", equalTo(TRACE_ID));
  }

  @Test
  void malformedJsonReturnsASafeBadRequestProblem() {
    given()
        .header("traceparent", TRACEPARENT)
        .contentType("application/json")
        .body("{\"name\":")
        .when()
        .post("/test/problems")
        .then()
        .statusCode(400)
        .contentType("application/problem+json")
        .header("X-Trace-Id", TRACE_ID)
        .body("status", equalTo(400))
        .body("traceId", equalTo(TRACE_ID))
        .body(not(containsString("ProblemTestResource")))
        .body(not(containsString("com.fasterxml.jackson")))
        .body("$", not(hasKey("stackTrace")));
  }

  @Test
  void constraintViolationsUseTheSharedValidationPolicy() {
    given()
        .header("traceparent", TRACEPARENT)
        .contentType("application/json")
        .body("{\"name\":\"\"}")
        .when()
        .post("/test/problems")
        .then()
        .statusCode(422)
        .contentType("application/problem+json")
        .header("X-Trace-Id", TRACE_ID)
        .body("status", equalTo(422))
        .body("title", equalTo("Constraint violation"))
        .body("traceId", equalTo(TRACE_ID));
  }

  @Test
  void unexpectedFailuresDoNotExposeInternalDetails() {
    given()
        .header("traceparent", TRACEPARENT)
        .when()
        .get("/test/problems/unexpected")
        .then()
        .statusCode(500)
        .contentType("application/problem+json")
        .header("X-Trace-Id", TRACE_ID)
        .body("status", equalTo(500))
        .body("traceId", equalTo(TRACE_ID))
        .body(not(containsString("internal-database-connection-detail")))
        .body(not(containsString("IllegalStateException")))
        .body(not(containsString("ProblemTestResource")))
        .body("$", not(hasKey("stackTrace")));
  }
}
