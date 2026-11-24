package com.penvire.lab.identity.config;

import io.smallrye.config.ConfigMapping;

import java.util.Map;

/** The key-encryption keys (ADR 0007): 32 random bytes each, base64-encoded. */
@ConfigMapping(prefix = "penvire.identity.signing-key.kek")
public interface SigningKeyConfig {

  /** The KEK new keys are sealed with. */
  String current();

  /** Every KEK by ID; older ones stay so their keys can still be read. */
  Map<String, String> keys();
}
