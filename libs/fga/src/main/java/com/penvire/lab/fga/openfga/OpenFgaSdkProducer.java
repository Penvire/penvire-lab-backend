package com.penvire.lab.fga.openfga;

import com.penvire.lab.fga.FgaException;
import com.penvire.lab.fga.config.FgaConfig;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.configuration.ApiToken;
import dev.openfga.sdk.api.configuration.ClientConfiguration;
import dev.openfga.sdk.api.configuration.Credentials;
import dev.openfga.sdk.errors.FgaInvalidParameterException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * CDI producer that builds the underlying {@link OpenFgaClient} from {@link FgaConfig}. Keeps all
 * OpenFGA SDK configuration isolated inside {@code com.penvire.lab.fga.openfga}.
 */
@ApplicationScoped
public class OpenFgaSdkProducer {

  /** Produces the application-scoped OpenFGA SDK client bean. */
  @Produces
  @ApplicationScoped
  OpenFgaClient openFgaClient(FgaConfig config) {
    return createClient(config);
  }

  /**
   * Constructs an {@link OpenFgaClient} from {@code config}, applying timeouts, retry settings,
   * optional pinned model ID, and optional bearer token.
   *
   * @throws FgaException if {@code config} fails SDK parameter validation
   */
  public static OpenFgaClient createClient(FgaConfig config) {
    ClientConfiguration clientConfig =
        new ClientConfiguration()
            .apiUrl(config.apiUrl().toString())
            .storeId(config.storeId())
            .connectTimeout(config.connectTimeout())
            .readTimeout(config.readTimeout())
            .maxRetries(config.maxRetries());

    config
        .authorizationModelId()
        .filter(s -> !s.isBlank())
        .ifPresent(clientConfig::authorizationModelId);

    config
        .apiToken()
        .filter(s -> !s.isBlank())
        .ifPresent(token -> clientConfig.credentials(new Credentials(new ApiToken(token))));

    try {
      return new OpenFgaClient(clientConfig);
    } catch (FgaInvalidParameterException e) {
      throw new FgaException("Invalid OpenFGA client configuration", e);
    }
  }
}
