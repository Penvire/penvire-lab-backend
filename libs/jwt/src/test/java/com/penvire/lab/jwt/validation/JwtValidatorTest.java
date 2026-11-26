package com.penvire.lab.jwt.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.penvire.lab.clock.MutableClock;
import com.penvire.lab.jwt.jws.JwsVerificationException;
import com.penvire.lab.jwt.jws.JwsVerifier;
import com.penvire.lab.jwt.jws.Rs256JwsVerifier;
import java.io.IOException;
import java.security.PublicKey;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class JwtValidatorTest {

  @Test
  void validatesAnAccessTokenAndReturnsItsAuthenticatedClaims() throws Exception {
    var claims = JwtTestTokens.accessClaims();
    VerifiedJwt result =
        validator().validate(JwtTestTokens.accessToken(claims), JwtTestTokens.accessPolicy());

    assertEquals(claims.toString(), result.claims().toString());
    assertEquals("external-user-subject", result.claims().path("sub").textValue());
    assertFalse(result.claims().has("sid"));
    assertFalse(result.claims().has("family_id"));
  }

  @Test
  void validatesARefreshTokenWithARealMlDsaSignature() throws Exception {
    var claims = JwtTestTokens.refreshClaims();

    VerifiedJwt result =
        validator()
            .validate(JwtTestTokens.refreshToken(claims), JwtTestTokens.refreshPolicy("ML-DSA-65"));

    assertEquals(claims.toString(), result.claims().toString());
    assertEquals("session-id", result.claims().path("sid").textValue());
    assertEquals("refresh-family-id", result.claims().path("family_id").textValue());
  }

  @Test
  void resolvesKeysUsingTrustedIssuerAndPolicyCheckedHeader() throws Exception {
    var calls = new AtomicInteger();
    JwtVerificationKeyResolver resolver =
        (issuer, kid, algorithm, type) -> {
          calls.incrementAndGet();
          assertEquals(JwtTestTokens.ISSUER, issuer);
          assertEquals("retired-but-valid-key", kid);
          assertEquals("RS256", algorithm);
          assertEquals(JwtType.ACCESS, type);
          return JwtTestTokens.RSA_KEYS.getPublic();
        };
    var header = JwtTestTokens.header(JwtType.ACCESS, "RS256").put("kid", "retired-but-valid-key");
    var claims = JwtTestTokens.accessClaims().put("iss", "https://untrusted.example");
    String token = JwtTestTokens.rsaToken(header, claims);
    var validator = new JwtValidator(signatureVerifiers(), JwtTestTokens.CLOCK, resolver);

    assertThrows(
        JwtVerificationException.class,
        () -> validator.validate(token, JwtTestTokens.accessPolicy()));
    assertEquals(1, calls.get());
  }

  @Test
  void preservesKeySourceOutagesInsteadOfReportingInvalidCredentials() throws Exception {
    var unavailable = new IllegalStateException("test key source unavailable");
    JwtVerificationKeyResolver resolver =
        (issuer, kid, algorithm, type) -> {
          throw unavailable;
        };
    var validator = new JwtValidator(signatureVerifiers(), JwtTestTokens.CLOCK, resolver);
    String token = JwtTestTokens.accessToken(JwtTestTokens.accessClaims());

    assertSame(
        unavailable,
        assertThrows(
            IllegalStateException.class,
            () -> validator.validate(token, JwtTestTokens.accessPolicy())));
  }

  @Test
  void snapshotsTheSignatureVerifierRegistry() throws Exception {
    var registry = new HashMap<>(signatureVerifiers());
    var validator = new JwtValidator(registry, JwtTestTokens.CLOCK, JwtValidatorTest::resolveKey);
    registry.clear();

    VerifiedJwt result =
        validator.validate(
            JwtTestTokens.accessToken(JwtTestTokens.accessClaims()), JwtTestTokens.accessPolicy());

    assertEquals("external-user-subject", result.claims().path("sub").textValue());
  }

  @Test
  void returnedClaimsCannotBeChangedThroughAnAccessor() throws Exception {
    VerifiedJwt result =
        validator()
            .validate(
                JwtTestTokens.accessToken(JwtTestTokens.accessClaims()),
                JwtTestTokens.accessPolicy());

    ((ObjectNode) result.claims()).put("sub", "different-user");

    assertEquals("external-user-subject", result.claims().path("sub").textValue());
  }

  @Test
  void rejectsAnAlgorithmOutsidePolicyBeforeResolvingAnyKey() throws Exception {
    String token =
        JwtTestTokens.rsaToken(
            JwtTestTokens.header(JwtType.ACCESS, "none"), JwtTestTokens.accessClaims());
    var validator =
        new JwtValidator(
            signatureVerifiers(),
            JwtTestTokens.CLOCK,
            (issuer, kid, algorithm, type) -> {
              throw new AssertionError("A disallowed algorithm must not reach key resolution");
            });

    assertThrows(
        JwtVerificationException.class,
        () -> validator.validate(token, JwtTestTokens.accessPolicy()));
  }

  @Test
  void rejectsAllowedButUnconfiguredAlgorithms() throws Exception {
    var validator = new JwtValidator(Map.of(), JwtTestTokens.CLOCK, JwtValidatorTest::resolveKey);
    String token = JwtTestTokens.accessToken(JwtTestTokens.accessClaims());

    assertThrows(
        JwtVerificationException.class,
        () -> validator.validate(token, JwtTestTokens.accessPolicy()));
  }

  @Test
  void rejectsRefreshTokensAtAnAccessTokenBoundary() throws Exception {
    String token = JwtTestTokens.refreshToken(JwtTestTokens.refreshClaims());
    var policy =
        JwtTestTokens.policy(
            Set.of(JwtTestTokens.AUDIENCE), Set.of(JwtType.ACCESS), Set.of("ML-DSA-65"));

    assertThrows(JwtVerificationException.class, () -> validator().validate(token, policy));
  }

  @Test
  void rejectsRsaRefreshTokensWhenPqcEnforcementIsEnabled() throws Exception {
    String token =
        JwtTestTokens.rsaToken(
            JwtTestTokens.header(JwtType.REFRESH, "RS256"), JwtTestTokens.refreshClaims());

    assertThrows(
        JwtVerificationException.class,
        () -> validator().validate(token, JwtTestTokens.refreshPolicy("RS256")));
  }

  @Test
  void permitsExplicitlyConfiguredRsaRefreshCompatibility() throws Exception {
    var validator =
        new JwtValidator(
            signatureVerifiers(), JwtTestTokens.CLOCK, JwtValidatorTest::resolveKey, false);
    var claims = JwtTestTokens.refreshClaims();
    String token = JwtTestTokens.rsaToken(JwtTestTokens.header(JwtType.REFRESH, "RS256"), claims);

    assertEquals(
        claims.toString(),
        validator.validate(token, JwtTestTokens.refreshPolicy("RS256")).claims().toString());
  }

  @ParameterizedTest
  @MethodSource("invalidHeaderFields")
  void rejectsMissingOrInvalidRequiredHeaderFields(String field, String value) throws Exception {
    var header = JwtTestTokens.header(JwtType.ACCESS, "RS256");
    replaceField(header, field, value);
    String token = JwtTestTokens.rsaToken(header, JwtTestTokens.accessClaims());

    assertThrows(
        JwtVerificationException.class,
        () -> validator().validate(token, JwtTestTokens.accessPolicy()));
  }

  @Test
  void rejectsUnknownTokenTypes() throws Exception {
    var header = JwtTestTokens.header(JwtType.ACCESS, "RS256").put("typ", "JWT");
    String token = JwtTestTokens.rsaToken(header, JwtTestTokens.accessClaims());

    assertThrows(
        JwtVerificationException.class,
        () -> validator().validate(token, JwtTestTokens.accessPolicy()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "[]", "[\"custom\"]"})
  void rejectsUnsupportedCriticalHeaders(String critical) throws Exception {
    var header = JwtTestTokens.header(JwtType.ACCESS, "RS256");
    header.set("crit", JwtTestTokens.json(critical));
    String token = JwtTestTokens.rsaToken(header, JwtTestTokens.accessClaims());

    assertThrows(
        JwtVerificationException.class,
        () -> validator().validate(token, JwtTestTokens.accessPolicy()));
  }

  @Test
  void acceptsTheExplicitStandardBase64PayloadEncoding() throws Exception {
    var header = JwtTestTokens.header(JwtType.ACCESS, "RS256").put("b64", true);
    var claims = JwtTestTokens.accessClaims();

    assertEquals(
        claims.toString(),
        validator()
            .validate(JwtTestTokens.rsaToken(header, claims), JwtTestTokens.accessPolicy())
            .claims()
            .toString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"false", "null", "\"true\"", "1", "[]"})
  void rejectsUnsupportedPayloadEncodingValues(String encoding) throws Exception {
    var header = JwtTestTokens.header(JwtType.ACCESS, "RS256");
    header.set("b64", JwtTestTokens.json(encoding));
    String token = JwtTestTokens.rsaToken(header, JwtTestTokens.accessClaims());

    assertThrows(
        JwtVerificationException.class,
        () -> validator().validate(token, JwtTestTokens.accessPolicy()));
  }

  @Test
  void rejectsPayloadTamperingBeforeCheckingClaims() throws Exception {
    String[] parts = JwtTestTokens.accessToken(JwtTestTokens.accessClaims()).split("\\.", -1);
    parts[1] =
        JwtTestTokens.encode(JwtTestTokens.accessClaims().put("sub", "different-user").toString());

    var failure =
        assertThrows(
            JwtVerificationException.class,
            () -> validator().validate(String.join(".", parts), JwtTestTokens.accessPolicy()));

    assertEquals("Invalid JWT signature", failure.getMessage());
  }

  @Test
  void translatesSignatureVerificationErrors() throws Exception {
    var signatureFailure = new JwsVerificationException("test signature backend failure");
    JwsVerifier failingVerifier =
        (input, signature, key) -> {
          throw signatureFailure;
        };
    var validator =
        new JwtValidator(
            Map.of("RS256", failingVerifier), JwtTestTokens.CLOCK, JwtValidatorTest::resolveKey);
    String token = JwtTestTokens.accessToken(JwtTestTokens.accessClaims());

    var failure =
        assertThrows(
            JwtVerificationException.class,
            () -> validator.validate(token, JwtTestTokens.accessPolicy()));

    assertSame(signatureFailure, failure.getCause());
  }

  @Test
  void translatesMalformedCompactTokens() {
    var failure =
        assertThrows(
            JwtVerificationException.class,
            () -> validator().validate("not-a-token", JwtTestTokens.accessPolicy()));

    assertInstanceOf(JwsVerificationException.class, failure.getCause());
  }

  @ParameterizedTest
  @ValueSource(strings = {" ", "null", "[]", "true", "1", "\"claims\""})
  void requiresAnObjectPayloadEvenWhenTheSignatureIsValid(String payload) throws Exception {
    String token =
        JwtTestTokens.signed(
            JwtTestTokens.header(JwtType.ACCESS, "RS256").toString(),
            payload,
            "SHA256withRSA",
            JwtTestTokens.RSA_KEYS);

    assertThrows(
        JwtVerificationException.class,
        () -> validator().validate(token, JwtTestTokens.accessPolicy()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{", "{\"sub\":\"a\",\"sub\":\"b\"}", "{} {}"})
  void rejectsMalformedDuplicateOrTrailingClaims(String payload) throws Exception {
    String token =
        JwtTestTokens.signed(
            JwtTestTokens.header(JwtType.ACCESS, "RS256").toString(),
            payload,
            "SHA256withRSA",
            JwtTestTokens.RSA_KEYS);

    var failure =
        assertThrows(
            JwtVerificationException.class,
            () -> validator().validate(token, JwtTestTokens.accessPolicy()));

    assertInstanceOf(IOException.class, failure.getCause());
  }

  @ParameterizedTest
  @MethodSource("invalidAccessTextClaims")
  void rejectsMissingOrInvalidRequiredAccessIdentifiers(String field, String value)
      throws Exception {
    var claims = JwtTestTokens.accessClaims();
    replaceField(claims, field, value);
    String token = JwtTestTokens.accessToken(claims);

    assertThrows(
        JwtVerificationException.class,
        () -> validator().validate(token, JwtTestTokens.accessPolicy()));
  }

  @ParameterizedTest
  @MethodSource("invalidRefreshTextClaims")
  void rejectsMissingOrInvalidSessionAndFamilyIdentifiers(String field, String value)
      throws Exception {
    var claims = JwtTestTokens.refreshClaims();
    replaceField(claims, field, value);
    String token = JwtTestTokens.refreshToken(claims);

    assertThrows(
        JwtVerificationException.class,
        () -> validator().validate(token, JwtTestTokens.refreshPolicy("ML-DSA-65")));
  }

  @Test
  void rejectsAnUnexpectedIssuer() throws Exception {
    String token =
        JwtTestTokens.accessToken(
            JwtTestTokens.accessClaims().put("iss", "https://another-issuer.test"));

    assertThrows(
        JwtVerificationException.class,
        () -> validator().validate(token, JwtTestTokens.accessPolicy()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"[\"other-api\",\"tenant-api\"]", "[\"tenant-api\",\"other-api\"]"})
  void acceptsAnAudienceArrayContainingAnAcceptedAudience(String audiences) throws Exception {
    var claims = JwtTestTokens.accessClaims();
    claims.set("aud", JwtTestTokens.json(audiences));

    assertEquals(
        claims.toString(),
        validator()
            .validate(JwtTestTokens.accessToken(claims), JwtTestTokens.accessPolicy())
            .claims()
            .toString());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "<missing>",
        "null",
        "false",
        "1",
        "{}",
        "[]",
        "\"\"",
        "\" \"",
        "\"other-api\"",
        "[\"other-api\"]",
        "[null]",
        "[\"\"]",
        "[\"tenant-api\",42]",
        "[\"tenant-api\",\" \"]"
      })
  void rejectsMissingInvalidOrUnacceptedAudiences(String audience) throws Exception {
    var claims = JwtTestTokens.accessClaims();
    replaceField(claims, "aud", audience);
    String token = JwtTestTokens.accessToken(claims);

    assertThrows(
        JwtVerificationException.class,
        () -> validator().validate(token, JwtTestTokens.accessPolicy()));
  }

  @ParameterizedTest
  @MethodSource("invalidNumericDates")
  void rejectsMissingOrInvalidRequiredNumericDates(String field, String value) throws Exception {
    var claims = JwtTestTokens.accessClaims();
    replaceField(claims, field, value);
    String token = JwtTestTokens.accessToken(claims);

    assertThrows(
        JwtVerificationException.class,
        () -> validator().validate(token, JwtTestTokens.accessPolicy()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"exp", "nbf", "iat"})
  void rejectsNumericDatesOutsideTheInstantRange(String field) throws Exception {
    var claims = JwtTestTokens.accessClaims().put(field, Long.MAX_VALUE);
    String token = JwtTestTokens.accessToken(claims);

    var failure =
        assertThrows(
            JwtVerificationException.class,
            () -> validator().validate(token, JwtTestTokens.accessPolicy()));

    assertInstanceOf(DateTimeException.class, failure.getCause());
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1})
  void rejectsExpiryAtOrBeforeIssuance(long offset) throws Exception {
    var claims =
        JwtTestTokens.accessClaims()
            .put("exp", JwtTestTokens.NOW.minusSeconds(60).getEpochSecond() + offset);
    String token = JwtTestTokens.accessToken(claims);

    assertThrows(
        JwtVerificationException.class,
        () -> validator().validate(token, JwtTestTokens.accessPolicy()));
  }

  @Test
  void acceptsUntilImmediatelyBeforeTheExpirySkewDeadline() throws Exception {
    var claims =
        JwtTestTokens.accessClaims()
            .put("exp", JwtTestTokens.NOW.minusSeconds(30).getEpochSecond());
    var clock = Clock.fixed(JwtTestTokens.NOW.minusNanos(1), ZoneOffset.UTC);

    assertEquals(
        claims.toString(),
        validator(clock)
            .validate(JwtTestTokens.accessToken(claims), JwtTestTokens.accessPolicy())
            .claims()
            .toString());
  }

  /** One validator lives as long as the service, so it must not capture the time once. */
  @Test
  void readsTheClockOnEveryValidation() throws Exception {
    var clock = MutableClock.at(JwtTestTokens.NOW);
    var validator = validator(clock);
    String token = JwtTestTokens.accessToken(JwtTestTokens.accessClaims());

    validator.validate(token, JwtTestTokens.accessPolicy());
    // exp is NOW + 240 s and the policy allows 30 s of skew.
    clock.advance(Duration.ofSeconds(270));

    assertThrows(
        JwtVerificationException.class,
        () -> validator.validate(token, JwtTestTokens.accessPolicy()));
  }

  @Test
  void rejectsExactlyAtTheExpirySkewDeadline() throws Exception {
    var claims =
        JwtTestTokens.accessClaims()
            .put("exp", JwtTestTokens.NOW.minusSeconds(30).getEpochSecond());
    String token = JwtTestTokens.accessToken(claims);

    assertThrows(
        JwtVerificationException.class,
        () -> validator().validate(token, JwtTestTokens.accessPolicy()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"iat", "nbf"})
  void acceptsFutureTimeClaimsExactlyAtTheSkewBoundary(String field) throws Exception {
    var claims =
        JwtTestTokens.accessClaims().put(field, JwtTestTokens.NOW.plusSeconds(30).getEpochSecond());

    assertEquals(
        claims.toString(),
        validator()
            .validate(JwtTestTokens.accessToken(claims), JwtTestTokens.accessPolicy())
            .claims()
            .toString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"iat", "nbf"})
  void rejectsFutureTimeClaimsImmediatelyBeyondTheSkewBoundary(String field) throws Exception {
    var claims =
        JwtTestTokens.accessClaims().put(field, JwtTestTokens.NOW.plusSeconds(30).getEpochSecond());
    String token = JwtTestTokens.accessToken(claims);
    var validator = validator(Clock.fixed(JwtTestTokens.NOW.minusNanos(1), ZoneOffset.UTC));

    assertThrows(
        JwtVerificationException.class,
        () -> validator.validate(token, JwtTestTokens.accessPolicy()));
  }

  @ParameterizedTest
  @ValueSource(longs = {0, 1})
  void rejectsNotBeforeAtOrAfterExpiry(long offset) throws Exception {
    var claims =
        JwtTestTokens.accessClaims()
            .put("nbf", JwtTestTokens.NOW.plusSeconds(240 + offset).getEpochSecond());
    String token = JwtTestTokens.accessToken(claims);

    assertThrows(
        JwtVerificationException.class,
        () -> validator().validate(token, JwtTestTokens.accessPolicy()));
  }

  @Test
  void appliesZeroSkewWithoutRejectingCurrentIssuanceAndNotBefore() throws Exception {
    var claims =
        JwtTestTokens.accessClaims()
            .put("iat", JwtTestTokens.NOW.getEpochSecond())
            .put("nbf", JwtTestTokens.NOW.getEpochSecond());
    var policy =
        new JwtValidationPolicy(
            JwtTestTokens.ISSUER,
            Set.of(JwtTestTokens.AUDIENCE),
            Set.of(JwtType.ACCESS),
            Set.of("RS256"),
            Duration.ZERO);

    assertEquals(
        claims.toString(),
        validator().validate(JwtTestTokens.accessToken(claims), policy).claims().toString());
  }

  @Test
  void expiresAtTheExactExpirationWhenSkewIsZero() throws Exception {
    var claims = JwtTestTokens.accessClaims().put("exp", JwtTestTokens.NOW.getEpochSecond());
    String token = JwtTestTokens.accessToken(claims);
    var policy =
        new JwtValidationPolicy(
            JwtTestTokens.ISSUER,
            Set.of(JwtTestTokens.AUDIENCE),
            Set.of(JwtType.ACCESS),
            Set.of("RS256"),
            Duration.ZERO);

    assertThrows(JwtVerificationException.class, () -> validator().validate(token, policy));
  }

  private static JwtValidator validator() {
    return validator(JwtTestTokens.CLOCK);
  }

  private static JwtValidator validator(Clock clock) {
    return new JwtValidator(signatureVerifiers(), clock, JwtValidatorTest::resolveKey);
  }

  private static Map<String, JwsVerifier> signatureVerifiers() {
    return Map.of(
        "RS256", new Rs256JwsVerifier(), "ML-DSA-65", JwtTestTokens.mlDsaSignatureVerifier());
  }

  private static PublicKey resolveKey(String issuer, String kid, String algorithm, JwtType type) {
    assertEquals(JwtTestTokens.ISSUER, issuer);
    assertEquals(JwtTestTokens.KEY_ID, kid);
    return switch (algorithm) {
      case "RS256" -> JwtTestTokens.RSA_KEYS.getPublic();
      case "ML-DSA-65" -> JwtTestTokens.ML_DSA_KEYS.getPublic();
      default -> throw new AssertionError("Unexpected algorithm: " + algorithm);
    };
  }

  private static void replaceField(ObjectNode object, String field, String value)
      throws IOException {
    if (value.equals("<missing>")) {
      object.remove(field);
    } else {
      object.set(field, JwtTestTokens.json(value));
    }
  }

  private static Stream<Arguments> invalidHeaderFields() {
    return invalidTextFields("alg", "typ", "kid");
  }

  private static Stream<Arguments> invalidAccessTextClaims() {
    return invalidTextFields("iss", "sub", "jti", "client_id");
  }

  private static Stream<Arguments> invalidRefreshTextClaims() {
    return invalidTextFields("sid", "family_id");
  }

  private static Stream<Arguments> invalidTextFields(String... fields) {
    return Stream.of(fields)
        .flatMap(field ->
            Stream.of("<missing>", "null", "1", "false", "[]", "{}", "\"\"", "\" \"")
                .map(value -> Arguments.of(field, value)));
  }

  private static Stream<Arguments> invalidNumericDates() {
    return Stream.of("exp", "nbf", "iat")
        .flatMap(field ->
            Stream.of(
                    "<missing>",
                    "null",
                    "\"123\"",
                    "1.5",
                    "false",
                    "[]",
                    "{}",
                    "9223372036854775808")
                .map(value -> Arguments.of(field, value)));
  }
}
