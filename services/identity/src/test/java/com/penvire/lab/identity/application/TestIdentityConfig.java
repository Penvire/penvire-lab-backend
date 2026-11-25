package com.penvire.lab.identity.application;

import static org.junit.jupiter.api.Assertions.fail;

import com.penvire.lab.identity.config.IdentityConfig;
import java.net.URI;

record TestIdentityConfig(URI issuer) implements IdentityConfig {
  TestIdentityConfig(String issuer) {
    this(URI.create(issuer));
  }

  @Override
  public String host() {
    return fail("Access-token issuance must not read the host");
  }
}
