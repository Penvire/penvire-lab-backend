package com.penvire.lab.identity.adapter.out.crypto;

import com.penvire.lab.identity.application.port.out.SealedPrivateKey;
import com.penvire.lab.identity.application.port.out.SigningKeyProtectorPort;
import com.penvire.lab.identity.domain.signingkey.SigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import jakarta.enterprise.context.ApplicationScoped;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;

/** AES-256-GCM for privaate keys and HMAC-SHA256 for rows, under KEKs from configuration. */
@ApplicationScoped
public class AesGcmSigningKeyProtectorAdapter implements SigningKeyProtectorPort {

  private static final String CIPHER = "AES/GCM/NoPadding";
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final String MAC = "HmacSHA256";

  private final KeyEncryptionRing ring;
  private final SecureRandom random = new SecureRandom();

  AesGcmSigningKeyProtectorAdapter(KeyEncryptionRing ring) {
    this.ring = ring;
  }

  @Override
  public SealedPrivateKey seal(SigningKey key, PrivateKey privateKey) {
    if (!privateKey.getAlgorithm().equals(key.publicKey().getAlgorithm())) {
      throw new IllegalArgumentException("The private key does not belong to key " + key.kid());
    }

    var kek = ring.current();
    byte[] nonce = new byte[NONCE_BYTES];
    random.nextBytes(nonce);
    byte[] plaintext = privateKey.getEncoded();

    try {
      var cipher = Cipher.getInstance(CIPHER);
      cipher.init(Cipher.ENCRYPT_MODE, kek.encryption(), new GCMParameterSpec(TAG_BITS, nonce));
      cipher.updateAAD(SigningKeyEncoding.associatedData(key, kek.id()));
      return new SealedPrivateKey(kek.id(), nonce, cipher.doFinal(plaintext));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Cannot seal the private key of " + key.kid(), e);
    } finally {
      Arrays.fill(plaintext, (byte) 0);
    }
  }

  @Override
  public PrivateKey unseal(SigningKey key, SealedPrivateKey sealed) {
    var kek =
        ring.getById(sealed.kekId())
            .orElseThrow(() -> new IllegalArgumentException("Cannot find KEK " + sealed.kekId()));

    byte[] plaintext;

    try {
      var cipher = Cipher.getInstance(CIPHER);
      cipher.init(
          Cipher.DECRYPT_MODE, kek.encryption(), new GCMParameterSpec(TAG_BITS, sealed.nonce()));
      cipher.updateAAD(SigningKeyEncoding.associatedData(key, kek.id()));
      plaintext = cipher.doFinal(sealed.ciphertext());
    } catch (GeneralSecurityException e) {
      // Never the plaintext or the KEK in the message: aa wrong KEK, nonce or row all end here.
      throw new IllegalStateException("Cannot unseal the private key of " + key.kid(), e);
    }

    try {
      return KeyFactory.getInstance(keyFactoryAlgorithm(key.algorithm()))
          .generatePrivate(new PKCS8EncodedKeySpec(plaintext));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("The private key of " + key.kid() + " is unreadable", e);
    } finally {
      Arrays.fill(plaintext, (byte) 0);
    }
  }

  @Override
  public byte[] mac(SigningKey key, String kekId) {
    var kek =
        ring.getById(kekId).orElseThrow(() -> new IllegalStateException("Unknown KEK " + kekId));
    return compute(kek, key);
  }

  @Override
  public boolean isIntact(SigningKey key, String kekId, byte[] mac) {
    return ring.getById(kekId)
        .map(kek -> MessageDigest.isEqual(compute(kek, key), mac))
        .orElse(false);
  }

  private static byte[] compute(KeyEncryptionKey kek, SigningKey key) {
    try {
      var mac = Mac.getInstance(MAC);
      mac.init(kek.mac());
      return mac.doFinal(SigningKeyEncoding.macInput(key));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Cannot compute the MAC of " + key.kid(), e);
    }
  }

  private static String keyFactoryAlgorithm(SigningKeyAlgorithm algorithm) {
    return switch (algorithm) {
      case ML_DSA_65 -> "ML-DSA";
      case RS256 -> "RSA";
    };
  }
}
