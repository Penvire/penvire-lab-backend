package com.penvire.lab.identity.adapter.out.persistence.entity;

import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

/**
 * A signing key as stored (ADR 0007). Holds only stored bytes: the out-adapter encrypts the private
 * key and computes the row MAC before saving, and checks both after loading.
 */
@Entity
@Getter
@Setter
@Table(name = "signing_keys")
@SuppressWarnings("NullAway.Init")
public class SigningKeyEntity {

  /** Assigned by {@code IIdGenerator}; its string form is the JWS {@code kid} */
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "signing_key_algorithm", nullable = false, updatable = false)
  private SigningKeyAlgorithm signingKeyAlgorithm;

  /** X.509 SubjectPublicKeyInfo (DER), as {@code PublicKey.getEncoded()} returns it. */
  @Column(nullable = false, name = "public_key", updatable = false)
  private byte[] publicKey;

  /** AES-256-GCM ciphertext of the PKCS#8 private key, tag included; null once retired. */
  @Column(name = "encrypted_private_key")
  private byte @Nullable [] encryptedPrivateKey;

  /** The 12-byte GCM nonce; null together with {@link #encryptedPrivateKey}. */
  @Column(name = "private_key_nonce")
  private byte @Nullable [] privateKeyNonce;

  /** The KEK that encrypts the private key and derives the row MAC key. */
  @Column(name = "kek_id", nullable = false)
  private String kekId;

  /** HMAC-SHA256 over id, purpose, algorithm, public key and the four instants. */
  @Column(name = "row_mac", nullable = false)
  private byte[] rowMac;

  @Column(name = "publish_from", nullable = false, updatable = false)
  private Instant publishFrom;

  @Column(name = "sign_from", nullable = false, updatable = false)
  private Instant signFrom;

  @Column(name = "sign_until", nullable = false)
  private Instant signUntil;

  @Column(name = "publish_until", nullable = false)
  private Instant publishUntil;

  @Column(name = "revoked_at")
  @Nullable
  private Instant revokedAt;
}
