package com.penvire.lab.audit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.penvire.lab.widget.events.v1.AuditActorType;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AuditActorTest {

  @Test
  void aUserHasItsSubAndTheClientItActedThrough() {
    AuditActor actor = AuditActor.user("bob", "widget-web");

    assertEquals(AuditActor.Type.USER, actor.type());
    assertEquals("bob", actor.id());
    assertEquals("widget-web", actor.clientId());
  }

  @Test
  void aUserMayActWithoutAClient() {
    AuditActor actor = AuditActor.user("bob", null);

    assertEquals("bob", actor.id());
    assertNull(actor.clientId());
  }

  @Test
  void aClientActsForItself() {
    AuditActor actor = AuditActor.client("widget-sync");

    assertEquals(AuditActor.Type.CLIENT, actor.type());
    assertEquals("widget-sync", actor.id());
    assertNull(actor.clientId());
  }

  @Test
  void theSystemHasNoIdOrClient() {
    AuditActor actor = AuditActor.system();

    assertEquals(AuditActor.Type.SYSTEM, actor.type());
    assertNull(actor.id());
    assertNull(actor.clientId());
  }

  @Test
  void anAnonymousCallerHasNoIdOrClient() {
    AuditActor actor = AuditActor.anonymous();

    assertEquals(AuditActor.Type.ANONYMOUS, actor.type());
    assertNull(actor.id());
    assertNull(actor.clientId());
  }

  @Test
  void rejectsAUserWithoutSub() {
    assertThrows(
        IllegalArgumentException.class, () -> new AuditActor(AuditActor.Type.USER, null, null));
  }

  @Test
  void rejectsAClientWithoutClientId() {
    assertThrows(
        IllegalArgumentException.class, () -> new AuditActor(AuditActor.Type.CLIENT, null, null));
  }

  @Test
  void rejectsAClientActingThroughAnotherClient() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuditActor(AuditActor.Type.CLIENT, "widget-sync", "widget-web"));
  }

  @Test
  void rejectsTheSystemWithAnId() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuditActor(AuditActor.Type.SYSTEM, "widget", null));
  }

  @Test
  void rejectsTheSystemWithAClient() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuditActor(AuditActor.Type.SYSTEM, null, "widget-sync"));
  }

  @Test
  void rejectsAnAnonymousCallerWithAnId() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuditActor(AuditActor.Type.ANONYMOUS, "bob", null));
  }

  @Test
  void rejectsAnAnonymousCallerWithAClient() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuditActor(AuditActor.Type.ANONYMOUS, null, "widget-web"));
  }

  @Test
  void acceptsTheCanonicalConstructorForAValidActor() {
    assertDoesNotThrow(() -> new AuditActor(AuditActor.Type.USER, "bob", "widget-web"));
  }

  @Test
  void hasTheSameTypesAsTheContract() {
    assertEquals(
        Arrays.stream(AuditActorType.values()).map(Enum::name).toList(),
        Arrays.stream(AuditActor.Type.values()).map(Enum::name).toList());
  }
}
