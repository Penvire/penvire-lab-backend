package com.penvire.lab.persistence;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import com.fasterxml.uuid.UUIDClock;
import io.quarkus.arc.DefaultBean;
import jakarta.inject.Singleton;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * UUIDv7 (RFC 9562): a millisecond timestamp first, so new rows append to the primary-key index
 * instead of scattering across it like random v4 IDs. The timestamp comes from the injected Clock,
 * so tests control it. A service replaces it by defining its own IdGenerator bean.
 */
@Singleton
@DefaultBean
final class Uuid7IdGenerator implements IdGenerator {
  private final NoArgGenerator generator;

  Uuid7IdGenerator(Clock clock) {
    generator = Generators.timeBasedEpochGenerator(new SecureRandom(), new ClockSource(clock));
  }

  @Override
  public UUID next() {
    return generator.generate();
  }

  /**
   * JUG reads the time through a UUIDClock; this one asks the service's Clock. It never goes back:
   * JUG would otherwise issue IDs that sort before earlier ones when NTP steps the system clock
   * back. Until the clock catches up, IDs reuse the last millisecond and JUG's counter orders them.
   */
  private static final class ClockSource extends UUIDClock {
    private final Clock clock;
    private final AtomicLong latest = new AtomicLong(Long.MIN_VALUE);

    ClockSource(Clock clock) {
      this.clock = clock;
    }

    @Override
    public long currentTimeMillis() {
      return latest.accumulateAndGet(clock.millis(), Math::max);
    }
  }
}
