package com.penvire.lab.identity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HealthTest {

    @Test
    void reportsLive() {
        given().when().get("/q/health/live")
                .then().statusCode(200).body("status", equalTo("UP"));
    }

    @Test
    void reportsReady() {
        given().when().get("/q/health/ready")
                .then().statusCode(200).body("status", equalTo("UP"));
    }
}
