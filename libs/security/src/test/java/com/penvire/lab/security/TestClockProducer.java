package com.penvire.lab.security;

import com.penvire.lab.clock.MutableClock;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Clock;
import java.time.Instant;

@Dependent
public class TestClockProducer {

  public static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");

  @Produces
  @Singleton
  Clock clock() {
    return MutableClock.at(NOW);
  }
}
