package com.penvire.lab.identity.config;

import io.smallrye.config.ConfigMapping;
import java.net.URI;

@ConfigMapping(prefix = "penvire.identity")
public interface IdentityConfig {
  URI issuer();

  String host();
}
