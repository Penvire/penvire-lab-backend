package com.penvire.lab.fga.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Provides access to the compiled shared OpenFGA authorization model bundled from {@code
 * dev.penvire.lab:fga-model} (ADR 0001, ADR 0010).
 */
public final class FgaModel {

  /** Classpath resource path of the compiled OpenFGA authorization model JSON. */
  public static final String RESOURCE_PATH = "fga/model.json";

  /** Classpath resource path of the bundled model metadata properties. */
  public static final String METADATA_RESOURCE_PATH = "fga/model.properties";

  private FgaModel() {}

  /** Returns the pinned {@code dev.penvire.lab:fga-model} contract version. */
  public static String version() {
    return Bundled.VERSION;
  }

  /** Returns the compiled OpenFGA authorization model JSON payload. */
  public static String json() {
    return Bundled.JSON;
  }

  private static final class Bundled {
    private static final String JSON = loadJson();
    private static final String VERSION = loadVersion();

    private static String loadJson() {
      try (InputStream stream = openRequiredResource(RESOURCE_PATH)) {
        String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        if (content.isEmpty()) {
          throw new IllegalStateException(
              "Bundled OpenFGA model resource is empty: " + RESOURCE_PATH);
        }
        return content;
      } catch (IOException e) {
        throw new UncheckedIOException(
            "Failed to read bundled OpenFGA model resource: " + RESOURCE_PATH, e);
      }
    }

    private static String loadVersion() {
      Properties properties = new Properties();
      try (InputStream stream = openRequiredResource(METADATA_RESOURCE_PATH)) {
        properties.load(stream);
      } catch (IOException e) {
        throw new UncheckedIOException(
            "Failed to read bundled OpenFGA model metadata: " + METADATA_RESOURCE_PATH, e);
      }
      String version = properties.getProperty("version");
      if (version == null || version.isBlank()) {
        throw new IllegalStateException(
            "Missing 'version' property in bundled OpenFGA model metadata: "
                + METADATA_RESOURCE_PATH);
      }
      return version.trim();
    }

    private static InputStream openRequiredResource(String path) {
      ClassLoader loader = FgaModel.class.getClassLoader();
      InputStream stream = loader != null ? loader.getResourceAsStream(path) : null;
      if (stream == null) {
        throw new IllegalStateException("Missing bundled OpenFGA model resource: " + path);
      }
      return stream;
    }
  }
}
