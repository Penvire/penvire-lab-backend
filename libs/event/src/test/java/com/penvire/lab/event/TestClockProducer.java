package com.penvire.lab.event;

import com.penvire.lab.clock.MutableClock;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Clock;
import java.time.Instant;

/** A fixed clock, so tests can assert the event time and the cleanup cutoff exactly. */
@Dependent
public class TestClockProducer {

  public static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");

  @Produces
  @Singleton
  Clock clock() {
    return MutableClock.at(NOW);
  }
}
