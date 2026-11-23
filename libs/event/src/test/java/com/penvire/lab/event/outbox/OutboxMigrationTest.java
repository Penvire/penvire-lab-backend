package com.penvire.lab.event.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.eclipse.microprofile.config.ConfigProvider;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** The library's migration next to a service's own; the test application sets neither default. */
@QuarkusTest
class OutboxMigrationTest {

  @Inject Flyway flyway;

  @Inject AgroalDataSource dataSource;

  @Test
  void flywayReadsServiceAndLibraryMigrations() {
    assertEquals(
        "db/migration,db/event",
        ConfigProvider.getConfig().getValue("quarkus.flyway.locations", String.class));
  }

  @Test
  void startupAppliesServiceAndLibraryMigrations() {
    assertEquals(List.of("0.1", "1"), appliedVersions(flyway));
  }

  @Test
  void libraryMigrationAddedAfterServiceMigrationsStillApplies() {
    // A schema of its own, so the scenario does not touch the tables created at startup.
    Flyway serviceBeforeUpgrade =
        Flyway.configure()
            .dataSource(dataSource)
            .schemas("library_upgrade")
            .locations("db/migration")
            .load();
    serviceBeforeUpgrade.migrate();

    // The library's default, as ConfigProvider shows it to the service's Flyway.
    boolean outOfOrder =
        ConfigProvider.getConfig().getValue("quarkus.flyway.out-of-order", Boolean.class);
    Flyway serviceAfterUpgrade =
        Flyway.configure()
            .dataSource(dataSource)
            .schemas("library_upgrade")
            .locations("db/migration", "db/event")
            .outOfOrder(outOfOrder)
            .load();
    serviceAfterUpgrade.migrate();

    MigrationInfo libraryMigration =
        Arrays.stream(serviceAfterUpgrade.info().applied())
            .filter(migration -> "0.1".equals(versionOf(migration)))
            .findFirst()
            .orElseThrow();
    assertEquals(MigrationState.OUT_OF_ORDER, libraryMigration.getState());
    assertEquals(List.of("0.1", "1"), appliedVersions(serviceAfterUpgrade));
  }

  /** Applied versioned migrations, without Flyway's version-less schema-creation marker. */
  private static List<String> appliedVersions(Flyway flyway) {
    return Arrays.stream(flyway.info().applied())
        .map(OutboxMigrationTest::versionOf)
        .filter(Objects::nonNull)
        .sorted()
        .toList();
  }

  private static @Nullable String versionOf(MigrationInfo migration) {
    MigrationVersion version = migration.getVersion();
    return version == null ? null : version.getVersion();
  }
}
