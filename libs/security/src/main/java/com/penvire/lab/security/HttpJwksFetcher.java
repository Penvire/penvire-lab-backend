package com.penvire.lab.security;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@ApplicationScoped
public class HttpJwksFetcher implements JwksFetcher {

  private final HttpClient httpClient =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

  @Override
  public byte[] fetch(URI jwksUri, Duration timeout) {
    HttpRequest request =
        HttpRequest.newBuilder(jwksUri)
            .timeout(timeout)
            .header("ACCEPT", "application/json")
            .GET()
            .build();

    try {
      HttpResponse<byte[]> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

      if (response.statusCode() != 200) {
        throw new JwksUnavailableException("JWKS endpoint returned HTTP " + response.statusCode());
      }
      return response.body();
    } catch (IOException exception) {
      throw new JwksUnavailableException("Cannot reach JWKS endpoint", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new JwksUnavailableException("Interrupted while fetching JWKS", exception);
    }
  }
}
