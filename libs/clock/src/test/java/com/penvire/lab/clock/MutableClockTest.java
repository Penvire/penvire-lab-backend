package com.penvire.lab.clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MutableClockTest {

  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void startsAtTheGivenInstantInUtc() {
    MutableClock clock = MutableClock.at(START);

    assertEquals(START, clock.instant());
    assertEquals(ZoneOffset.UTC, clock.getZone());
  }

  @Test
  void standsStillUntilMoved() {
    MutableClock clock = MutableClock.at(START);

    assertEquals(clock.instant(), clock.instant());
  }

  @Test
  void advanceMovesTimeForward() {
    MutableClock clock = MutableClock.at(START);

    clock.advance(Duration.ofMinutes(5));

    assertEquals(START.plusSeconds(300), clock.instant());
  }

  @Test
  void setJumpsToTheGivenInstant() {
    MutableClock clock = MutableClock.at(START);
    Instant earlier = START.minusSeconds(3600);

    clock.set(earlier);

    assertEquals(earlier, clock.instant());
  }

  @Test
  void millisFollowsTheInstant() {
    MutableClock clock = MutableClock.at(START);

    clock.advance(Duration.ofMillis(42));

    assertEquals(START.toEpochMilli() + 42, clock.millis());
  }

  @Test
  void aZonedCopySharesTheTime() {
    MutableClock clock = MutableClock.at(START);
    Clock zurich = clock.withZone(ZoneId.of("Europe/Zurich"));

    clock.advance(Duration.ofSeconds(1));

    assertEquals(ZoneId.of("Europe/Zurich"), zurich.getZone());
    assertEquals(START.plusSeconds(1), zurich.instant());
  }

  @Test
  void theSameZoneReturnsTheSameClock() {
    MutableClock clock = MutableClock.at(START);

    assertSame(clock, clock.withZone(ZoneOffset.UTC));
  }

  @Test
  void concurrentAdvancesAreNotLost() throws Exception {
    MutableClock clock = MutableClock.at(START);
    int advances = 10_000;

    try (ExecutorService threads = Executors.newFixedThreadPool(8)) {
      IntStream.range(0, advances)
          .forEach(i -> threads.execute(() -> clock.advance(Duration.ofMillis(1))));
    }

    assertEquals(START.plusMillis(advances), clock.instant());
  }
}
