package com.penvire.lab.clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SystemClockTest {

  @Inject Clock clock;

  @Test
  void theDefaultClockIsSystemTimeInUtc() {
    Instant before = Instant.now();
    Instant now = clock.instant();
    Instant after = Instant.now();

    assertEquals(ZoneOffset.UTC, clock.getZone());
    assertFalse(now.isBefore(before), () -> now + " is before " + before);
    assertFalse(now.isAfter(after), () -> now + " is after " + after);
  }
}
