package com.penvire.lab.identity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import java.net.URL;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HealthTest {

    @TestHTTPResource(value = "/health/live", management = true)
    URL live;

    @TestHTTPResource(value = "/health/ready", management = true)
    URL ready;

    @Test
    void reportsLiveOnManagementPort() {
        given().when().get(live)
                .then().statusCode(200).body("status", equalTo("UP"));
    }

    @Test
    void reportsReadyOnManagementPort() {
        given().when().get(ready)
                .then().statusCode(200).body("status", equalTo("UP"));
    }

    @Test
    void isNotServedOnApplicationPort() {
        given().when().get("/q/health")
                .then().statusCode(404);
    }
}
