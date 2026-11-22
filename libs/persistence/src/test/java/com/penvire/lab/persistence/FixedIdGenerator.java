package com.penvire.lab.persistence;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.inject.Singleton;
import java.util.UUID;

/**
 * Stands in for a service's own IdGenerator: a plain bean, not an alternative. It only exists in
 * the profile of {@link ServiceDefinedIdGeneratorTest}, so every other test keeps the default.
 */
@Singleton
@IfBuildProfile(ServiceDefinedIdGeneratorTest.PROFILE)
class FixedIdGenerator implements IdGenerator {

  static final UUID ID = UUID.fromString("00000000-0000-7000-8000-000000000001");

  @Override
  public UUID next() {
    return ID;
  }
}
