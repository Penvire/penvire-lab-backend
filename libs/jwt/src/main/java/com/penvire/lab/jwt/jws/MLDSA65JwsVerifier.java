package com.penvire.lab.jwt.jws;

import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.NamedParameterSpec;

public final class MLDSA65JwsVerifier implements JwsVerifier {
  public static final String ALGORITHM = "ML-DSA-65";
  private static final int SIGNATURE_BYTES = 3309;

  @Override
  public boolean verifySignature(byte[] signingInput, byte[] signature, PublicKey trustedKey) {
    if (trustedKey == null
        || !(trustedKey.getParams() instanceof NamedParameterSpec params)
        || !ALGORITHM.equals(params.getName())) {
      throw new JwsVerificationException("ML-DSA-65 requires an ML-DSA-65 public key");
    }

    if (signature.length != SIGNATURE_BYTES) {
      return false;
    }

    try {
      Signature verifier = Signature.getInstance(ALGORITHM);
      verifier.initVerify(trustedKey);
      verifier.update(signingInput);
      return verifier.verify(signature);
    } catch (GeneralSecurityException exception) {
      throw new JwsVerificationException("Cannot verify ML-DSA-65 signature", exception);
    }
  }
}
