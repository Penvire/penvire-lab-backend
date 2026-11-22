package com.penvire.lab.identity.domain.signingkey;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

@Getter
@Builder
public class PublicSigningKey {
  private final String kty;
  private final String kid;
  private final String alg;
  private final String pub;

  @Builder.Default private final String use = "sig";

  @Nullable private String n;
  @Nullable private String e;
}
