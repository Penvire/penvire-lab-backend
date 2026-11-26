package com.penvire.lab.identity.adapter.out.persistence.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penvire.lab.identity.adapter.out.persistence.entity.signingkey.SigningKeyEntityRepository;
import com.penvire.lab.identity.application.port.out.SigningKeyPersistencePort;
import com.penvire.lab.identity.domain.signingkey.SigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import com.penvire.lab.identity.domain.signingkey.SigningKeyLifetime;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SigningKeyJpaPersistenceAdapterTest {
  @Inject SigningKeyPersistencePort keys;
  @Inject SigningKeyEntityRepository entities;
  @Inject Clock clock;

  @Test
  void committedRevocationWinsOverRetirementInATransactionThatReadTheOldKey()
      throws NoSuchAlgorithmException {
    var observedAt = clock.instant();
    var start = Instant.ofEpochSecond(observedAt.getEpochSecond() - 60, 123_456_789);
    var lifetime =
        new SigningKeyLifetime(
            start.minusSeconds(60), start, start.plusSeconds(120), start.plusSeconds(180));
    var pair = KeyPairGenerator.getInstance("ML-DSA-65").generateKeyPair();
    var algorithm = SigningKeyAlgorithm.ML_DSA_65;
    var id = UUID.randomUUID();
    var key = new SigningKey(id, algorithm, pair.getPublic(), lifetime);
    var replacement = new SigningKey(UUID.randomUUID(), algorithm, pair.getPublic(), lifetime);

    QuarkusTransaction.requiringNew().run(() -> keys.insert(key, pair.getPrivate()));
    try {
      var failure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  QuarkusTransaction.requiringNew()
                      .run(() -> {
                        var loaded =
                            keys.findPublishedAt(observedAt).stream()
                                .filter(published -> published.id().equals(id))
                                .findFirst()
                                .orElseThrow();
                        assertEquals(lifetime, loaded.lifetime());
                        assertTrue(keys.findSignerAt(algorithm, observedAt).isPresent());

                        QuarkusTransaction.requiringNew().run(() -> keys.revoke(id));
                        keys.retire(loaded.id(), Duration.ofMinutes(5));
                      }));
      assertEquals("Only a signing key that currently signs can be retired", failure.getMessage());

      QuarkusTransaction.requiringNew()
          .run(() -> {
            assertFalse(
                keys.findPublishedAt(observedAt).stream()
                    .anyMatch(published -> published.id().equals(id)));
            assertTrue(keys.findSignerAt(algorithm, observedAt).isEmpty());
            var revoked = entities.findByIdOptional(id).orElseThrow();
            assertNotNull(revoked.getRevokedAt());
            assertNull(revoked.getEncryptedPrivateKey());
            assertNull(revoked.getPrivateKeyNonce());
            keys.insert(replacement, pair.getPrivate());
          });
      QuarkusTransaction.requiringNew()
          .run(() ->
              assertEquals(
                  replacement.id(),
                  keys.findSignerAt(algorithm, observedAt).orElseThrow().key().id()));
    } finally {
      QuarkusTransaction.requiringNew()
          .run(() -> {
            entities.deleteById(replacement.id());
            entities.deleteById(id);
          });
    }
  }
}
