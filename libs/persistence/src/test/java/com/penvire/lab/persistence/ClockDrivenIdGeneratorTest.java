package com.penvire.lab.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penvire.lab.clock.MutableClock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The default generator takes its timestamp from the Clock bean, not from the system. The generator
 * is a singleton that remembers the last time it used, so every test first moves the clock past
 * anything an earlier test did, whatever the order.
 */
@QuarkusTest
@TestProfile(ClockDrivenIdGeneratorTest.Profile.class)
class ClockDrivenIdGeneratorTest {

  static final String PROFILE = "clock-driven-id-generator";

  @Inject IdGenerator ids;
  @Inject Clock clock;

  private MutableClock time;

  @BeforeEach
  void moveIntoUnusedTime() {
    time = (MutableClock) clock;
    time.advance(Duration.ofDays(1));
  }

  @Test
  void embedsTheClocksTime() {
    assertEquals(time.millis(), embeddedMillis(ids.next()));
  }

  @Test
  void followsTheClockWhenItMoves() {
    ids.next();
    time.advance(Duration.ofHours(1));

    assertEquals(time.millis(), embeddedMillis(ids.next()));
  }

  /** NTP can step the system clock back; IDs must still sort in creation order. */
  @Test
  void staysOrderedWhenTheClockGoesBack() {
    UUID before = ids.next();
    time.advance(Duration.ofSeconds(-10));
    UUID after = ids.next();

    assertTrue(after.compareTo(before) > 0, () -> after + " does not sort after " + before);
  }

  private static long embeddedMillis(UUID id) {
    return id.getMostSignificantBits() >>> 16;
  }

  public static class Profile implements QuarkusTestProfile {
    @Override
    public String getConfigProfile() {
      return PROFILE;
    }
  }
}
