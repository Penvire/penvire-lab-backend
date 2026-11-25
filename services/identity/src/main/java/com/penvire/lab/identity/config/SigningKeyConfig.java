package com.penvire.lab.identity.config;

import io.smallrye.config.ConfigMapping;
import java.util.Map;

/** The key-encryption keys (ADR 0007): 32 random bytes each, base64-encoded. */
@ConfigMapping(prefix = "penvire.identity.signing-key.kek")
public interface SigningKeyConfig {

  /** The KEK new keys are sealed with. */
  String current();

  /**
   * Every KEK by ID. An older KEK stays until every key sealed under it is re-sealed; remove it
   * then, or rows restored from a backup still verify (ADR 0009).
   */
  Map<String, String> keys();
}
