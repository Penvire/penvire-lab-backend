package com.penvire.lab.event;

import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.avro.Schema;
import org.apache.avro.SchemaNormalization;
import org.apache.avro.specific.SpecificRecordBase;
import org.jspecify.annotations.Nullable;

/**
 * Finds the schema revisions a contract package ships as {@code avro/<full name>/<n>.avsc}.
 * Revisions are numbered from 1 without gaps, and generated classes match the latest one.
 */
@Singleton
public class SchemaCatalog {

  private final Map<Class<?>, SchemaRevision> latestByClass = new ConcurrentHashMap<>();

  /**
   * The revision a producer writes: the latest one in the package that holds the record's class.
   *
   * @throws IllegalStateException if the package ships no revision, or its latest revision does not
   *     match the generated class
   */
  public SchemaRevision latestOf(SpecificRecordBase record) {
    return latestByClass.computeIfAbsent(
        record.getClass(), type -> findLatest(type, record.getSchema()));
  }

  private static SchemaRevision findLatest(Class<?> type, Schema generated) {
    String fullName = generated.getFullName();
    // The .avsc files sit in the same jar as the generated class.
    ClassLoader loader = type.getClassLoader();
    SchemaRevision latest = null;
    for (int revision = 1; ; revision++) {
      Schema schema = read(loader, "avro/" + fullName + "/" + revision + ".avsc");
      if (schema == null) {
        break;
      }
      latest = new SchemaRevision(schema, revision);
    }

    if (latest == null) {
      throw new IllegalStateException("No schema revisions on the classpath for " + fullName);
    }

    // Compares the parsing canonical form: SCHEMA$ carries avro.java.string properties the
    // .avsc files lack.
    if (SchemaNormalization.parsingFingerprint64(latest.schema())
        != SchemaNormalization.parsingFingerprint64(generated)) {
      throw new IllegalStateException(
          fullName
              + " does not match its latest revision "
              + latest.revision()
              + "; is the class from a different package version than its schemas?");
    }

    return latest;
  }

  private static @Nullable Schema read(ClassLoader loader, String resource) {
    try (InputStream in = loader.getResourceAsStream(resource)) {
      // A fresh parser per file: revisions redefine the same named types.
      return in == null ? null : new Schema.Parser().parse(in);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read " + resource, e);
    }
  }
}
