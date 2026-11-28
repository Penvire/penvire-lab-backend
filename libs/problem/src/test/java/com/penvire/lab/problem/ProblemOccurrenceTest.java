package com.penvire.lab.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProblemOccurrenceTest {

  @Test
  void noneShowsNothingBeyondTheType() {
    var none = ProblemOccurrence.NONE;

    assertNull(none.detail());
    assertTrue(none.properties().isEmpty());
    assertNull(none.retryAfter());
  }

  @Test
  void eachWitherKeepsWhatWasSetBefore() {
    var occurrence =
        ProblemOccurrence.NONE
            .withDetail("The order was already shipped.")
            .withProperty("currentVersion", 7)
            .withProperty("orderId", "42")
            .withRetryAfter(Duration.ofSeconds(30));

    assertEquals("The order was already shipped.", occurrence.detail());
    assertEquals(Map.of("currentVersion", 7, "orderId", "42"), occurrence.properties());
    assertEquals(Duration.ofSeconds(30), occurrence.retryAfter());
  }

  @Test
  void withPropertyReplacesAnEarlierValue() {
    var occurrence =
        ProblemOccurrence.NONE.withProperty("currentVersion", 6).withProperty("currentVersion", 7);

    assertEquals(Map.of("currentVersion", 7), occurrence.properties());
  }

  @Test
  void propertiesCannotChangeAfterConstruction() {
    var properties = new HashMap<String, Object>();
    properties.put("currentVersion", 7);
    var occurrence = new ProblemOccurrence(null, properties, null);

    properties.put("leaked", "later");

    assertEquals(Map.of("currentVersion", 7), occurrence.properties());
    assertThrows(
        UnsupportedOperationException.class, () -> occurrence.properties().put("other", 1));
  }

  @ParameterizedTest
  @ValueSource(strings = {"type", "title", "status", "detail", "instance", "traceId"})
  void rejectsPropertiesThatWouldShadowStandardOrTraceMembers(String name) {
    assertThrows(
        IllegalArgumentException.class, () -> ProblemOccurrence.NONE.withProperty(name, "x"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "CurrentVersion", "current-version", "current_version", "1st"})
  void rejectsPropertyNamesThatAreNotCamelCase(String name) {
    assertThrows(
        IllegalArgumentException.class, () -> ProblemOccurrence.NONE.withProperty(name, "x"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t\n", "\u2003"})
  void aBlankDetailMeansNoDetail(String blank) {
    assertNull(ProblemOccurrence.NONE.withDetail(blank).detail());
  }

  @Test
  void stripsSurroundingWhitespaceFromTheDetail() {
    var occurrence = ProblemOccurrence.NONE.withDetail("\u2003 The order was already shipped.\n");

    assertEquals("The order was already shipped.", occurrence.detail());
  }

  @Test
  void rejectsANegativeRetryAfter() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ProblemOccurrence.NONE.withRetryAfter(Duration.ofSeconds(-1)));
  }

  @Test
  void allowsRetryingImmediately() {
    assertEquals(Duration.ZERO, ProblemOccurrence.NONE.withRetryAfter(Duration.ZERO).retryAfter());
  }
}
