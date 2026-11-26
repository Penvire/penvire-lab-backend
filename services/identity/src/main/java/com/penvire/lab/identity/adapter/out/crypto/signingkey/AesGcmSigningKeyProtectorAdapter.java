package com.penvire.lab.identity.adapter.out.crypto.signingkey;

import com.penvire.lab.identity.application.port.out.signingkey.SealedPrivateKey;
import com.penvire.lab.identity.application.port.out.signingkey.SigningKeyProtectorPort;
import com.penvire.lab.identity.domain.signingkey.SigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;

/** AES-256-GCM for private keys and HMAC-SHA256 for rows, under KEKs from configuration. */
@ApplicationScoped
public class AesGcmSigningKeyProtectorAdapter implements SigningKeyProtectorPort {

  private static final String CIPHER = "AES/GCM/NoPadding";
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final String MAC = "HmacSHA256";
  private static final byte[] KEY_PAIR_CHALLENGE =
      "penvire signing-key correspondence v1".getBytes(StandardCharsets.UTF_8);

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

    requireCorrespondingKeys(key, privateKey);

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
            .orElseThrow(() -> new IllegalStateException("Unknown KEK " + sealed.kekId()));

    byte[] plaintext;

    try {
      var cipher = Cipher.getInstance(CIPHER);
      cipher.init(
          Cipher.DECRYPT_MODE, kek.encryption(), new GCMParameterSpec(TAG_BITS, sealed.nonce()));
      cipher.updateAAD(SigningKeyEncoding.associatedData(key, kek.id()));
      plaintext = cipher.doFinal(sealed.ciphertext());
    } catch (GeneralSecurityException e) {
      // Never the plaintext or the KEK in the message: a wrong KEK, nonce or row all end here.
      throw new IllegalStateException("Cannot unseal the private key of " + key.kid(), e);
    }

    try {
      return KeyFactory.getInstance(key.algorithm().keyFactoryAlgorithm())
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

  private void requireCorrespondingKeys(SigningKey key, PrivateKey priv) {
    String alg = getSignatureAlgorithm(key.algorithm());

    byte[] signature;

    try {
      Signature signer = Signature.getInstance(alg);
      signer.initSign(priv);
      signer.update(KEY_PAIR_CHALLENGE);
      signature = signer.sign();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Cannot sign data with key " + key.kid(), e);
    }

    try {
      Signature verifier = Signature.getInstance(alg);
      verifier.initVerify(key.publicKey());
      verifier.update(KEY_PAIR_CHALLENGE);
      if (!verifier.verify(signature)) {
        throw new IllegalArgumentException("The private key does not belong to key " + key.kid());
      }
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Cannot verify data with key " + key.kid(), e);
    }
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

  private static String getSignatureAlgorithm(SigningKeyAlgorithm algorithm) {
    return switch (algorithm) {
      case ML_DSA_65 -> "ML-DSA-65";
      case RS256 -> "SHA256withRSA";
    };
  }
}
