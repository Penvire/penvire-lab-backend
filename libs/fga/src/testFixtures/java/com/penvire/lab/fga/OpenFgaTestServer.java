package com.penvire.lab.fga;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penvire.lab.fga.config.FgaConfig;
import com.penvire.lab.fga.model.FgaModel;
import com.penvire.lab.fga.openfga.OpenFgaClientAdapter;
import com.penvire.lab.fga.openfga.OpenFgaSdkProducer;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.model.ConsistencyPreference;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Testcontainers-backed OpenFGA server provisioned with the bundled {@link FgaModel} (or a custom
 * authorization model JSON) for real integration and contract tests.
 */
public final class OpenFgaTestServer implements AutoCloseable {

  /** Pinned OpenFGA container image matching the lab runtime. */
  public static final String DEFAULT_IMAGE = "openfga/openfga:v1.23.0";

  private static final int HTTP_PORT = 8080;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final GenericContainer<?> container;
  private final URI apiUrl;
  private final HttpClient httpClient;
  private final ProvisionedStore defaultStore;

  private OpenFgaTestServer(
      GenericContainer<?> container,
      URI apiUrl,
      HttpClient httpClient,
      ProvisionedStore defaultStore) {
    this.container = container;
    this.apiUrl = apiUrl;
    this.httpClient = httpClient;
    this.defaultStore = defaultStore;
  }

  /** Starts an OpenFGA container and provisions a store with the bundled {@link FgaModel}. */
  public static OpenFgaTestServer start() {
    return start(FgaModel.json());
  }

  /** Starts an OpenFGA container and provisions a store with {@code modelJson}. */
  public static OpenFgaTestServer start(String modelJson) {
    validateModelJson(modelJson);
    GenericContainer<?> container =
        new GenericContainer<>(DEFAULT_IMAGE)
            .withCommand("run")
            .withExposedPorts(HTTP_PORT)
            .waitingFor(Wait.forHttp("/healthz").forPort(HTTP_PORT));
    container.start();
    try {
      URI apiUrl =
          URI.create("http://" + container.getHost() + ":" + container.getMappedPort(HTTP_PORT));
      HttpClient httpClient = HttpClient.newHttpClient();
      ProvisionedStore store = provisionStore(httpClient, apiUrl, "penvire-test", modelJson);
      return new OpenFgaTestServer(container, apiUrl, httpClient, store);
    } catch (RuntimeException e) {
      container.stop();
      throw e;
    }
  }

  /** Base HTTP URL of the running OpenFGA container. */
  public URI apiUrl() {
    return apiUrl;
  }

  /** Store identifier of the initial provisioned store. */
  public String storeId() {
    return defaultStore.storeId();
  }

  /** Authorization model identifier uploaded to the initial provisioned store. */
  public String authorizationModelId() {
    return defaultStore.authorizationModelId();
  }

  /** {@link FgaConfig} targeting the initial provisioned store and model. */
  public FgaConfig config() {
    return defaultStore.config();
  }

  /** Synchronous {@link FgaClient} targeting the initial provisioned store and model. */
  public FgaClient client() {
    return defaultStore.client();
  }

  /** Creates a fresh isolated store provisioned with the bundled {@link FgaModel}. */
  public ProvisionedStore createStore() {
    return createStore(FgaModel.json());
  }

  /** Creates a fresh isolated store provisioned with {@code modelJson}. */
  public ProvisionedStore createStore(String modelJson) {
    validateModelJson(modelJson);
    return provisionStore(httpClient, apiUrl, "penvire-test", modelJson);
  }

  @Override
  public void close() {
    container.stop();
  }

  /** An isolated OpenFGA store provisioned with an authorization model and ready-to-use client. */
  public record ProvisionedStore(
      String storeId, String authorizationModelId, FgaConfig config, FgaClient client) {}

  private static ProvisionedStore provisionStore(
      HttpClient http, URI apiUrl, String storeName, String modelJson) {
    String storeId =
        postJsonAndReadField(
            http, apiUrl.resolve("/stores"), "{\"name\":\"" + storeName + "\"}", "id");
    String modelId =
        postJsonAndReadField(
            http,
            apiUrl.resolve("/stores/" + storeId + "/authorization-models"),
            modelJson,
            "authorization_model_id");

    FgaConfig config =
        new TestFgaConfig(
            apiUrl,
            storeId,
            Optional.of(modelId),
            Optional.empty(),
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
            Duration.ofSeconds(4),
            1,
            ConsistencyPreference.HIGHER_CONSISTENCY);
    OpenFgaClient sdkClient = OpenFgaSdkProducer.createClient(config);
    FgaClient client = new OpenFgaClientAdapter(sdkClient, config);
    return new ProvisionedStore(storeId, modelId, config, client);
  }

  private static String postJsonAndReadField(
      HttpClient http, URI uri, String body, String fieldName) {
    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    try {
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 201) {
        throw new IllegalStateException(
            "OpenFGA POST "
                + uri.getPath()
                + " failed with status "
                + response.statusCode()
                + ": "
                + response.body());
      }
      JsonNode json = OBJECT_MAPPER.readTree(response.body());
      JsonNode field = json.get(fieldName);
      if (field == null || field.asText().isBlank()) {
        throw new IllegalStateException(
            "OpenFGA response missing '" + fieldName + "': " + response.body());
      }
      return field.asText();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while calling OpenFGA " + uri.getPath(), e);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to call OpenFGA " + uri.getPath(), e);
    }
  }

  private static void validateModelJson(String modelJson) {
    Objects.requireNonNull(modelJson, "modelJson");
    if (modelJson.isBlank()) {
      throw new IllegalArgumentException("modelJson must not be blank");
    }
  }

  private record TestFgaConfig(
      URI apiUrl,
      String storeId,
      Optional<String> authorizationModelId,
      Optional<String> apiToken,
      Duration connectTimeout,
      Duration readTimeout,
      Duration requestTimeout,
      int maxRetries,
      ConsistencyPreference consistency)
      implements FgaConfig {}
}
