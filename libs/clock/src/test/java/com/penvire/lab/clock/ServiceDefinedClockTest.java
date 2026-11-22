package com.penvire.lab.clock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.time.Clock;
import org.junit.jupiter.api.Test;

/**
 * Without {@code @DefaultBean} on the library's producer, two Clock beans would make the
 * application fail at startup with an ambiguous dependency.
 */
@QuarkusTest
@TestProfile(ServiceDefinedClockTest.Profile.class)
class ServiceDefinedClockTest {

  static final String PROFILE = "service-defined-clock";

  @Inject Clock clock;

  @Test
  void aServiceDefinedClockReplacesTheDefault() {
    assertEquals(ServiceClockProducer.NOW, clock.instant());
  }

  public static class Profile implements QuarkusTestProfile {
    @Override
    public String getConfigProfile() {
      return PROFILE;
    }
  }
}
