package com.penvire.lab.identity.application.port.out;

import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import java.security.KeyPair;

public interface SigningKeyGeneratorPort {
  KeyPair generate(SigningKeyAlgorithm algorithm);
}
