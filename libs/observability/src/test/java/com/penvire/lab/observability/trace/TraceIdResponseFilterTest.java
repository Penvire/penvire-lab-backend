package com.penvire.lab.observability.trace;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TraceIdResponseFilterTest {

  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";

  @Test
  void returnsTheIncomingTraceIdOnASuccessfulResponse() {
    given()
        .header("traceparent", TRACEPARENT)
        .when()
        .get("/test/trace")
        .then()
        .statusCode(200)
        .body(equalTo("ok"))
        .header("X-Trace-Id", TRACE_ID);
  }

  @Test
  void returnsTheTraceIdOnAnErrorResponse() {
    given()
        .header("traceparent", TRACEPARENT)
        .when()
        .get("/test/trace/error")
        .then()
        .statusCode(409)
        .body(equalTo("conflict"))
        .header("X-Trace-Id", TRACE_ID);
  }

  @Test
  void generatesATraceWhenTheRequestHasNoParent() {
    given()
        .when()
        .get("/test/trace")
        .then()
        .statusCode(200)
        .header("X-Trace-Id", matchesPattern("(?!0{32})[0-9a-f]{32}"));
  }

  @Test
  void replacesAnExistingHeaderWithTheActiveTraceId() {
    var response =
        given()
            .header("traceparent", TRACEPARENT)
            .when()
            .get("/test/trace/existing-header")
            .then()
            .statusCode(200)
            .header("X-Trace-Id", TRACE_ID)
            .extract()
            .response();

    assertEquals(1, response.getHeaders().getValues("X-Trace-Id").size());
  }
}
