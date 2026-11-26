package com.penvire.lab.identity.adapter.out.crypto.signingkey;

import com.penvire.lab.identity.application.port.out.signingkey.SigningKeyGeneratorPort;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import jakarta.enterprise.context.ApplicationScoped;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.NamedParameterSpec;

@ApplicationScoped
public class JcaSigningKeyGeneratorAdapter implements SigningKeyGeneratorPort {

  @Override
  public KeyPair generate(SigningKeyAlgorithm algorithm) {
    try {
      var generator = KeyPairGenerator.getInstance(algorithm.keyFactoryAlgorithm());
      switch (algorithm) {
        case ML_DSA_65 -> generator.initialize(NamedParameterSpec.ML_DSA_65);
        case RS256 -> generator.initialize(2048);
      }
      return generator.generateKeyPair();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Cannot generate signing key for " + algorithm.joseName(), e);
    }
  }
}
