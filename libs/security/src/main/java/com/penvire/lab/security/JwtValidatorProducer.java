package com.penvire.lab.security;

import com.penvire.lab.jwt.jws.MLDSA65JwsVerifier;
import com.penvire.lab.jwt.jws.Rs256JwsVerifier;
import com.penvire.lab.jwt.validation.JwtValidator;
import com.penvire.lab.jwt.validation.JwtVerificationKeyResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;
import java.util.Map;

@ApplicationScoped
public class JwtValidatorProducer {

  @Produces
  @ApplicationScoped
  JwtValidator jwtValidator(Clock clock, JwtVerificationKeyResolver resolver) {
    return new JwtValidator(
        Map.of("RS256", new Rs256JwsVerifier(), "ML-DSA-65", new MLDSA65JwsVerifier()),
        clock,
        resolver);
  }
}
