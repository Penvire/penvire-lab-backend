package com.penvire.lab.jwt.jws;

import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;

public class Rs256JwsVerifier implements JwsVerifier {

  @Override
  public boolean verifySignature(byte[] signingInput, byte[] signature, PublicKey trustedKey) {
    if (!(trustedKey instanceof RSAPublicKey rsaKey)) {
      throw new JwsVerificationException("RS256 requires an RSA public key");
    }

    int modulusBits = rsaKey.getModulus().bitLength();
    if (modulusBits < 2048) {
      throw new JwsVerificationException("RSA keys must have at least 2048 bits");
    }

    int expectedSignatureBytes = (modulusBits + 7) / 8;
    if (signature.length != expectedSignatureBytes) {
      return false;
    }

    try {
      Signature verifier = Signature.getInstance("SHA256withRSA");
      verifier.initVerify(rsaKey);
      verifier.update(signingInput);

      return verifier.verify(signature);
    } catch (GeneralSecurityException exception) {
      throw new JwsVerificationException("Cannot verify RS256 signature", exception);
    }
  }
}
