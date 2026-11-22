package com.penvire.lab.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.penvire.lab.clock.MutableClock;
import com.penvire.lab.identity.application.port.out.JwksRepository;
import com.penvire.lab.identity.domain.signingkey.PublicSigningKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwksServiceTest {

  private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

  @Test
  void asksForTheKeysPublishableAtTheCurrentTime() {
    var clock = MutableClock.at(NOW);
    var repository = new RecordingJwksRepository();
    var service = new JwksService(repository, clock);

    service.getJwksSet();
    clock.advance(Duration.ofDays(1));
    service.getJwksSet();

    assertEquals(List.of(NOW, NOW.plus(Duration.ofDays(1))), repository.askedAt);
  }

  private static final class RecordingJwksRepository implements JwksRepository {
    private final List<Instant> askedAt = new ArrayList<>();

    @Override
    public List<PublicSigningKey> findAllPublishablePublicKeys(Instant now) {
      askedAt.add(now);
      return List.of();
    }
  }
}
