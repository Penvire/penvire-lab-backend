package com.penvire.lab.identity.application;

import com.penvire.lab.identity.application.port.out.signingkey.SigningKeyGeneratorPort;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.EnumMap;
import java.util.Map;

/** Returns one fixed key pair per algorithm, so tests do not wait for RSA key generation. */
final class FixedKeyPairs implements SigningKeyGeneratorPort {
  private static final Map<SigningKeyAlgorithm, KeyPair> PAIRS = generateAll();

  @Override
  public KeyPair generate(SigningKeyAlgorithm algorithm) {
    return pair(algorithm);
  }

  KeyPair pair(SigningKeyAlgorithm algorithm) {
    return java.util.Objects.requireNonNull(PAIRS.get(algorithm));
  }

  private static Map<SigningKeyAlgorithm, KeyPair> generateAll() {
    var pairs = new EnumMap<SigningKeyAlgorithm, KeyPair>(SigningKeyAlgorithm.class);
    try {
      pairs.put(
          SigningKeyAlgorithm.ML_DSA_65,
          KeyPairGenerator.getInstance("ML-DSA-65").generateKeyPair());
      pairs.put(SigningKeyAlgorithm.RS256, KeyPairGenerator.getInstance("RSA").generateKeyPair());
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
    return pairs;
  }
}
