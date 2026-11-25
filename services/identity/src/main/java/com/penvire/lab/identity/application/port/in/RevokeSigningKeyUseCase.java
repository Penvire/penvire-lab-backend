package com.penvire.lab.identity.application.port.in;

import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;

public interface RevokeSigningKeyUseCase {
  void revoke(SigningKeyAlgorithm algorithm);
}
