package com.penvire.lab.fga.config;

import dev.openfga.sdk.api.model.ConsistencyPreference;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * Configuration for the OpenFGA client ({@code penvire.fga.*}).
 *
 * <p>Checks and reads default to {@link ConsistencyPreference#HIGHER_CONSISTENCY} so revocations
 * and newly written tuples take effect immediately without stale cache reads (ADR 0010). {@link
 * #requestTimeout()} caps the total blocking wait across SDK retries.
 */
@ConfigMapping(prefix = "penvire.fga")
public interface FgaConfig {

  /** Base HTTP URL of the OpenFGA server (for example {@code http://127.0.0.1:8090}). */
  URI apiUrl();

  /** OpenFGA store identifier. */
  String storeId();

  /** Optional pinned authorization model ID; when absent, OpenFGA uses the store's latest model. */
  Optional<String> authorizationModelId();

  /** Optional pre-shared bearer token sent in the {@code Authorization} header. */
  Optional<String> apiToken();

  /** TCP connection timeout per HTTP attempt to OpenFGA. */
  @WithDefault("PT1S")
  Duration connectTimeout();

  /** HTTP read timeout per attempt to OpenFGA. */
  @WithDefault("PT2S")
  Duration readTimeout();

  /** Overall deadline per {@code FgaClient} call across SDK retries before failing closed. */
  @WithDefault("PT4S")
  Duration requestTimeout();

  /** Maximum number of automatic SDK retries on retryable OpenFGA errors. */
  @WithDefault("1")
  int maxRetries();

  /** Consistency preference applied to checks, reads, and list queries. */
  @WithDefault("HIGHER_CONSISTENCY")
  ConsistencyPreference consistency();
}
