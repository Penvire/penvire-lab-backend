package com.penvire.lab.jwt;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.penvire.lab.jwt.jws.JwsDecoder;
import com.penvire.lab.jwt.jws.JwsVerifier;
import com.penvire.lab.jwt.jws.Rs256JwsVerifier;
import java.security.PublicKey;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JwtValidator {
  private static final ObjectMapper OBJECT_MAPPER =
      JsonMapper.builder()
          .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .build();

  private final JwsDecoder decoder = new JwsDecoder();
  private final Map<String, JwsVerifier> signatureVerifiers;
  private final Clock clock;

  public JwtValidator(Map<String, JwsVerifier> signatureVerifiers, Clock clock) {
    this.signatureVerifiers = Map.copyOf(signatureVerifiers);

    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public VerifiedJwt validate(String compactJwt, PublicKey trustedKey, JwtValidationPolicy policy) {
    return new VerifiedJwt();
  }

}
