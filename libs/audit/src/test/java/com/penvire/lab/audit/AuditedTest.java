package com.penvire.lab.audit;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penvire.lab.audit.RecordedAudits.Row;
import dev.penvire.lab.widget.events.v1.AuditActorType;
import dev.penvire.lab.widget.events.v1.AuditOutcome;
import dev.penvire.lab.widget.events.v1.AuditRecorded;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AuditedTest {

  @Inject WidgetService widgets;

  @Inject AuditScope audit;

  @Inject RecordedAudits recorded;

  @Test
  void recordsASuccessWithTheWidgetsTopicTypeAndTarget() {
    UUID id = UUID.randomUUID();

    widgets.create(id);

    Row row = single(id.toString());
    assertEquals("widget.audit", row.topic());
    assertEquals("dev.penvire.lab.widget.audit-recorded.v1", row.type());
    AuditRecorded event = row.event();
    assertEquals("widget.widget.create", event.getAction().toString());
    assertEquals(AuditOutcome.SUCCESS, event.getOutcome());
    assertEquals("widget", event.getTargetType().toString());
    assertEquals(id.toString(), String.valueOf(event.getTargetId()));
    assertNull(event.getReason());
    assertTrue(event.getChanges().isEmpty());
    assertTrue(recorded.widgetExists(id));
  }

  @Test
  void recordsASuccessInTheActionsTransaction() {
    UUID id = UUID.randomUUID();

    assertThrows(
        IllegalStateException.class,
        () ->
            QuarkusTransaction.requiringNew()
                .run(() -> {
                  widgets.create(id);
                  throw new IllegalStateException("the caller fails after the use case returned");
                }));

    assertFalse(recorded.widgetExists(id));
    assertTrue(recorded.withSubject(id.toString()).isEmpty());
  }

  @Test
  void recordsAFailureAfterTheChangeRolledBack() {
    UUID id = UUID.randomUUID();
    IllegalStateException failure = new IllegalStateException("database password is hunter2");

    IllegalStateException thrown =
        assertThrows(IllegalStateException.class, () -> widgets.createAndFail(id, failure));

    assertSame(failure, thrown);
    assertFalse(recorded.widgetExists(id));
    AuditRecorded event = single(id.toString()).event();
    assertEquals(AuditOutcome.FAILED, event.getOutcome());
    assertEquals("error", String.valueOf(event.getReason()));
    assertFalse(event.toString().contains("hunter2"));
  }

  @Test
  void recordsARefusalWithItsReasonButNotItsMessage() {
    UUID id = UUID.randomUUID();

    assertThrows(
        WidgetRefusedException.class,
        () -> widgets.createAndFail(id, new WidgetRefusedException("widget_limit_reached")));

    AuditRecorded event = single(id.toString()).event();
    assertEquals(AuditOutcome.DENIED, event.getOutcome());
    assertEquals("widget_limit_reached", String.valueOf(event.getReason()));
    assertFalse(event.toString().contains("s3cr3t"));
  }

  @Test
  void recordsAFailureWithItsReason() {
    UUID id = UUID.randomUUID();

    assertThrows(
        WidgetUnavailableException.class,
        () -> widgets.createAndFail(id, new WidgetUnavailableException()));

    AuditRecorded event = single(id.toString()).event();
    assertEquals(AuditOutcome.FAILED, event.getOutcome());
    assertEquals("widget_store_unavailable", String.valueOf(event.getReason()));
  }

  @Test
  void failsAnActionThatCannotRecordWithoutATransaction() {
    UUID id = UUID.randomUUID();

    assertThrows(RuntimeException.class, () -> widgets.readWithoutTransaction(id));
  }

  @Test
  void failsAndRollsBackAnActionOfAnotherService() {
    UUID id = UUID.randomUUID();

    assertThrows(RuntimeException.class, () -> widgets.createWithForeignAction(id));

    assertFalse(recorded.widgetExists(id));
  }

  @Test
  void failsAndRollsBackAnActionNotNamedServiceResourceVerb() {
    UUID id = UUID.randomUUID();

    assertThrows(RuntimeException.class, () -> widgets.createWithMalformedAction(id));

    assertFalse(recorded.widgetExists(id));
  }

  @Test
  void recordsTheServiceItselfOutsideAnHttpRequest() {
    UUID id = UUID.randomUUID();

    widgets.create(id);

    AuditRecorded event = single(id.toString()).event();
    assertEquals(AuditActorType.SYSTEM, event.getActorType());
    assertNull(event.getActorId());
    assertNull(event.getClientId());
  }

  @Test
  @TestSecurity(user = "alice")
  void recordsTheAuthenticatedCallerAsUser() {
    UUID id = UUID.randomUUID();

    widgets.create(id);

    AuditRecorded event = single(id.toString()).event();
    assertEquals(AuditActorType.USER, event.getActorType());
    assertEquals("alice", String.valueOf(event.getActorId()));
  }

  @Test
  void recordsAnUnauthenticatedHttpCallerAsAnonymous() {
    UUID id = UUID.randomUUID();

    given().post("/widgets/{id}", id).then().statusCode(204);

    AuditRecorded event = single(id.toString()).event();
    assertEquals(AuditActorType.ANONYMOUS, event.getActorType());
    assertNull(event.getActorId());
  }

  @Test
  @TestSecurity(user = "alice")
  void recordsTheActorTheUseCaseSets() {
    UUID asClient = UUID.randomUUID();
    UUID throughClient = UUID.randomUUID();

    widgets.createAs(asClient, AuditActor.client("widget-sync"));
    widgets.createAs(throughClient, AuditActor.user("bob", "widget-web"));

    AuditRecorded client = single(asClient.toString()).event();
    assertEquals(AuditActorType.CLIENT, client.getActorType());
    assertEquals("widget-sync", String.valueOf(client.getActorId()));
    assertNull(client.getClientId());
    AuditRecorded user = single(throughClient.toString()).event();
    assertEquals(AuditActorType.USER, user.getActorType());
    assertEquals("bob", String.valueOf(user.getActorId()));
    assertEquals("widget-web", String.valueOf(user.getClientId()));
  }

  @Test
  void recordsANestedActionWithItsOwnTarget() {
    UUID id = UUID.randomUUID();
    UUID partId = UUID.randomUUID();

    widgets.createWithPart(id, partId);

    AuditRecorded widget = single(id.toString()).event();
    assertEquals("widget.widget.create", widget.getAction().toString());
    AuditRecorded part = single(partId.toString()).event();
    assertEquals("widget.part.attach", part.getAction().toString());
    assertEquals("part", part.getTargetType().toString());
  }

  @Test
  @TestSecurity(user = "alice")
  void keysAnEventWithoutTargetByItsActor() {
    assertThrows(
        WidgetRefusedException.class,
        () -> widgets.refuseBeforeCreating(new WidgetRefusedException("widget_limit_reached")));

    List<Row> rows = recorded.withSubject("alice");
    assertFalse(rows.isEmpty());
    assertNull(rows.getLast().event().getTargetId());
  }

  @Test
  void keysAnEventWithoutTargetOrActorByItsOwnId() {
    given().post("/widgets/refused").then().statusCode(500);

    Row row = recorded.ofAction("widget.widget.create").getLast();
    assertEquals(AuditOutcome.DENIED, row.event().getOutcome());
    assertEquals(AuditActorType.ANONYMOUS, row.event().getActorType());
    assertEquals("invalid_client", String.valueOf(row.event().getReason()));
    assertEquals(row.id().toString(), row.subject());
  }

  @Test
  void recordsARefusalOfASubclassOfAListedException() {
    UUID id = UUID.randomUUID();

    assertThrows(
        WidgetLimitReachedException.class,
        () -> widgets.createAndFail(id, new WidgetLimitReachedException()));

    AuditRecorded event = single(id.toString()).event();
    assertEquals(AuditOutcome.DENIED, event.getOutcome());
    assertEquals("widget_limit_reached", String.valueOf(event.getReason()));
  }

  @Test
  void recordsNoErrorWhenTheActionListsNoExceptions() {
    UUID id = UUID.randomUUID();
    IllegalStateException failure = new IllegalStateException("left to logs and traces");

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class, () -> widgets.createAndFailWithDefaults(id, failure));

    assertSame(failure, thrown);
    assertFalse(recorded.widgetExists(id));
    assertTrue(recorded.withSubject(id.toString()).isEmpty());
  }

  @Test
  void recordsNoUnlistedRefusalWhenTheActionListsNoExceptions() {
    UUID id = UUID.randomUUID();

    assertThrows(
        WidgetRefusedException.class,
        () -> widgets.createAndFailWithDefaults(id, new WidgetRefusedException("widget_locked")));

    assertTrue(recorded.withSubject(id.toString()).isEmpty());
  }

  @Test
  @TestSecurity(user = "alice", roles = "member")
  void recordsACallerWithoutPermissionAsDeniedByDefault() {
    UUID id = UUID.randomUUID();

    assertThrows(ForbiddenException.class, () -> widgets.deleteAsAdminWithDefaults(id));

    AuditRecorded event = recorded.withSubject("alice").getLast().event();
    assertEquals("widget.widget.delete", event.getAction().toString());
    assertEquals(AuditOutcome.DENIED, event.getOutcome());
  }

  @Test
  void recordsAnUnauthenticatedCallerAsDeniedByDefault() {
    UUID id = UUID.randomUUID();

    assertThrows(UnauthorizedException.class, () -> widgets.deleteAsAdminWithDefaults(id));

    List<Row> rows = recorded.ofAction("widget.widget.delete");
    assertFalse(rows.isEmpty());
    assertEquals(AuditOutcome.DENIED, rows.getLast().event().getOutcome());
  }

  @Test
  void recordsAFailureListedInFailedOn() {
    UUID id = UUID.randomUUID();

    assertThrows(
        WidgetUnavailableException.class,
        () -> widgets.updateAndFail(id, new WidgetUnavailableException()));

    AuditRecorded event = single(id.toString()).event();
    assertEquals(AuditOutcome.FAILED, event.getOutcome());
    assertEquals("widget_store_unavailable", String.valueOf(event.getReason()));
  }

  @Test
  void recordsNothingForAFailureInNeitherList() {
    UUID id = UUID.randomUUID();
    IllegalStateException failure = new IllegalStateException("not worth an audit event");

    IllegalStateException thrown =
        assertThrows(IllegalStateException.class, () -> widgets.updateAndFail(id, failure));

    assertSame(failure, thrown);
    assertFalse(recorded.widgetExists(id));
    assertTrue(recorded.withSubject(id.toString()).isEmpty());
  }

  @Test
  void recordsOnlyRefusalsWhenFailedOnIsEmpty() {
    UUID failed = UUID.randomUUID();
    UUID refused = UUID.randomUUID();

    assertThrows(
        WidgetUnavailableException.class,
        () -> widgets.updateAndFailRecordingRefusalsOnly(failed, new WidgetUnavailableException()));
    assertThrows(
        WidgetRefusedException.class,
        () ->
            widgets.updateAndFailRecordingRefusalsOnly(
                refused, new WidgetRefusedException("widget_locked")));

    assertTrue(recorded.withSubject(failed.toString()).isEmpty());
    assertEquals(AuditOutcome.DENIED, single(refused.toString()).event().getOutcome());
  }

  @Test
  void prefersDeniedOnWhenBothListsMatch() {
    UUID refused = UUID.randomUUID();
    UUID failed = UUID.randomUUID();

    assertThrows(
        WidgetRefusedException.class,
        () ->
            widgets.updateAndFailWithOverlappingLists(
                refused, new WidgetRefusedException("widget_locked")));
    assertThrows(
        IllegalStateException.class,
        () -> widgets.updateAndFailWithOverlappingLists(failed, new IllegalStateException()));

    assertEquals(AuditOutcome.DENIED, single(refused.toString()).event().getOutcome());
    assertEquals(AuditOutcome.FAILED, single(failed.toString()).event().getOutcome());
  }

  @Test
  void rejectsAnExceptionListedAsBothDeniedAndFailed() {
    UUID id = UUID.randomUUID();

    assertThrows(IllegalStateException.class, () -> widgets.updateWithConflictingLists(id));

    assertFalse(recorded.widgetExists(id));
    assertTrue(recorded.withSubject(id.toString()).isEmpty());
  }

  @Test
  @TestSecurity(user = "alice", roles = "member")
  void recordsARefusalByQuarkusSecurityListedInDeniedOn() {
    UUID id = UUID.randomUUID();

    assertThrows(ForbiddenException.class, () -> widgets.deleteAsAdmin(id));

    List<Row> rows = recorded.ofAction("widget.widget.delete");
    assertFalse(
        rows.isEmpty(),
        "no audit event: Quarkus' @RolesAllowed check ran outside the audit interceptor");
    AuditRecorded event = rows.getLast().event();
    assertEquals(AuditOutcome.DENIED, event.getOutcome());
    assertEquals("error", String.valueOf(event.getReason()));
  }

  @Test
  void rejectsATargetOutsideAnAuditedMethod() {
    assertThrows(IllegalStateException.class, () -> audit.target("w-1"));
  }

  private Row single(String subject) {
    List<Row> rows = recorded.withSubject(subject);
    assertEquals(1, rows.size(), () -> "audit events with subject " + subject + ": " + rows);
    return rows.getFirst();
  }
}
