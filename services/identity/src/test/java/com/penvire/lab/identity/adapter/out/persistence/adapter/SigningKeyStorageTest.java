package com.penvire.lab.identity.adapter.out.persistence.adapter;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penvire.lab.identity.adapter.out.persistence.entity.SigningKeyEntity;
import com.penvire.lab.identity.adapter.out.persistence.entity.SigningKeyEntityRepository;
import com.penvire.lab.identity.application.port.out.SigningKeyPort;
import com.penvire.lab.identity.domain.signingkey.SigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import com.penvire.lab.identity.domain.signingkey.SigningKeyLifetime;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.TransactionalException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The persistence adapter against PostgreSQL from Dev Services: what it stores, what it returns,
 * how it treats tampered rows, and the constraints of V1. Retiring and revoking read the injected
 * system clock, so every lifetime here is placed around the current instant.
 */
@QuarkusTest
class SigningKeyStorageTest {

  private static final KeyPair ML_DSA = generate("ML-DSA-65");
  private static final KeyPair RSA = generate("RSA");

  @Inject SigningKeyPort keys;
  @Inject SigningKeyEntityRepository entities;
  @Inject Clock clock;

  private Instant now;

  @BeforeEach
  void emptyTheTable() {
    now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
    deleteAll();
  }

  @AfterEach
  void cleanUp() {
    deleteAll();
  }

  private void deleteAll() {
    QuarkusTransaction.requiringNew().run(() -> entities.deleteAll());
  }

  private static KeyPair generate(String algorithm) {
    try {
      return KeyPairGenerator.getInstance(algorithm).generateKeyPair();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private static KeyPair pairFor(SigningKeyAlgorithm algorithm) {
    return algorithm == SigningKeyAlgorithm.ML_DSA_65 ? ML_DSA : RSA;
  }

  /** Published an hour ago, signing since half an hour ago, for another hour. */
  private SigningKeyLifetime current() {
    return new SigningKeyLifetime(
        now.minus(Duration.ofHours(1)),
        now.minus(Duration.ofMinutes(30)),
        now.plus(Duration.ofHours(1)),
        now.plus(Duration.ofHours(2)));
  }

  private SigningKey key(SigningKeyAlgorithm algorithm, SigningKeyLifetime lifetime) {
    return new SigningKey(UUID.randomUUID(), algorithm, pairFor(algorithm).getPublic(), lifetime);
  }

  private SigningKey stored(SigningKeyAlgorithm algorithm, SigningKeyLifetime lifetime) {
    var key = key(algorithm, lifetime);
    inTransaction(() -> keys.insert(key, pairFor(algorithm).getPrivate()));
    return key;
  }

  private static void inTransaction(Runnable work) {
    QuarkusTransaction.requiringNew().run(work);
  }

  private static <T> T fetch(Supplier<T> work) {
    return QuarkusTransaction.requiringNew().call(work::get);
  }

  private SigningKeyEntity entity(UUID id) {
    return fetch(() -> entities.findByIdOptional(id).orElseThrow());
  }

  private List<UUID> publishedAt(Instant instant) {
    return fetch(() -> keys.findPublishedAt(instant).stream().map(SigningKey::id).toList());
  }

  private boolean signsAt(SigningKeyAlgorithm algorithm, Instant instant) {
    return fetch(() -> keys.findSignerAt(algorithm, instant).isPresent());
  }

  /** Runs SQL that bypasses the adapter, as someone with write access to the database could. */
  private int sql(String statement, UUID id) {
    return fetch(() ->
        entities
            .getEntityManager()
            .createNativeQuery(statement)
            .setParameter("id", id)
            .executeUpdate());
  }

  // Storing and reading back

  @ParameterizedTest
  @EnumSource(SigningKeyAlgorithm.class)
  void readsBackWhatItStored(SigningKeyAlgorithm algorithm) {
    var key = stored(algorithm, current());

    var loaded = fetch(() -> keys.findPublishedAt(now)).getFirst();

    assertAll(
        () -> assertEquals(key.id(), loaded.id()),
        () -> assertEquals(key.algorithm(), loaded.algorithm()),
        () -> assertEquals(key.lifetime(), loaded.lifetime()),
        () -> assertArrayEquals(key.publicKey().getEncoded(), loaded.publicKey().getEncoded()));
  }

  @Test
  void readsBackInstantsWithSubMicrosecondPrecision() {
    var lifetime =
        new SigningKeyLifetime(
            now.minusSeconds(60).plusNanos(123_456_789),
            now.minusSeconds(30).plusNanos(1),
            now.plusSeconds(60).plusNanos(999),
            now.plusSeconds(120).plusNanos(999_999_999));
    var key = stored(SigningKeyAlgorithm.ML_DSA_65, lifetime);

    var loaded = fetch(() -> keys.findPublishedAt(now)).getFirst();

    assertEquals(key.lifetime(), loaded.lifetime());
  }

  @Test
  void storesThePrivateKeyOnlyEncrypted() {
    var key = stored(SigningKeyAlgorithm.ML_DSA_65, current());
    byte[] pkcs8 = ML_DSA.getPrivate().getEncoded();

    var entity = entity(key.id());

    assertAll(
        () -> assertEquals("test", entity.getKekId()),
        () -> assertEquals(12, Objects.requireNonNull(entity.getPrivateKeyNonce()).length),
        () -> assertEquals(32, entity.getRowMac().length),
        () ->
            assertEquals(
                pkcs8.length + 16, Objects.requireNonNull(entity.getEncryptedPrivateKey()).length),
        () ->
            assertFalse(
                Arrays.equals(
                    pkcs8,
                    Arrays.copyOf(
                        Objects.requireNonNull(entity.getEncryptedPrivateKey()), pkcs8.length))));
  }

  @Test
  void refusesToStoreARevokedKey() {
    var key = key(SigningKeyAlgorithm.ML_DSA_65, current().revokeAt(now));

    assertThrows(
        IllegalArgumentException.class,
        () -> inTransaction(() -> keys.insert(key, ML_DSA.getPrivate())));
    assertTrue(fetch(() -> entities.findAll().list()).isEmpty());
  }

  @Test
  void refusesToStoreAPrivateKeyThatDoesNotBelongToTheKey() {
    var key = key(SigningKeyAlgorithm.ML_DSA_65, current());
    var other = generate("ML-DSA-65");

    assertThrows(
        IllegalArgumentException.class,
        () -> inTransaction(() -> keys.insert(key, other.getPrivate())));
    assertTrue(fetch(() -> entities.findAll().list()).isEmpty());
  }

  @Test
  void requiresTheCallerToOwnTheTransaction() {
    assertThrows(TransactionalException.class, () -> keys.findPublishedAt(now));
  }

  // Publication and signing windows

  @Test
  void publishesFromPublishFromInclusiveToPublishUntilExclusive() {
    var key = stored(SigningKeyAlgorithm.ML_DSA_65, current());
    var lifetime = key.lifetime();

    assertAll(
        () -> assertEquals(List.of(), publishedAt(lifetime.publishFrom().minusNanos(1_000))),
        () -> assertEquals(List.of(key.id()), publishedAt(lifetime.publishFrom())),
        () ->
            assertEquals(List.of(key.id()), publishedAt(lifetime.publishUntil().minusNanos(1_000))),
        () -> assertEquals(List.of(), publishedAt(lifetime.publishUntil())));
  }

  @Test
  void publishesTheKeysOfEveryAlgorithm() {
    var mlDsa = stored(SigningKeyAlgorithm.ML_DSA_65, current());
    var rsa = stored(SigningKeyAlgorithm.RS256, current());

    assertEquals(Set.of(mlDsa.id(), rsa.id()), Set.copyOf(publishedAt(now)));
  }

  @ParameterizedTest
  @EnumSource(SigningKeyAlgorithm.class)
  void handsOutAPrivateKeyThatSignsForThePublishedKey(SigningKeyAlgorithm algorithm)
      throws GeneralSecurityException {
    var key = stored(algorithm, current());
    var active = fetch(() -> keys.findSignerAt(algorithm, now)).orElseThrow();
    var jca = algorithm == SigningKeyAlgorithm.ML_DSA_65 ? "ML-DSA-65" : "SHA256withRSA";
    byte[] message = {1, 2, 3};

    var signer = Signature.getInstance(jca);
    signer.initSign(active.privateKey());
    signer.update(message);
    byte[] signature = signer.sign();
    var verifier = Signature.getInstance(jca);
    verifier.initVerify(key.publicKey());
    verifier.update(message);

    assertEquals(key.id(), active.key().id());
    assertTrue(verifier.verify(signature));
  }

  @Test
  void signsFromSignFromInclusiveToSignUntilExclusive() {
    var key = stored(SigningKeyAlgorithm.ML_DSA_65, current());
    var lifetime = key.lifetime();
    var algorithm = SigningKeyAlgorithm.ML_DSA_65;

    assertAll(
        () -> assertFalse(signsAt(algorithm, lifetime.signFrom().minusNanos(1_000))),
        () -> assertTrue(signsAt(algorithm, lifetime.signFrom())),
        () -> assertTrue(signsAt(algorithm, lifetime.signUntil().minusNanos(1_000))),
        () -> assertFalse(signsAt(algorithm, lifetime.signUntil())));
  }

  @Test
  void findsNoSignerForAnotherAlgorithm() {
    stored(SigningKeyAlgorithm.ML_DSA_65, current());

    assertFalse(signsAt(SigningKeyAlgorithm.RS256, now));
  }

  // Tampered rows

  @ParameterizedTest
  @ValueSource(
      strings = {
        "update signing_keys set publish_from = publish_from - interval '1 second' where id = :id",
        "update signing_keys set sign_from = sign_from - interval '1 second' where id = :id",
        "update signing_keys set sign_until = sign_until + interval '1 microsecond' where id = :id",
        "update signing_keys set publish_until = publish_until + interval '1 day' where id = :id",
        "update signing_keys set kek_id = 'gone' where id = :id",
        "update signing_keys set row_mac = decode(repeat('00', 32), 'hex') where id = :id",
        "update signing_keys set public_key = public_key || '\\x00'::bytea where id = :id",
      })
  void ignoresARowChangedOutsideTheAdapter(String tampering) {
    var key = stored(SigningKeyAlgorithm.ML_DSA_65, current());

    assertEquals(1, sql(tampering, key.id()));

    assertAll(
        () -> assertEquals(List.of(), publishedAt(now)),
        () -> assertFalse(signsAt(SigningKeyAlgorithm.ML_DSA_65, now)),
        () ->
            assertThrows(
                IllegalStateException.class,
                () -> inTransaction(() -> keys.retire(key.id(), Duration.ofMinutes(5)))),
        () ->
            assertThrows(
                IllegalStateException.class, () -> inTransaction(() -> keys.revoke(key.id()))));
  }

  @Test
  void keepsAnUntamperedKeyBesideATamperedOne() {
    var tampered = stored(SigningKeyAlgorithm.ML_DSA_65, current());
    var intact = stored(SigningKeyAlgorithm.RS256, current());

    sql("update signing_keys set kek_id = 'gone' where id = :id", tampered.id());

    assertEquals(List.of(intact.id()), publishedAt(now));
  }

  @Test
  void doesNotRepublishARevokedKeyWhoseRevocationWasRemovedByHand() {
    var key = stored(SigningKeyAlgorithm.ML_DSA_65, current());
    inTransaction(() -> keys.revoke(key.id()));

    sql("update signing_keys set revoked_at = null where id = :id", key.id());

    assertEquals(List.of(), publishedAt(now));
  }

  @Test
  void refusesToSignWithACiphertextMovedFromAnotherKey() {
    var mlDsa = stored(SigningKeyAlgorithm.ML_DSA_65, current());
    var rsa = stored(SigningKeyAlgorithm.RS256, current());

    sql(
        "update signing_keys set (encrypted_private_key, private_key_nonce) ="
            + " (select encrypted_private_key, private_key_nonce from signing_keys"
            + " where id = '"
            + rsa.id()
            + "') where id = :id",
        mlDsa.id());

    // The public part is intact, so the key stays published; only signing fails, loudly.
    assertEquals(Set.of(mlDsa.id(), rsa.id()), Set.copyOf(publishedAt(now)));
    assertThrows(
        IllegalStateException.class,
        () -> fetch(() -> keys.findSignerAt(SigningKeyAlgorithm.ML_DSA_65, now)));
  }

  // Retiring

  @Test
  void retiringStopsSigningAndKeepsTheKeyPublishedForTheRetention() {
    var key = stored(SigningKeyAlgorithm.ML_DSA_65, current());
    var retention = Duration.ofMinutes(5);

    inTransaction(() -> keys.retire(key.id(), retention));

    var entity = entity(key.id());
    var signUntil = entity.getSignUntil();
    assertAll(
        () -> assertFalse(signUntil.isBefore(now)),
        () -> assertFalse(signUntil.isAfter(clock.instant())),
        () -> assertEquals(signUntil.plus(retention), entity.getPublishUntil()),
        () -> assertFalse(signsAt(SigningKeyAlgorithm.ML_DSA_65, signUntil)),
        () -> assertEquals(List.of(key.id()), publishedAt(signUntil)),
        () -> assertEquals(List.of(), publishedAt(signUntil.plus(retention))));
  }

  @Test
  void cannotRetireAKeyThatDoesNotSignYet() {
    var pending =
        stored(
            SigningKeyAlgorithm.ML_DSA_65,
            new SigningKeyLifetime(
                now.minusSeconds(60), now.plus(Duration.ofHours(1)),
                now.plus(Duration.ofHours(2)), now.plus(Duration.ofHours(3))));

    assertThrows(
        IllegalStateException.class,
        () -> inTransaction(() -> keys.retire(pending.id(), Duration.ofMinutes(5))));
  }

  @Test
  void cannotRetireAnUnknownKey() {
    assertThrows(
        IllegalStateException.class,
        () -> inTransaction(() -> keys.retire(UUID.randomUUID(), Duration.ofMinutes(5))));
  }

  // Revoking

  @Test
  void revokingErasesThePrivateKeyAndUnpublishesTheKeyAtOnce() {
    var key = stored(SigningKeyAlgorithm.ML_DSA_65, current());

    inTransaction(() -> keys.revoke(key.id()));

    var entity = entity(key.id());
    assertAll(
        () -> assertNull(entity.getEncryptedPrivateKey()),
        () -> assertNull(entity.getPrivateKeyNonce()),
        () -> assertFalse(entity.getRevokedAt() == null),
        () -> assertEquals(key.lifetime().signUntil(), entity.getSignUntil()),
        () -> assertEquals(key.lifetime().publishUntil(), entity.getPublishUntil()),
        () -> assertEquals(List.of(), publishedAt(now)),
        () -> assertFalse(signsAt(SigningKeyAlgorithm.ML_DSA_65, now)));
  }

  @Test
  void canRevokeAKeyBeforeItIsPublished() {
    var future =
        stored(
            SigningKeyAlgorithm.ML_DSA_65,
            new SigningKeyLifetime(
                now.plus(Duration.ofHours(1)), now.plus(Duration.ofHours(2)),
                now.plus(Duration.ofHours(3)), now.plus(Duration.ofHours(4))));

    inTransaction(() -> keys.revoke(future.id()));

    assertEquals(List.of(), publishedAt(now.plus(Duration.ofHours(2))));
    assertNull(entity(future.id()).getEncryptedPrivateKey());
  }

  @Test
  void cannotRevokeTwice() {
    var key = stored(SigningKeyAlgorithm.ML_DSA_65, current());
    inTransaction(() -> keys.revoke(key.id()));

    assertThrows(IllegalStateException.class, () -> inTransaction(() -> keys.revoke(key.id())));
  }

  @Test
  void cannotRevokeAnUnknownKey() {
    assertThrows(
        IllegalStateException.class, () -> inTransaction(() -> keys.revoke(UUID.randomUUID())));
  }

  @Test
  void aRevokedKeyMakesRoomForANewSigner() {
    var revoked = stored(SigningKeyAlgorithm.ML_DSA_65, current());
    inTransaction(() -> keys.revoke(revoked.id()));

    var replacement = stored(SigningKeyAlgorithm.ML_DSA_65, current());

    assertEquals(
        replacement.id(),
        fetch(() -> keys.findSignerAt(SigningKeyAlgorithm.ML_DSA_65, now))
            .orElseThrow()
            .key()
            .id());
  }

  // Cleanup

  @Test
  void erasesThePrivateKeysOfKeysThatNoLongerSign() {
    var retired =
        stored(
            SigningKeyAlgorithm.ML_DSA_65,
            new SigningKeyLifetime(
                now.minus(Duration.ofHours(3)), now.minus(Duration.ofHours(3)),
                now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(1))));
    var signing = stored(SigningKeyAlgorithm.ML_DSA_65, current());

    int erased = fetch(() -> keys.erasePrivateKeysAt(now));

    assertAll(
        () -> assertEquals(1, erased),
        () -> assertNull(entity(retired.id()).getEncryptedPrivateKey()),
        () -> assertNull(entity(retired.id()).getPrivateKeyNonce()),
        // Erasing needs no new MAC: the retired key stays published and intact.
        () -> assertEquals(Set.of(retired.id(), signing.id()), Set.copyOf(publishedAt(now))),
        () -> assertTrue(signsAt(SigningKeyAlgorithm.ML_DSA_65, now)),
        () -> assertEquals(0, (int) fetch(() -> keys.erasePrivateKeysAt(now))));
  }

  @Test
  void deletesOnlyKeysPastTheirPublication() {
    var expired =
        stored(
            SigningKeyAlgorithm.ML_DSA_65,
            new SigningKeyLifetime(
                now.minus(Duration.ofHours(3)),
                now.minus(Duration.ofHours(3)),
                now.minus(Duration.ofHours(2)),
                now));
    var live = stored(SigningKeyAlgorithm.ML_DSA_65, current());

    int deleted = fetch(() -> keys.deleteExpiredAt(now));

    assertAll(
        () -> assertEquals(1, deleted),
        () -> assertTrue(fetch(() -> entities.findByIdOptional(expired.id())).isEmpty()),
        () -> assertTrue(fetch(() -> entities.findByIdOptional(live.id())).isPresent()));
  }

  // Database constraints, checked without the adapter

  @Test
  void theDatabaseRejectsASecondUnrevokedSignerForTheSameAlgorithm() {
    stored(SigningKeyAlgorithm.ML_DSA_65, current());
    var overlapping = key(SigningKeyAlgorithm.ML_DSA_65, current());

    assertThrows(
        PersistenceException.class,
        () ->
            inTransaction(() -> {
              keys.insert(overlapping, ML_DSA.getPrivate());
              entities.flush();
            }));
  }

  @Test
  void theDatabaseAllowsAdjacentSignersOfOneAlgorithmAndOverlappingSignersOfTwo() {
    var lifetime = current();
    stored(SigningKeyAlgorithm.ML_DSA_65, lifetime);
    stored(
        SigningKeyAlgorithm.ML_DSA_65,
        new SigningKeyLifetime(
            lifetime.signUntil(),
            lifetime.signUntil(),
            lifetime.signUntil().plus(Duration.ofHours(1)),
            lifetime.signUntil().plus(Duration.ofHours(2))));
    stored(SigningKeyAlgorithm.RS256, lifetime);

    assertEquals(3L, fetch(() -> entities.count()));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        // A revoked key must not keep its private key.
        "update signing_keys set revoked_at = publish_from where id = :id",
        // Revoked at or after expiry.
        "update signing_keys set revoked_at = publish_until, encrypted_private_key = null,"
            + " private_key_nonce = null where id = :id",
        // Ciphertext and nonce come together.
        "update signing_keys set private_key_nonce = null where id = :id",
        "update signing_keys set encrypted_private_key = null where id = :id",
        "update signing_keys set private_key_nonce = '\\x00'::bytea where id = :id",
        "update signing_keys set row_mac = '\\x00'::bytea where id = :id",
        "update signing_keys set signing_key_algorithm = 'ES256' where id = :id",
        // Instants out of order.
        "update signing_keys set sign_from = publish_from - interval '1 second' where id = :id",
        "update signing_keys set sign_until = sign_from where id = :id",
        "update signing_keys set publish_until = sign_until - interval '1 second' where id = :id",
      })
  void theDatabaseRejects(String violation) {
    var key = stored(SigningKeyAlgorithm.ML_DSA_65, current());

    assertThrows(PersistenceException.class, () -> sql(violation, key.id()));
  }

  @Test
  void theDatabaseAllowsARevocationBeforePublication() {
    var key = stored(SigningKeyAlgorithm.ML_DSA_65, current());

    assertEquals(
        1,
        sql(
            "update signing_keys set revoked_at = publish_from - interval '1 hour',"
                + " encrypted_private_key = null, private_key_nonce = null where id = :id",
            key.id()));
  }
}
