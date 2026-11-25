package com.penvire.lab.identity.application.port.out;

import com.penvire.lab.identity.domain.signingkey.SigningKey;
import java.security.PrivateKey;

/**
 * Protects stored signing keys (ADR 0007): encrypts their private part and authenticates every
 * other value with a MAC, both under a KEK that never enters the database.
 */
public interface SigningKeyProtectorPort {

  /**
   * Encrypts {@code privateKey} under the current KEK, bound to the key's id, algorithm and public
   * key.
   */
  SealedPrivateKey seal(SigningKey key, PrivateKey privateKey);

  /**
   * Decrypts the private part of {@code key}.
   *
   * @throws IllegalStateException if the KEK is unknown or the private part does not decrypt
   */
  PrivateKey unseal(SigningKey key, SealedPrivateKey sealed);

  /**
   * The MAC over the key's id, algorithm, public key, lifetime and revocation under KEK {@code
   * kekId}.
   */
  byte[] mac(SigningKey key, String kekId);

  /** Whether {@code mac} matches the key; false for an unknown KEK. */
  boolean isIntact(SigningKey key, String kekId, byte[] mac);
}
