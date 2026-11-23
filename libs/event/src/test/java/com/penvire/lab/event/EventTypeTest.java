package com.penvire.lab.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class EventTypeTest {

  @Test
  void derivesServiceEventAndMajorFromTheFullName() {
    assertEquals(
        new EventType("widget", "widget-created", 1),
        EventType.of(TestEvents.WidgetCreated.SCHEMA));
  }

  @Test
  void typeNamesTheServiceTheEventAndTheMajor() {
    assertEquals(
        "dev.penvire.lab.identity.session-started.v2",
        new EventType("identity", "session-started", 2).type());
  }

  @Test
  void sourceNamesTheService() {
    assertEquals("/penvire-lab/identity", new EventType("identity", "session-started", 1).source());
  }

  /** The cases of rules.test.js in the contracts repository, plus a digit before a capital. */
  @ParameterizedTest
  @CsvSource({
    "SessionStarted, session-started",
    "SigningKeyActivated, signing-key-activated",
    "UserMFAEnabled, user-mfa-enabled",
    "JWKSRotated, jwks-rotated",
    "Key2Rotated, key2-rotated",
  })
  void kebabFollowsTheContractsRule(String recordName, String event) {
    assertEquals(event, EventType.kebab(recordName));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "com.example.events.v1",
        "dev.penvire.lab.widget.v1",
        "dev.penvire.lab.widget.events.v0",
        "dev.penvire.lab.Widget.events.v1",
      })
  void rejectsSchemasOutsideAPenvireLabEventsNamespace(String namespace) {
    Schema schema = SchemaBuilder.record("WidgetCreated").namespace(namespace).fields().endRecord();

    assertThrows(IllegalArgumentException.class, () -> EventType.of(schema));
  }
}
