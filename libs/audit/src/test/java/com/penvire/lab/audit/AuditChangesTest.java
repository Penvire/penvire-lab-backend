package com.penvire.lab.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penvire.lab.audit.RecordedAudits.Row;
import dev.penvire.lab.widget.events.v1.AuditChange;
import dev.penvire.lab.widget.events.v1.AuditOutcome;
import dev.penvire.lab.widget.events.v1.AuditRecorded;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** The {@code changes} of an audit event, computed from the state a use case reports (ADR 0006). */
@QuarkusTest
class AuditChangesTest {

  /** One recorded change; values are JSON text, and Java null means not recorded. */
  record Change(String path, @Nullable String oldValue, @Nullable String newValue) {

    static Change of(AuditChange change) {
      return new Change(
          change.getFieldPath().toString(), text(change.getOldValue()), text(change.getNewValue()));
    }

    private static @Nullable String text(@Nullable CharSequence value) {
      return value == null ? null : value.toString();
    }
  }

  @Inject WidgetStateService widgets;

  @Inject AuditScope audit;

  @Inject RecordedAudits recorded;

  @Test
  void recordsEveryChangedFieldWithItsValuesSortedByPath() {
    UUID id = UUID.randomUUID();
    Widget before = Widget.sprocket();

    widgets.update(id, before, before.withSize(4).withName("cog"));

    assertEquals(
        List.of(new Change("name", "\"sprocket\"", "\"cog\""), new Change("size", "3", "4")),
        changes(id));
  }

  @Test
  void recordsNoUnchangedField() {
    UUID id = UUID.randomUUID();

    widgets.update(id, Widget.sprocket(), Widget.sprocket());

    assertEquals(List.of(), changes(id));
  }

  @Test
  void recordsANestedFieldByItsDottedPath() {
    UUID id = UUID.randomUUID();
    Widget before = Widget.sprocket();

    widgets.update(id, before, before.withDimensions(new Dimensions(11, 20)));

    assertEquals(List.of(new Change("dimensions.width", "10", "11")), changes(id));
  }

  @Test
  void recordsACollectionAsOneValue() {
    UUID id = UUID.randomUUID();
    Widget before = Widget.sprocket();

    widgets.update(id, before, before.withTags(List.of("a", "b")));

    assertEquals(List.of(new Change("tags", "[\"a\"]", "[\"a\",\"b\"]")), changes(id));
  }

  @Test
  void recordsEnumsAndInstantsAsJsonStrings() {
    UUID id = UUID.randomUUID();
    Widget before = Widget.sprocket();

    widgets.update(
        id,
        before,
        before.withColour(Colour.RED).withUpdatedAt(Instant.parse("2030-01-02T08:30:00Z")));

    assertEquals(
        List.of(
            new Change("colour", "\"BLUE\"", "\"RED\""),
            new Change("updatedAt", "\"2030-01-01T12:00:00Z\"", "\"2030-01-02T08:30:00Z\"")),
        changes(id));
  }

  @Test
  void recordsANullValueAsJsonNull() {
    UUID id = UUID.randomUUID();
    Widget before = Widget.sprocket().withNote("fragile");

    widgets.update(id, before, before.withNote(null));

    assertEquals(List.of(new Change("note", "\"fragile\"", "null")), changes(id));
  }

  @Test
  void recordsACreateWithJsonNullAsEveryOldValue() {
    UUID id = UUID.randomUUID();

    widgets.update(id, null, Widget.sprocket());

    assertEquals(
        List.of(
            new Change("colour", "null", "\"BLUE\""),
            new Change("dimensions.height", "null", "20"),
            new Change("dimensions.width", "null", "10"),
            new Change("name", "null", "\"sprocket\""),
            new Change("owner.email", "null", "\"bob@example.test\""),
            new Change("size", "null", "3"),
            new Change("tags", "null", "[\"a\"]"),
            new Change("updatedAt", "null", "\"2030-01-01T12:00:00Z\"")),
        changes(id));
  }

  @Test
  void recordsADeleteWithJsonNullAsEveryNewValue() {
    UUID id = UUID.randomUUID();

    widgets.update(id, Widget.sprocket(), null);

    assertEquals(
        List.of(
            new Change("colour", "\"BLUE\"", "null"),
            new Change("dimensions.height", "20", "null"),
            new Change("dimensions.width", "10", "null"),
            new Change("name", "\"sprocket\"", "null"),
            new Change("owner.email", "\"bob@example.test\"", "null"),
            new Change("size", "3", "null"),
            new Change("tags", "[\"a\"]", "null"),
            new Change("updatedAt", "\"2030-01-01T12:00:00Z\"", "null")),
        changes(id));
  }

  @Test
  void ignoresAFieldAnnotatedAuditIgnored() {
    UUID id = UUID.randomUUID();
    Widget before = Widget.sprocket();

    widgets.update(id, before, before.withSecretHash("hash-2"));

    assertEquals(List.of(), changes(id));
  }

  @Test
  void ignoresANestedTypeAnnotatedAuditIgnored() {
    UUID id = UUID.randomUUID();
    Widget before = Widget.sprocket();

    widgets.update(
        id, before, before.withOwner(new Owner("bob@example.test", new Profile("Robert"))));

    assertEquals(List.of(), changes(id));
  }

  @Test
  void recordsAPathOnlyFieldWithoutItsValues() {
    UUID id = UUID.randomUUID();
    Widget before = Widget.sprocket();

    widgets.updateWithEmailByPath(
        id, before, before.withOwner(new Owner("robert@example.test", new Profile("Bob"))));

    assertEquals(List.of(new Change("owner.email", null, null)), changes(id));
  }

  @Test
  void recordsEveryFieldUnderAPathOnlyRecordWithoutItsValues() {
    UUID id = UUID.randomUUID();
    Widget before = Widget.sprocket();

    widgets.updateWithOwnerByPath(
        id,
        before,
        before.withSize(4).withOwner(new Owner("robert@example.test", new Profile("Bob"))));

    assertEquals(
        List.of(new Change("owner.email", null, null), new Change("size", "3", "4")), changes(id));
  }

  @Test
  void ignoresAFieldTheActionIgnores() {
    UUID id = UUID.randomUUID();
    Widget before = Widget.sprocket();

    widgets.updateIgnoringNote(id, before, before.withNote("fragile"));

    assertEquals(List.of(), changes(id));
  }

  @Test
  void prefersIgnoringOverPathOnly() {
    UUID id = UUID.randomUUID();
    Widget before = Widget.sprocket();

    widgets.updateWithNoteByPathAndIgnored(id, before, before.withNote("fragile"));

    assertEquals(List.of(), changes(id));
  }

  @Test
  void ignoresATypeTheActionIgnoresWhereverItAppears() {
    UUID id = UUID.randomUUID();
    Widget before = Widget.sprocket();

    widgets.updateIgnoringDimensions(
        id, before, before.withDimensions(new Dimensions(11, 21)).withSize(4));

    assertEquals(List.of(new Change("size", "3", "4")), changes(id));
  }

  @Test
  void recordsNoChangesWhenTheStateItselfIsIgnored() {
    UUID id = UUID.randomUUID();

    widgets.updateProfile(id, new Profile("Bob"), new Profile("Robert"));

    AuditRecorded event = single(id).event();
    assertEquals(AuditOutcome.SUCCESS, event.getOutcome());
    assertTrue(event.getChanges().isEmpty());
  }

  @Test
  void recordsNoChangesWhenTheUseCaseReportsNoState() {
    UUID id = UUID.randomUUID();

    widgets.updateWithoutReporting(id);

    assertEquals(List.of(), changes(id));
  }

  @Test
  void recordsNoChangesForARefusal() {
    UUID id = UUID.randomUUID();
    Widget before = Widget.sprocket();

    assertThrows(
        WidgetRefusedException.class,
        () -> widgets.updateAndRefuse(id, before, before.withName("cog")));

    AuditRecorded event = single(id).event();
    assertEquals(AuditOutcome.DENIED, event.getOutcome());
    assertTrue(event.getChanges().isEmpty());
  }

  @Test
  void failsAndRollsBackAStateOfAnotherType() {
    UUID id = UUID.randomUUID();

    assertThrows(
        RuntimeException.class,
        () -> widgets.updateReportingAnotherType(id, new Gadget("a"), new Gadget("b")));

    assertFalse(recorded.widgetExists(id));
    assertNoSuccess(id);
  }

  @Test
  void failsAndRollsBackAStateTheActionDoesNotDeclare() {
    UUID id = UUID.randomUUID();

    assertThrows(
        RuntimeException.class,
        () ->
            widgets.updateReportingUndeclaredState(
                id, Widget.sprocket(), Widget.sprocket().withSize(4)));

    assertFalse(recorded.widgetExists(id));
    assertNoSuccess(id);
  }

  @Test
  void rejectsAPathOnlyFieldTheStateDoesNotHave() {
    UUID id = UUID.randomUUID();

    assertThrows(IllegalStateException.class, () -> widgets.updateWithUnknownPathOnly(id));

    assertFalse(recorded.widgetExists(id));
    assertTrue(recorded.withSubject(id.toString()).isEmpty());
  }

  @Test
  void rejectsAnIgnoredFieldTheStateDoesNotHave() {
    UUID id = UUID.randomUUID();

    assertThrows(IllegalStateException.class, () -> widgets.updateWithUnknownIgnore(id));

    assertFalse(recorded.widgetExists(id));
    assertTrue(recorded.withSubject(id.toString()).isEmpty());
  }

  @Test
  void rejectsAStateOutsideAnAuditedMethod() {
    assertThrows(
        IllegalStateException.class, () -> audit.state(Widget.sprocket(), Widget.sprocket()));
  }

  private List<Change> changes(UUID id) {
    AuditRecorded event = single(id).event();
    assertEquals(AuditOutcome.SUCCESS, event.getOutcome());
    return event.getChanges().stream().map(Change::of).toList();
  }

  private Row single(UUID id) {
    List<Row> rows = recorded.withSubject(id.toString());
    assertEquals(1, rows.size(), () -> "audit events with subject " + id + ": " + rows);
    return rows.getFirst();
  }

  private void assertNoSuccess(UUID id) {
    assertTrue(
        recorded.withSubject(id.toString()).stream()
            .noneMatch(row -> row.event().getOutcome() == AuditOutcome.SUCCESS));
  }
}
