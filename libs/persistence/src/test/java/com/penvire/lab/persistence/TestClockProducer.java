package com.penvire.lab.persistence;

import com.penvire.lab.clock.MutableClock;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Clock;
import java.time.Instant;

/** A movable clock, only in the profile of {@link ClockDrivenIdGeneratorTest}. */
@Dependent
@IfBuildProfile(ClockDrivenIdGeneratorTest.PROFILE)
class TestClockProducer {

  static final Instant START = Instant.parse("2030-01-01T00:00:00Z");

  @Produces
  @Singleton
  Clock clock() {
    return MutableClock.at(START);
  }
}
