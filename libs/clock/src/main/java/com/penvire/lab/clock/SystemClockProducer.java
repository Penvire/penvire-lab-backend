package com.penvire.lab.clock;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Clock;
import java.time.Duration;

/**
 * The system clock in UTC. A service replaces it by producing its own Clock bean, for example a
 * MutableClock in tests.
 */
@Dependent
final class SystemClockProducer {
  /** Singleton, not ApplicationScoped: the clock is immutable, so a client proxy buys nothing. */
  @Produces
  @Singleton
  @DefaultBean
  Clock systemClock() {
    return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
  }
}
