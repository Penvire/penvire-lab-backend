package com.penvire.lab.observability.trace;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(TracingDisabledTest.DisabledTracing.class)
class TracingDisabledTest {

  public static class DisabledTracing implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of("quarkus.otel.sdk.disabled", "true");
    }
  }

  @Test
  void omitsTheTraceHeaderWithoutAnActiveTrace() {
    given().when().get("/test/trace").then().statusCode(200).header("X-Trace-Id", nullValue());
  }

  @Test
  void doesNotEchoAnIncomingTraceHeaderWhenTracingIsDisabled() {
    given()
        .header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
        .when()
        .get("/test/trace")
        .then()
        .statusCode(200)
        .header("X-Trace-Id", nullValue());
  }
}
