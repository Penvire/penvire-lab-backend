package com.penvire.lab.security;

import com.penvire.lab.jwt.jwk.JwkPublicKey;
import com.penvire.lab.jwt.jwk.JwksParseException;
import com.penvire.lab.jwt.jwk.JwksParser;
import com.penvire.lab.jwt.validation.JwtType;
import com.penvire.lab.jwt.validation.JwtVerificationException;
import com.penvire.lab.jwt.validation.JwtVerificationKeyResolver;
import com.penvire.lab.security.config.SecurityConfig;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.jspecify.annotations.Nullable;

@ApplicationScoped
@DefaultBean
public class CachingJwksKeyResolver implements JwtVerificationKeyResolver {

  private record CacheSnapshot(Map<String, JwkPublicKey> keys, Instant expiresAt) {
    boolean isFresh(Instant now) {
      return now.isBefore(expiresAt);
    }
  }

  private final JwksFetcher fetcher;
  private final SecurityConfig config;
  private final Clock clock;
  private final JwksParser parser = new JwksParser();

  private final Object refreshLock = new Object();
  private volatile @Nullable CacheSnapshot cache;
  private volatile @Nullable Instant lastRefreshAttempt;
  private @Nullable CompletableFuture<Map<String, JwkPublicKey>> inFlight;

  public CachingJwksKeyResolver(JwksFetcher fetcher, SecurityConfig config, Clock clock) {
    this.fetcher = fetcher;
    this.config = config;
    this.clock = clock;
  }

  @Override
  public PublicKey resolve(String trustedIssuer, String keyId, String algorithm, JwtType type) {
    if (!config.issuer().equals(trustedIssuer) || type != JwtType.ACCESS) {
      throw new JwtVerificationException("Untrusted issuer or token type");
    }
    Instant now = clock.instant();
    CacheSnapshot current = cache;
    if (current != null && current.isFresh(now)) {
      JwkPublicKey hit = current.keys().get(keyId);
      if (hit != null) {
        return matchAlgorithm(hit, algorithm);
      }
      Map<String, JwkPublicKey> refreshed = refreshForUnknownKey(keyId);
      return findOrThrow(refreshed, keyId, algorithm);
    }
    Map<String, JwkPublicKey> refreshed = refreshExpired();
    return findOrThrow(refreshed, keyId, algorithm);
  }

  private Map<String, JwkPublicKey> refreshExpired() {
    CompletableFuture<Map<String, JwkPublicKey>> future;
    boolean leader = false;
    synchronized (refreshLock) {
      Instant now = clock.instant();
      CacheSnapshot current = cache;
      if (current != null && current.isFresh(now)) {
        return current.keys();
      }
      if (inFlight != null) {
        future = inFlight;
      } else {
        lastRefreshAttempt = now;
        inFlight = new CompletableFuture<>();
        future = inFlight;
        leader = true;
      }
    }
    return executeOrJoin(future, leader);
  }

  private Map<String, JwkPublicKey> refreshForUnknownKey(String keyId) {
    CompletableFuture<Map<String, JwkPublicKey>> future;
    boolean leader = false;
    synchronized (refreshLock) {
      Instant now = clock.instant();
      CacheSnapshot current = cache;
      if (current != null && current.isFresh(now)) {
        if (current.keys().containsKey(keyId)) {
          return current.keys();
        }
        if (inFlight != null) {
          future = inFlight;
        } else if (lastRefreshAttempt != null
            && now.isBefore(lastRefreshAttempt.plus(config.jwksRefreshCooldown()))) {
          throw new JwtVerificationException("Unknown verification key");
        } else {
          lastRefreshAttempt = now;
          inFlight = new CompletableFuture<>();
          future = inFlight;
          leader = true;
        }
      } else if (inFlight != null) {
        future = inFlight;
      } else {
        lastRefreshAttempt = now;
        inFlight = new CompletableFuture<>();
        future = inFlight;
        leader = true;
      }
    }
    return executeOrJoin(future, leader);
  }

  private Map<String, JwkPublicKey> executeOrJoin(
      CompletableFuture<Map<String, JwkPublicKey>> future, boolean leader) {
    if (leader) {
      try {
        byte[] body = fetcher.fetch(config.jwksUri(), config.jwksTimeout());
        Map<String, JwkPublicKey> parsed = parser.parse(body);
        synchronized (refreshLock) {
          cache = new CacheSnapshot(parsed, clock.instant().plus(config.jwksCacheTtl()));
          inFlight = null;
        }
        future.complete(parsed);
        return parsed;
      } catch (JwksUnavailableException | JwksParseException exception) {
        JwksUnavailableException failure =
            exception instanceof JwksUnavailableException unavailable
                ? unavailable
                : new JwksUnavailableException("Invalid JWKS response", exception);
        synchronized (refreshLock) {
          inFlight = null;
        }
        future.completeExceptionally(failure);
        throw failure;
      }
    }
    try {
      return future.join();
    } catch (CompletionException exception) {
      if (exception.getCause() instanceof JwksUnavailableException unavailable) {
        throw unavailable;
      }
      throw new JwksUnavailableException("JWKS refresh failed", exception);
    }
  }

  private static PublicKey findOrThrow(
      Map<String, JwkPublicKey> keys, String keyId, String algorithm) {
    JwkPublicKey key = keys.get(keyId);
    if (key == null) {
      throw new JwtVerificationException("Unknown verification key");
    }
    return matchAlgorithm(key, algorithm);
  }

  private static PublicKey matchAlgorithm(JwkPublicKey key, String algorithm) {
    if (!key.algorithm().equals(algorithm)) {
      throw new JwtVerificationException("Key algorithm mismatch");
    }
    return key.publicKey();
  }
}
