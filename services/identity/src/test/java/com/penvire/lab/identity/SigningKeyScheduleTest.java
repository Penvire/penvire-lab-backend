package com.penvire.lab.identity;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penvire.lab.identity.adapter.out.persistence.entity.SigningKeyEntityRepository;
import com.penvire.lab.identity.config.JwksConfig;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import com.penvire.lab.identity.server.model.Jwk;
import com.penvire.lab.identity.server.model.JwkSet;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The scheduled rotation trigger runs {@code RotateSigningKeyUseCase} automatically on tick. */
@QuarkusTest
@TestProfile(SigningKeyScheduleTest.FastSchedule.class)
class SigningKeyScheduleTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  private static final Duration POLL_INTERVAL = Duration.ofMillis(50);

  @Inject SigningKeyEntityRepository entities;
  @Inject JwksConfig jwksConfig;
  @Inject Scheduler scheduler;

  @AfterEach
  void pauseScheduler() {
    scheduler.pause();
  }

  @Test
  void repopulatesKeysOnScheduleWhenMissing() throws InterruptedException {
    var expectedAlgorithms =
        jwksConfig.algorithms().stream()
            .map(SigningKeyAlgorithm::joseName)
            .collect(Collectors.toSet());
    var startupKids = kids(jwks());
    assertEquals(expectedAlgorithms.size(), startupKids.size());

    QuarkusTransaction.requiringNew().run(() -> entities.deleteAll());

    var deadline = Instant.now().plus(TIMEOUT);
    JwkSet repopulated = jwks();
    while (!hasFreshKeys(repopulated, expectedAlgorithms, startupKids)
        && Instant.now().isBefore(deadline)) {
      Thread.sleep(POLL_INTERVAL.toMillis());
      repopulated = jwks();
    }

    assertEquals(expectedAlgorithms, algorithms(repopulated));
    assertTrue(
        Collections.disjoint(kids(repopulated), startupKids),
        "Expected scheduled rotation to generate fresh keys after table wipe");
  }

  private static boolean hasFreshKeys(
      JwkSet jwks, Set<String> expectedAlgorithms, Set<String> previousKids) {
    return jwks.getKeys().size() == expectedAlgorithms.size()
        && algorithms(jwks).equals(expectedAlgorithms)
        && Collections.disjoint(kids(jwks), previousKids);
  }

  private static JwkSet jwks() {
    return given()
        .when()
        .get("/.well-known/jwks.json")
        .then()
        .statusCode(200)
        .extract()
        .as(JwkSet.class);
  }

  private static Set<String> algorithms(JwkSet jwks) {
    return jwks.getKeys().stream().map(Jwk::getAlg).collect(Collectors.toSet());
  }

  private static Set<String> kids(JwkSet jwks) {
    return jwks.getKeys().stream().map(Jwk::getKid).collect(Collectors.toSet());
  }

  public static class FastSchedule implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "penvire.jwks.rotation-delay", "1s",
          "penvire.jwks.rotation-interval", "1s");
    }
  }
}
