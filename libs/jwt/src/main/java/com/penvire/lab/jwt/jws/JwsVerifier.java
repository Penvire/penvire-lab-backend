package com.penvire.lab.jwt.jws;

import java.security.PublicKey;

/**
 * Verifies JWS Signatures using explicitly trusted keys.
 */
public interface JwsVerifier {
  /**
   * Verifies a compact JWS.
   * <p>This does not validate JWT claims or grant authorization.</p>
   *
   * @throws JwsVerificationException if the token cannot be verified
   */
  boolean verifySignature(
      byte[] signingInput,
      byte[] signature,
      PublicKey trustedKey);}
