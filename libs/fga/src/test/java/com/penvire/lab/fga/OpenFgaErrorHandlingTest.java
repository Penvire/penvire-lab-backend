package com.penvire.lab.fga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penvire.lab.fga.config.FgaConfig;
import com.penvire.lab.fga.model.FgaObject;
import com.penvire.lab.fga.model.FgaRelation;
import com.penvire.lab.fga.model.FgaSubject;
import com.penvire.lab.fga.model.FgaType;
import com.penvire.lab.fga.openfga.OpenFgaClientAdapter;
import com.penvire.lab.fga.openfga.OpenFgaSdkProducer;
import com.sun.net.httpserver.HttpServer;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.model.ConsistencyPreference;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenFgaErrorHandlingTest {

  private static final String STORE_ID = "01H00000000000000000000000";
  private static final FgaType TENANT = FgaType.of("tenant");
  private static final FgaRelation ADMIN = FgaRelation.of("admin");

  private HttpServer server;
  private URI baseUri;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.start();
    baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void translatesServerAndRateLimitErrorsToUnavailable() {
    AtomicInteger status = new AtomicInteger(503);
    server.createContext(
        "/",
        exchange -> {
          byte[] body =
              "{\"code\":\"internal_error\",\"message\":\"down\"}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status.get(), body.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
          }
        });

    OpenFgaClientAdapter adapter =
        adapter(baseUri, Optional.empty(), Duration.ofSeconds(2), Duration.ofSeconds(3));

    assertThrows(
        FgaUnavailableException.class,
        () -> adapter.check(FgaSubject.user("alice"), ADMIN, FgaObject.of(TENANT, "t1")));

    status.set(500);
    assertThrows(
        FgaUnavailableException.class,
        () -> adapter.check(FgaSubject.user("alice"), ADMIN, FgaObject.of(TENANT, "t1")));

    status.set(429);
    assertThrows(
        FgaUnavailableException.class,
        () -> adapter.check(FgaSubject.user("alice"), ADMIN, FgaObject.of(TENANT, "t1")));
  }

  @Test
  void translatesClientValidationErrorToFgaExceptionNotUnavailable() {
    server.createContext(
        "/",
        exchange -> {
          byte[] body =
              "{\"code\":\"validation_error\",\"message\":\"unknown relation\"}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(400, body.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
          }
        });

    OpenFgaClientAdapter adapter =
        adapter(baseUri, Optional.empty(), Duration.ofSeconds(2), Duration.ofSeconds(3));

    FgaException thrown =
        assertThrows(
            FgaException.class,
            () -> adapter.check(FgaSubject.user("alice"), ADMIN, FgaObject.of(TENANT, "t1")));
    assertFalse(thrown instanceof FgaUnavailableException);
  }

  @Test
  void enforcesOverallRequestDeadlineAndUnreachableUnavailable() throws IOException {
    server.createContext(
        "/",
        exchange -> {
          try {
            Thread.sleep(800);
          } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
          }
          exchange.close();
        });

    // Overall requestTimeout (150ms) cuts off a slow call even when readTimeout is longer (2s).
    OpenFgaClientAdapter deadlineAdapter =
        adapter(baseUri, Optional.empty(), Duration.ofSeconds(2), Duration.ofMillis(150));
    long startedNanos = System.nanoTime();
    assertThrows(
        FgaUnavailableException.class,
        () -> deadlineAdapter.check(FgaSubject.user("alice"), ADMIN, FgaObject.of(TENANT, "t1")));
    long elapsedMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    assertTrue(elapsedMs < 600, "Expected deadline cutoff under 600ms, took " + elapsedMs + "ms");

    int unusedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      unusedPort = socket.getLocalPort();
    }
    OpenFgaClientAdapter unreachableAdapter =
        adapter(
            URI.create("http://127.0.0.1:" + unusedPort),
            Optional.empty(),
            Duration.ofSeconds(1),
            Duration.ofSeconds(2));
    assertThrows(
        FgaUnavailableException.class,
        () ->
            unreachableAdapter.check(FgaSubject.user("alice"), ADMIN, FgaObject.of(TENANT, "t1")));
  }

  @Test
  void sendsBearerTokenAndSkipsEmptyWrites() {
    AtomicInteger requestCount = new AtomicInteger();
    AtomicReference<String> authHeader = new AtomicReference<>();
    server.createContext(
        "/",
        exchange -> {
          requestCount.incrementAndGet();
          authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
          byte[] body = "{\"allowed\":true}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
          }
        });

    OpenFgaClientAdapter adapter =
        adapter(
            baseUri, Optional.of("secret-fga-token"), Duration.ofSeconds(2), Duration.ofSeconds(3));

    adapter.write(List.of());
    adapter.delete(List.of());
    adapter.writeAndDelete(List.of(), List.of());
    assertEquals(0, requestCount.get());

    assertTrue(adapter.check(FgaSubject.user("alice"), ADMIN, FgaObject.of(TENANT, "t1")));
    assertEquals("Bearer secret-fga-token", authHeader.get());
    assertEquals(1, requestCount.get());
  }

  private static OpenFgaClientAdapter adapter(
      URI uri, Optional<String> apiToken, Duration readTimeout, Duration requestTimeout) {
    FgaConfig config =
        new FgaConfig() {
          @Override
          public URI apiUrl() {
            return uri;
          }

          @Override
          public String storeId() {
            return STORE_ID;
          }

          @Override
          public Optional<String> authorizationModelId() {
            return Optional.empty();
          }

          @Override
          public Optional<String> apiToken() {
            return apiToken;
          }

          @Override
          public Duration connectTimeout() {
            return Duration.ofSeconds(1);
          }

          @Override
          public Duration readTimeout() {
            return readTimeout;
          }

          @Override
          public Duration requestTimeout() {
            return requestTimeout;
          }

          @Override
          public int maxRetries() {
            return 0;
          }

          @Override
          public ConsistencyPreference consistency() {
            return ConsistencyPreference.HIGHER_CONSISTENCY;
          }
        };

    OpenFgaClient sdkClient = OpenFgaSdkProducer.createClient(config);
    return new OpenFgaClientAdapter(sdkClient, config);
  }
}
