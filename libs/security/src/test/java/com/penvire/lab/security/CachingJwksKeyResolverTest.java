package com.penvire.lab.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.penvire.lab.clock.MutableClock;
import com.penvire.lab.jwt.jwk.PublicJwkCodec;
import com.penvire.lab.jwt.validation.JwtType;
import com.penvire.lab.jwt.validation.JwtVerificationException;
import com.penvire.lab.security.config.SecurityConfig;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CachingJwksKeyResolverTest {

  private static final String ISSUER = "https://id.penvire.test";
  private static final URI JWKS_URI = URI.create("https://id.penvire.test/.well-known/jwks.json");
  private static final Instant START = Instant.parse("2030-01-01T12:00:00Z");

  private static KeyPair mlDsaKey1;
  private static KeyPair mlDsaKey2;
  private static KeyPair rsaKey1;

  private MutableClock clock;
  private AtomicInteger fetchCount;
  private AtomicReference<Supplier<byte[]>> responseSupplier;
  private CachingJwksKeyResolver resolver;

  @BeforeAll
  static void generateKeys() {
    mlDsaKey1 = TestTokenBuilder.generateMlDsa65KeyPair();
    mlDsaKey2 = TestTokenBuilder.generateMlDsa65KeyPair();
    rsaKey1 = TestTokenBuilder.generateRsaKeyPair();
  }

  @BeforeEach
  void setUp() {
    clock = MutableClock.at(START);
    fetchCount = new AtomicInteger();
    responseSupplier = new AtomicReference<>(() -> jwksBytes(mlDsaKey1.getPublic(), "mldsa-1"));

    JwksFetcher stubFetcher =
        (uri, timeout) -> {
          fetchCount.incrementAndGet();
          return responseSupplier.get().get();
        };

    resolver = new CachingJwksKeyResolver(stubFetcher, testConfig(), clock);
  }

  @Test
  void cachesKeysForFiveMinutesWithoutRefetching() {
    PublicKey first = resolver.resolve(ISSUER, "mldsa-1", "ML-DSA-65", JwtType.ACCESS);
    clock.advance(Duration.ofMinutes(4).plusSeconds(59));
    PublicKey second = resolver.resolve(ISSUER, "mldsa-1", "ML-DSA-65", JwtType.ACCESS);

    assertArrayEquals(mlDsaKey1.getPublic().getEncoded(), first.getEncoded());
    assertArrayEquals(first.getEncoded(), second.getEncoded());
    assertEquals(1, fetchCount.get());
  }

  @Test
  void refreshesAfterCacheTtlExpiresAndDoesNotFallBackToStaleKeys() {
    resolver.resolve(ISSUER, "mldsa-1", "ML-DSA-65", JwtType.ACCESS);
    assertEquals(1, fetchCount.get());

    clock.advance(Duration.ofMinutes(5));
    responseSupplier.set(() -> {
      throw new JwksUnavailableException("Identity down");
    });

    assertThrows(
        JwksUnavailableException.class,
        () -> resolver.resolve(ISSUER, "mldsa-1", "ML-DSA-65", JwtType.ACCESS));
    assertEquals(2, fetchCount.get());
  }

  @Test
  void refreshesOnUnknownKeyAfterCooldownAndPicksUpRotatedKey() {
    resolver.resolve(ISSUER, "mldsa-1", "ML-DSA-65", JwtType.ACCESS);
    assertEquals(1, fetchCount.get());

    responseSupplier.set(() -> jwksBytes(mlDsaKey2.getPublic(), "mldsa-2"));
    clock.advance(Duration.ofSeconds(5));

    PublicKey rotated = resolver.resolve(ISSUER, "mldsa-2", "ML-DSA-65", JwtType.ACCESS);

    assertArrayEquals(mlDsaKey2.getPublic().getEncoded(), rotated.getEncoded());
    assertEquals(2, fetchCount.get());
  }

  @Test
  void rateLimitsUnknownKeyRefreshesWithinCooldown() {
    resolver.resolve(ISSUER, "mldsa-1", "ML-DSA-65", JwtType.ACCESS);
    assertEquals(1, fetchCount.get());

    clock.advance(Duration.ofSeconds(4));

    assertThrows(
        JwtVerificationException.class,
        () -> resolver.resolve(ISSUER, "unknown-kid", "ML-DSA-65", JwtType.ACCESS));
    assertEquals(1, fetchCount.get());
  }

  @Test
  void preservesStillFreshKeysWhenUnknownKeyRefreshFails() {
    resolver.resolve(ISSUER, "mldsa-1", "ML-DSA-65", JwtType.ACCESS);
    clock.advance(Duration.ofSeconds(10));

    responseSupplier.set(() -> {
      throw new JwksUnavailableException("JWKS endpoint unavailable");
    });

    assertThrows(
        JwksUnavailableException.class,
        () -> resolver.resolve(ISSUER, "unknown-kid", "ML-DSA-65", JwtType.ACCESS));
    assertEquals(2, fetchCount.get());

    // Previously cached key is still fresh and resolves without hitting the endpoint.
    PublicKey stillFresh = resolver.resolve(ISSUER, "mldsa-1", "ML-DSA-65", JwtType.ACCESS);
    assertArrayEquals(mlDsaKey1.getPublic().getEncoded(), stillFresh.getEncoded());
    assertEquals(2, fetchCount.get());
  }

  @Test
  void coalescesConcurrentRefreshesIntoSingleFetch() throws Exception {
    int threads = 8;
    CountDownLatch enteredFetch = new CountDownLatch(1);
    CountDownLatch releaseFetch = new CountDownLatch(1);
    CountDownLatch startAll = new CountDownLatch(1);

    responseSupplier.set(() -> {
      enteredFetch.countDown();
      try {
        if (!releaseFetch.await(5, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Timed out waiting to release fetch");
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new JwksUnavailableException("Interrupted", exception);
      }
      return jwksBytes(mlDsaKey1.getPublic(), "mldsa-1");
    });

    try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
      List<Future<PublicKey>> futures = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        futures.add(
            pool.submit(() -> {
              startAll.await(5, TimeUnit.SECONDS);
              return resolver.resolve(ISSUER, "mldsa-1", "ML-DSA-65", JwtType.ACCESS);
            }));
      }

      startAll.countDown();
      enteredFetch.await(5, TimeUnit.SECONDS);
      releaseFetch.countDown();

      for (Future<PublicKey> future : futures) {
        assertArrayEquals(
            mlDsaKey1.getPublic().getEncoded(), future.get(5, TimeUnit.SECONDS).getEncoded());
      }
    }

    assertEquals(1, fetchCount.get());
  }

  @Test
  void rejectsAlgorithmMismatchUntrustedIssuerAndNonAccessTokenType() {
    PublicJwkCodec.RsaParameters rsa = PublicJwkCodec.encodeRsa((RSAPublicKey) rsaKey1.getPublic());
    responseSupplier.set(() ->
        """
        {
          "keys": [
            {"kty": "RSA", "alg": "RS256", "use": "sig", "kid": "rsa-1", "n": "%s", "e": "%s"}
          ]
        }
        """
            .formatted(rsa.modulus(), rsa.exponent())
            .getBytes(StandardCharsets.UTF_8));

    assertThrows(
        JwtVerificationException.class,
        () -> resolver.resolve("https://evil.test", "rsa-1", "RS256", JwtType.ACCESS));
    assertThrows(
        JwtVerificationException.class,
        () -> resolver.resolve(ISSUER, "rsa-1", "RS256", JwtType.REFRESH));
    assertThrows(
        JwtVerificationException.class,
        () -> resolver.resolve(ISSUER, "rsa-1", "ML-DSA-65", JwtType.ACCESS));
  }

  @Test
  void treatsMalformedJwksPayloadAsUnavailable() {
    responseSupplier.set(() -> "not-json".getBytes(StandardCharsets.UTF_8));

    assertThrows(
        JwksUnavailableException.class,
        () -> resolver.resolve(ISSUER, "mldsa-1", "ML-DSA-65", JwtType.ACCESS));
  }

  private static byte[] jwksBytes(PublicKey mlDsaPublicKey, String kid) {
    String pub = PublicJwkCodec.encodeMlDsa65(mlDsaPublicKey);
    return """
    {
      "keys": [
        {"kty": "AKP", "alg": "ML-DSA-65", "use": "sig", "kid": "%s", "pub": "%s"}
      ]
    }
    """
        .formatted(kid, pub)
        .getBytes(StandardCharsets.UTF_8);
  }

  private static SecurityConfig testConfig() {
    return new SecurityConfig() {
      @Override
      public String issuer() {
        return ISSUER;
      }

      @Override
      public Set<String> audiences() {
        return Set.of("test-api");
      }

      @Override
      public Set<String> allowedAlgorithms() {
        return Set.of("ML-DSA-65", "RS256");
      }

      @Override
      public Duration clockSkew() {
        return Duration.ofSeconds(30);
      }

      @Override
      public URI jwksUri() {
        return JWKS_URI;
      }

      @Override
      public Duration jwksCacheTtl() {
        return Duration.ofMinutes(5);
      }

      @Override
      public Duration jwksRefreshCooldown() {
        return Duration.ofSeconds(5);
      }

      @Override
      public Duration jwksTimeout() {
        return Duration.ofSeconds(3);
      }
    };
  }
}
