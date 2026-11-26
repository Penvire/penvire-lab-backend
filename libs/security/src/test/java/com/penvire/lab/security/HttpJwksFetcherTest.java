package com.penvire.lab.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpJwksFetcherTest {

  private final HttpJwksFetcher fetcher = new HttpJwksFetcher();
  private HttpServer server;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/jwks",
        exchange -> {
          byte[] body = "{\"keys\":[]}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.createContext(
        "/error",
        exchange -> {
          exchange.sendResponseHeaders(500, -1);
          exchange.close();
        });
    server.createContext(
        "/redirect",
        exchange -> {
          exchange.getResponseHeaders().add("Location", "/jwks");
          exchange.sendResponseHeaders(302, -1);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void fetchesJwksBodyOnHttp200() {
    byte[] body = fetcher.fetch(uri("/jwks"), Duration.ofSeconds(2));

    assertEquals("{\"keys\":[]}", new String(body, StandardCharsets.UTF_8));
  }

  @Test
  void rejectsNon200ResponsesAndRedirects() {
    assertThrows(
        JwksUnavailableException.class, () -> fetcher.fetch(uri("/error"), Duration.ofSeconds(2)));
    assertThrows(
        JwksUnavailableException.class,
        () -> fetcher.fetch(uri("/redirect"), Duration.ofSeconds(2)));
  }

  @Test
  void throwsUnavailableWhenEndpointCannotBeReached() {
    URI unreachable = uri("/jwks");
    server.stop(0);

    assertThrows(
        JwksUnavailableException.class, () -> fetcher.fetch(unreachable, Duration.ofSeconds(2)));
  }

  private URI uri(String path) {
    return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
  }
}
