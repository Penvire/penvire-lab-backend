package com.penvire.lab.persistence;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import io.quarkus.arc.DefaultBean;
import jakarta.inject.Singleton;
import java.util.UUID;

/**
 * UUIDv7 (RFC 9562): a millisecond timestamp first, so new rows append to the primary-key index
 * instead of scattering across it like random v4 IDs. A service replaces it by defining its own
 * IdGenerator bean.
 */
@Singleton
@DefaultBean
final class Uuid7IdGenerator implements IdGenerator {
  private final NoArgGenerator generator = Generators.timeBasedEpochGenerator();

  @Override
  public UUID next() {
    return generator.generate();
  }
}
