package com.penvire.lab.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Without {@code @DefaultBean} on the library's generator, two IdGenerator beans would make the
 * application fail at startup with an ambiguous dependency.
 */
@QuarkusTest
@TestProfile(ServiceDefinedIdGeneratorTest.Profile.class)
class ServiceDefinedIdGeneratorTest {

  static final String PROFILE = "service-defined-id-generator";

  @Inject IdGenerator ids;

  @Test
  void aServiceDefinedGeneratorReplacesTheDefault() {
    assertEquals(FixedIdGenerator.ID, ids.next());
  }

  public static class Profile implements QuarkusTestProfile {
    @Override
    public String getConfigProfile() {
      return PROFILE;
    }
  }
}
