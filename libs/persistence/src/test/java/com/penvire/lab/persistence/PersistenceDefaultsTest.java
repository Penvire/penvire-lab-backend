package com.penvire.lab.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

/** The test application.properties sets neither value, so both come from the library. */
@QuarkusTest
class PersistenceDefaultsTest {

  private final Config config = ConfigProvider.getConfig();

  @Test
  void hibernateOnlyValidatesTheSchema() {
    assertEquals(
        "validate",
        config.getValue("quarkus.hibernate-orm.schema-management.strategy", String.class));
  }

  @Test
  void flywayMigratesAtStartup() {
    assertEquals(true, config.getValue("quarkus.flyway.migrate-at-start", Boolean.class));
  }
}
