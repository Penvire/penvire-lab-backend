package com.penvire.lab.identity.adapter.out.crypto.jwt;

import com.penvire.lab.identity.domain.signingkey.ActiveSigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;

@ApplicationScoped
public class Signer {
  public byte[] sign(String message, ActiveSigningKey key) {
    SigningKeyAlgorithm alg = key.key().algorithm();
    if (!alg.keyFactoryAlgorithm().equals(key.privateKey().getAlgorithm())) {
      throw new IllegalStateException("The private key does not match the signing key's algorithm");
    }

    try {
      var signature = Signature.getInstance(alg.signatureAlgorithm());
      signature.initSign(key.privateKey());
      signature.update(message.getBytes(StandardCharsets.US_ASCII));
      return signature.sign();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to sign", e);
    }
  }
}
