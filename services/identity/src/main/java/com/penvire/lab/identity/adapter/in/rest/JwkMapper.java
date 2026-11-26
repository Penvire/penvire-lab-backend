package com.penvire.lab.identity.adapter.in.rest;

import com.penvire.lab.identity.domain.signingkey.SigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import com.penvire.lab.identity.server.model.Jwk;
import com.penvire.lab.identity.server.model.JwkSet;
import com.penvire.lab.jwt.jwk.PublicJwkCodec;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

/**
 * Maps signing keys to public JWKs and back using {@link PublicJwkCodec}. Private material is never
 * part of a JWK.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.JAKARTA_CDI)
public abstract class JwkMapper {

  /** A key's {@code kid} and {@code alg} come from the stored key, never from the key bytes. */
  @Mapping(target = "kid", expression = "java(key.kid())")
  @Mapping(target = "alg", source = "algorithm")
  @Mapping(target = "use", constant = "sig")
  @Mapping(target = "kty", ignore = true)
  @Mapping(target = "pub", ignore = true)
  @Mapping(target = "n", ignore = true)
  @Mapping(target = "e", ignore = true)
  public abstract Jwk toJwk(SigningKey key);

  public abstract List<Jwk> toJwks(List<SigningKey> keys);

  // MapStruct cannot map a list straight onto a bean, so the wrapper is built by hand.
  public JwkSet toJwkSet(List<SigningKey> keys) {
    return new JwkSet(toJwks(keys));
  }

  /** The JOSE name, not the enum constant: {@code ML-DSA-65}, not {@code ML_DSA_65}. */
  protected String toAlg(SigningKeyAlgorithm algorithm) {
    return algorithm.joseName();
  }

  @AfterMapping
  protected void addKeyMaterial(SigningKey key, @MappingTarget Jwk jwk) {
    switch (key.algorithm()) {
      case ML_DSA_65 -> {
        jwk.setKty(PublicJwkCodec.KTY_AKP);
        jwk.setPub(PublicJwkCodec.encodeMlDsa65(key.publicKey()));
      }
      case RS256 -> {
        var rsa = PublicJwkCodec.encodeRsa((RSAPublicKey) key.publicKey());
        jwk.setKty(PublicJwkCodec.KTY_RSA);
        jwk.setN(rsa.modulus());
        jwk.setE(rsa.exponent());
      }
    }
  }

  /**
   * Reads a public JWK. Only the two key types Identity uses are accepted, each with its one
   * algorithm; anything else is rejected rather than guessed. Parsing a key does not make it
   * trusted.
   *
   * @throws IllegalArgumentException if the JWK is malformed or its metadata does not match
   */
  public PublicKey toPublicKey(Jwk jwk) {
    return switch (jwk.getKty()) {
      case PublicJwkCodec.KTY_AKP -> {
        requireAlg(jwk, SigningKeyAlgorithm.ML_DSA_65);
        yield PublicJwkCodec.decodeMlDsa65(jwk.getPub());
      }
      case PublicJwkCodec.KTY_RSA -> {
        requireAlg(jwk, SigningKeyAlgorithm.RS256);
        yield PublicJwkCodec.decodeRsa(jwk.getN(), jwk.getE());
      }
      default -> throw new IllegalArgumentException("Unsupported JWK kty: " + jwk.getKty());
    };
  }

  private static void requireAlg(Jwk jwk, SigningKeyAlgorithm expected) {
    if (!expected.joseName().equals(jwk.getAlg())) {
      throw new IllegalArgumentException(
          "JWK kty "
              + jwk.getKty()
              + " requires alg "
              + expected.joseName()
              + ", was "
              + jwk.getAlg());
    }
  }
}
