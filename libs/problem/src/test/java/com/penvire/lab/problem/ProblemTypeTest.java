package com.penvire.lab.problem;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProblemTypeTest {

  @ParameterizedTest
  @ValueSource(strings = {"fga.unavailable", "identity.signing-key-missing", "bff2.not-found"})
  void acceptsModuleDotNameCodes(String code) {
    assertDoesNotThrow(() -> new ProblemType(code, ProblemKind.INTERNAL, "Title"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "unavailable",
        "Fga.unavailable",
        "fga.",
        ".unavailable",
        "fga.not_found",
        "fga.a.b",
        "1fga.unavailable",
        "fga.un available"
      })
  void rejectsCodesThatAreNotModuleDotName(String code) {
    assertThrows(
        IllegalArgumentException.class, () -> new ProblemType(code, ProblemKind.INTERNAL, "Title"));
  }

  @Test
  void rejectsBlankTitles() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ProblemType("fga.unavailable", ProblemKind.UNAVAILABLE, " "));
  }
}
