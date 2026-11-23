package com.penvire.lab.audit;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** The state a widget use case reports: leaves, a nested record, a collection and ignored data. */
record Widget(
    String name,
    int size,
    Colour colour,
    Dimensions dimensions,
    List<String> tags,
    @Nullable String note,
    @AuditIgnored String secretHash,
    Owner owner,
    Instant updatedAt) {

  static final Instant UPDATED_AT = Instant.parse("2030-01-01T12:00:00Z");

  static Widget sprocket() {
    return new Widget(
        "sprocket",
        3,
        Colour.BLUE,
        new Dimensions(10, 20),
        List.of("a"),
        null,
        "hash-1",
        new Owner("bob@example.test", new Profile("Bob")),
        UPDATED_AT);
  }

  Widget withName(String name) {
    return new Widget(name, size, colour, dimensions, tags, note, secretHash, owner, updatedAt);
  }

  Widget withSize(int size) {
    return new Widget(name, size, colour, dimensions, tags, note, secretHash, owner, updatedAt);
  }

  Widget withColour(Colour colour) {
    return new Widget(name, size, colour, dimensions, tags, note, secretHash, owner, updatedAt);
  }

  Widget withDimensions(Dimensions dimensions) {
    return new Widget(name, size, colour, dimensions, tags, note, secretHash, owner, updatedAt);
  }

  Widget withTags(List<String> tags) {
    return new Widget(name, size, colour, dimensions, tags, note, secretHash, owner, updatedAt);
  }

  Widget withNote(@Nullable String note) {
    return new Widget(name, size, colour, dimensions, tags, note, secretHash, owner, updatedAt);
  }

  Widget withSecretHash(String secretHash) {
    return new Widget(name, size, colour, dimensions, tags, note, secretHash, owner, updatedAt);
  }

  Widget withOwner(Owner owner) {
    return new Widget(name, size, colour, dimensions, tags, note, secretHash, owner, updatedAt);
  }

  Widget withUpdatedAt(Instant updatedAt) {
    return new Widget(name, size, colour, dimensions, tags, note, secretHash, owner, updatedAt);
  }
}
