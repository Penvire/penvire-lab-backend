package com.penvire.lab.identity;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request.Method;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.JsonValidationReportFormat;
import io.quarkus.test.junit.QuarkusTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@QuarkusTest
class IdentityResponseContractTest {
  @ParameterizedTest
  @ValueSource(strings = {"/.well-known/openid-configuration", "/jwks"})
  void servesAResponseThatMatchesThePublishedContract(String path) throws IOException {
    var contract = loadContract();
    var response = given().redirects().follow(false).when().get(path);
    var actual =
        SimpleResponse.Builder.status(response.statusCode()).withBody(response.asByteArray());
    for (var header : response.headers()) {
      actual.withHeader(header.getName(), header.getValue());
    }

    var report = contract.validateResponse(path, Method.GET, actual.build());
    assertFalse(
        report.hasErrors(),
        () -> "GET " + path + ": " + JsonValidationReportFormat.getInstance().apply(report));
  }

  private static OpenApiInteractionValidator loadContract() throws IOException {
    try (var spec =
        IdentityResponseContractTest.class.getResourceAsStream(
            "/contracts/identity-openapi.yaml")) {
      assertNotNull(spec, "Missing generated test resource: /contracts/identity-openapi.yaml");
      return OpenApiInteractionValidator.createForInlineApiSpecification(
              new String(spec.readAllBytes(), StandardCharsets.UTF_8))
          .build();
    }
  }
}
