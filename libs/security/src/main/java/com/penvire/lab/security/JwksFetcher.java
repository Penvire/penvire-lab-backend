package com.penvire.lab.security;

import java.net.URI;
import java.time.Duration;

public interface JwksFetcher {
  byte[] fetch(URI jwksUri, Duration timeout);
}
