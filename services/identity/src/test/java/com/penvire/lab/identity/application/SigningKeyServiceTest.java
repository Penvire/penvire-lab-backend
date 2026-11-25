package com.penvire.lab.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.penvire.lab.clock.MutableClock;
import com.penvire.lab.identity.application.port.out.SigningKeyPort;
import com.penvire.lab.identity.domain.signingkey.ActiveSigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SigningKeyServiceTest {

  private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

  @Test
  void asksForTheKeysPublishedAtTheCurrentTime() {
    var clock = MutableClock.at(NOW);
    var signingKeys = new RecordingSigningKeys();
    var service = new SigningKeyService(signingKeys, clock);

    service.getJwksSet();
    clock.advance(Duration.ofDays(1));
    service.getJwksSet();

    assertEquals(List.of(NOW, NOW.plus(Duration.ofDays(1))), signingKeys.askedAt);
  }

  /** Records when the published keys were asked for; every other operation is not expected. */
  private static final class RecordingSigningKeys implements SigningKeyPort {
    private final List<Instant> askedAt = new ArrayList<>();

    @Override
    public List<SigningKey> findPublishedAt(Instant now) {
      askedAt.add(now);
      return List.of();
    }

    @Override
    public void insert(SigningKey key, PrivateKey privateKey) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void retire(UUID id, Duration retention) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void revoke(UUID id) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<ActiveSigningKey> findSignerAt(SigningKeyAlgorithm algorithm, Instant now) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int erasePrivateKeysAt(Instant now) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int deleteExpiredAt(Instant now) {
      throw new UnsupportedOperationException();
    }
  }
}
