package com.penvire.lab.persistence;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

@QuarkusTest
class IdGeneratorTest {

  private static final int THREADS = 8;
  private static final int IDS_PER_THREAD = 1_250;

  @Inject IdGenerator ids;

  @Test
  void generatesRfc9562Version7Ids() {
    UUID id = ids.next();

    assertAll(
        () -> assertEquals(7, id.version(), "version"),
        () -> assertEquals(2, id.variant(), "variant (RFC 9562)"));
  }

  @Test
  void embedsTheCurrentTimeInTheFirst48Bits() {
    long before = System.currentTimeMillis();
    UUID id = ids.next();
    long after = System.currentTimeMillis();

    long embeddedMillis = id.getMostSignificantBits() >>> 16;

    assertTrue(
        embeddedMillis >= before && embeddedMillis <= after,
        () -> "Expected a timestamp in [" + before + ", " + after + "], got " + embeddedMillis);
  }

  /**
   * Many IDs share a millisecond here, so this checks the generator's counter, not the clock.
   * UUID.compareTo compares signed longs; a v7 ID's top bit stays 0 for millennia, so that is safe.
   */
  @Test
  void consecutiveIdsAreStrictlyIncreasing() {
    UUID previous = ids.next();
    for (int i = 0; i < 1_000; i++) {
      UUID current = ids.next();
      assertTrue(
          current.compareTo(previous) > 0, "ID " + current + " does not sort after " + previous);
      previous = current;
    }
  }

  @Test
  void idsFromConcurrentThreadsAreUnique() throws Exception {
    Callable<List<UUID>> batch =
        () -> IntStream.range(0, IDS_PER_THREAD).mapToObj(i -> ids.next()).toList();

    Set<UUID> unique = new HashSet<>();
    try (ExecutorService threads = Executors.newFixedThreadPool(THREADS)) {
      List<Future<List<UUID>>> batches =
          threads.invokeAll(IntStream.range(0, THREADS).mapToObj(i -> batch).toList());
      for (Future<List<UUID>> result : batches) {
        unique.addAll(result.get(10, TimeUnit.SECONDS));
      }
    }

    assertEquals(THREADS * IDS_PER_THREAD, unique.size());
  }
}
