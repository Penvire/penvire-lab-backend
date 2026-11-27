package com.penvire.lab.fga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.penvire.lab.fga.config.FgaConfig;
import com.penvire.lab.fga.openfga.OpenFgaClientAdapter;
import dev.openfga.sdk.api.model.ConsistencyPreference;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OpenFgaCdiTest {

  @Inject FgaClient fgaClient;
  @Inject CurrentFgaSubject currentCaller;
  @Inject FgaConfig config;

  @Test
  void wiresOpenFgaAdapterAndConfigDefaults() {
    assertNotNull(fgaClient);
    assertInstanceOf(OpenFgaClientAdapter.class, fgaClient);
    assertInstanceOf(SecurityIdentityCurrentFgaSubject.class, currentCaller);
    assertEquals(ConsistencyPreference.HIGHER_CONSISTENCY, config.consistency());
    assertEquals(Duration.ofSeconds(1), config.connectTimeout());
    assertEquals(Duration.ofSeconds(2), config.readTimeout());
    assertEquals(Duration.ofSeconds(4), config.requestTimeout());
  }
}
