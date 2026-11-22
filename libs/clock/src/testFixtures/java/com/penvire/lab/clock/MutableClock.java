package com.penvire.lab.clock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A clock that tests move by hand. Thread-safe, because Quarkus tests read it from request threads.
 * Copies made by {@link #withZone} share the same time, so moving one moves all.
 */
public final class MutableClock extends Clock {

  private final AtomicReference<Instant> now;
  private final ZoneId zone;

  private MutableClock(AtomicReference<Instant> now, ZoneId zone) {
    this.now = now;
    this.zone = zone;
  }

  public static MutableClock at(Instant instant) {
    return new MutableClock(new AtomicReference<>(instant), ZoneOffset.UTC);
  }

  public void set(Instant instant) {
    now.set(instant);
  }

  public void advance(Duration duration) {
    now.updateAndGet(current -> current.plus(duration));
  }

  @Override
  public Instant instant() {
    return now.get();
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return zone.equals(this.zone) ? this : new MutableClock(now, zone);
  }
}
