package com.penvire.lab.identity.adapter.out.crypto.signingkey;

import com.penvire.lab.identity.config.SigningKeyConfig;
import jakarta.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.crypto.KDF;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** All configured KEKs, checked and derived once at startup; a bad configuration stops Identity. */
@Singleton
public class KeyEncryptionRing {

  private static final int KEK_BYTES = 32;
  private static final byte[] ENCRYPTION_INFO =
      "penvire signing-key encryption v1".getBytes(StandardCharsets.UTF_8);
  private static final byte[] MAC_INFO =
      "penvire signing-key row-mac v1".getBytes(StandardCharsets.UTF_8);

  private final Map<String, KeyEncryptionKey> keys;
  private final KeyEncryptionKey current;

  KeyEncryptionRing(SigningKeyConfig config) {
    var derived = new HashMap<String, KeyEncryptionKey>();
    config.keys().forEach((id, encoded) -> derived.put(id, derive(id, decode(id, encoded))));
    this.keys = Map.copyOf(derived);

    var configured = keys.get(config.current());
    if (configured == null) {
      throw new IllegalStateException("The current KEK " + config.current() + " is not set.");
    }

    this.current = configured;
  }

  public KeyEncryptionKey current() {
    return current;
  }

  Optional<KeyEncryptionKey> getById(String id) {
    return Optional.ofNullable(keys.get(id));
  }

  private static byte[] decode(String id, String encoded) {
    byte[] material;
    try {
      material = Base64.getDecoder().decode(encoded);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("KEK " + id + " is not base64", e);
    }
    if (material.length != KEK_BYTES) {
      Arrays.fill(material, (byte) 0);
      throw new IllegalStateException("KEK " + id + " must be " + KEK_BYTES + " bytes");
    }
    return material;
  }

  private static KeyEncryptionKey derive(String id, byte[] material) {
    try {
      var ikm = new SecretKeySpec(material, "HKDF");
      var hkdf = KDF.getInstance("HKDF-SHA256");
      return new KeyEncryptionKey(
          id,
          hkdf.deriveKey(
              "AES", HKDFParameterSpec.ofExtract().addIKM(ikm).thenExpand(ENCRYPTION_INFO, 32)),
          hkdf.deriveKey(
              "HmacSHA256", HKDFParameterSpec.ofExtract().addIKM(ikm).thenExpand(MAC_INFO, 32)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Cannot derive keys from KEK " + id, e);
    } finally {
      Arrays.fill(material, (byte) 0);
    }
  }
}
