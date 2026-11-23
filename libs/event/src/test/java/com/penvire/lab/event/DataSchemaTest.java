package com.penvire.lab.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DataSchemaTest {

  private static final String URN =
      "urn:penvire-lab:schema:dev.penvire.lab.identity.events.v1.SessionStarted:12";

  @Test
  void parsesTheFullNameAndTheRevision() {
    assertEquals(
        new DataSchema("dev.penvire.lab.identity.events.v1.SessionStarted", 12),
        DataSchema.parse(URN));
  }

  @Test
  void writesTheUrnItParses() {
    assertEquals(URN, DataSchema.parse(URN).urn());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "dev.penvire.lab.identity.events.v1.SessionStarted:1",
        "urn:other:schema:dev.penvire.lab.identity.events.v1.SessionStarted:1",
        "urn:penvire-lab:schema:dev.penvire.lab.identity.events.v1.SessionStarted",
        "urn:penvire-lab:schema:dev.penvire.lab.identity.events.v1.SessionStarted:0",
        "urn:penvire-lab:schema:dev.penvire.lab.identity.events.v1.SessionStarted:01",
        "urn:penvire-lab:schema:dev..SessionStarted:1",
        "urn:penvire-lab:schema:dev.penvire.lab.identity.events.v1.SessionStarted:1 ",
      })
  void rejectsAnythingButAPenvireLabSchemaUrn(String urn) {
    assertThrows(IllegalArgumentException.class, () -> DataSchema.parse(urn));
  }
}
