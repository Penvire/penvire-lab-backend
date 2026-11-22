package com.penvire.lab.clock;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Clock;
import java.time.Instant;

/**
 * Stands in for a service's own clock: a plain producer, not an alternative. It only exists in the
 * profile of {@link ServiceDefinedClockTest}, so every other test keeps the default.
 */
@Dependent
@IfBuildProfile(ServiceDefinedClockTest.PROFILE)
class ServiceClockProducer {

  static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Produces
  @Singleton
  Clock clock() {
    return MutableClock.at(NOW);
  }
}
