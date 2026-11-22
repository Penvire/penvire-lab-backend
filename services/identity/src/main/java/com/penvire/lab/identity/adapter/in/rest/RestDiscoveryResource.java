package com.penvire.lab.identity.adapter.in.rest;

import com.penvire.lab.identity.application.port.in.GetJwksSetUseCase;
import com.penvire.lab.identity.config.IdentityConfig;
import com.penvire.lab.identity.domain.signingkey.PublicSigningKey;
import com.penvire.lab.identity.server.api.DiscoveryApi;
import com.penvire.lab.identity.server.model.JwkSet;
import com.penvire.lab.identity.server.model.OpenIdConfiguration;
import java.util.List;

public class RestDiscoveryResource implements DiscoveryApi {
  private final IdentityConfig identityConfig;
  private final GetJwksSetUseCase getJwksSetUseCase;
  private final JwkMapper jwkMapper;

  public RestDiscoveryResource(
      IdentityConfig identityConfig, GetJwksSetUseCase getJwksSetUseCase, JwkMapper jwkMapper) {
    this.identityConfig = identityConfig;
    this.getJwksSetUseCase = getJwksSetUseCase;
    this.jwkMapper = jwkMapper;
  }

  @Override
  public JwkSet getJwks() {
    List<PublicSigningKey> keys = getJwksSetUseCase.getJwksSet();
    return new JwkSet(jwkMapper.toJwks(keys));
  }

  @Override
  public OpenIdConfiguration getOpenIdConfiguration() {
    var issuer = identityConfig.issuer();
    var config = new OpenIdConfiguration();

    config.setIssuer(issuer);
    config.setJwksUri(issuer.resolve("/.well-known/jwks.json"));

    // Temporary
    config.setAuthorizationEndpoint(issuer.resolve("/authorize"));
    config.tokenEndpoint(issuer.resolve("/token"));

    config.grantTypesSupported(List.of("authorization_code"));
    config.subjectTypesSupported(List.of("public"));
    config.idTokenSigningAlgValuesSupported(List.of("RS256"));
    config.scopesSupported(List.of("openid"));
    config.codeChallengeMethodsSupported(List.of("S256"));
    config.tokenEndpointAuthMethodsSupported(List.of("none"));
    config.responseTypesSupported(List.of("code"));

    return config;
  }
}
