package com.penvire.lab.jwt.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JwtValidationPolicyTest {

  @Test
  void snapshotsTheConfiguredPolicySets() {
    var audiences = new HashSet<>(Set.of("tenant-api"));
    var types = new HashSet<>(Set.of(JwtType.ACCESS));
    var algorithms = new HashSet<>(Set.of("RS256"));
    var skew = Duration.ofSeconds(30);

    var policy = new JwtValidationPolicy("https://issuer.test", audiences, types, algorithms, skew);
    audiences.clear();
    types.clear();
    algorithms.clear();

    assertEquals("https://issuer.test", policy.issuer());
    assertEquals(Set.of("tenant-api"), policy.audiences());
    assertEquals(Set.of(JwtType.ACCESS), policy.types());
    assertEquals(Set.of("RS256"), policy.allowedAlgorithms());
    assertEquals(skew, policy.clockSkew());
    assertThrows(UnsupportedOperationException.class, () -> policy.audiences().add("other"));
    assertThrows(UnsupportedOperationException.class, () -> policy.types().add(JwtType.REFRESH));
    assertThrows(UnsupportedOperationException.class, () -> policy.allowedAlgorithms().add("none"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\n"})
  void rejectsBlankIssuers(String issuer) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new JwtValidationPolicy(
                issuer, Set.of("api"), Set.of(JwtType.ACCESS), Set.of("RS256"), Duration.ZERO));
  }

  @Test
  void rejectsEmptyAudiences() {
    assertThrows(
        IllegalArgumentException.class,
        () -> JwtTestTokens.policy(Set.of(), Set.of(JwtType.ACCESS), Set.of("RS256")));
  }

  @Test
  void rejectsEmptyTokenTypes() {
    assertThrows(
        IllegalArgumentException.class,
        () -> JwtTestTokens.policy(Set.of("api"), Set.of(), Set.of("RS256")));
  }

  @Test
  void rejectsEmptyAlgorithms() {
    assertThrows(
        IllegalArgumentException.class,
        () -> JwtTestTokens.policy(Set.of("api"), Set.of(JwtType.ACCESS), Set.of()));
  }

  @Test
  void rejectsNegativeClockSkew() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new JwtValidationPolicy(
                JwtTestTokens.ISSUER,
                Set.of("api"),
                Set.of(JwtType.ACCESS),
                Set.of("RS256"),
                Duration.ofNanos(-1)));
  }

  @Test
  void permitsZeroClockSkew() {
    var policy =
        new JwtValidationPolicy(
            JwtTestTokens.ISSUER,
            Set.of("api"),
            Set.of(JwtType.ACCESS),
            Set.of("RS256"),
            Duration.ZERO);

    assertEquals(Duration.ZERO, policy.clockSkew());
  }
}
