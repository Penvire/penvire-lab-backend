package com.penvire.lab.audit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Audited use cases that report the state before and after their change. */
@ApplicationScoped
public class WidgetStateService {

  private final EntityManager entityManager;
  private final AuditScope audit;

  WidgetStateService(EntityManager entityManager, AuditScope audit) {
    this.entityManager = entityManager;
    this.audit = audit;
  }

  /** Before is null for a create, after is null for a delete. */
  @Audited(value = "widget.widget.update", state = Widget.class)
  @Transactional
  public void update(UUID id, @Nullable Widget before, @Nullable Widget after) {
    audit.target(id.toString());
    audit.state(before, after);
  }

  @Audited(value = "widget.widget.update", state = Widget.class, pathOnly = "owner.email")
  @Transactional
  public void updateWithEmailByPath(UUID id, Widget before, Widget after) {
    audit.target(id.toString());
    audit.state(before, after);
  }

  /** A path naming a nested record covers every field under it. */
  @Audited(value = "widget.widget.update", state = Widget.class, pathOnly = "owner")
  @Transactional
  public void updateWithOwnerByPath(UUID id, Widget before, Widget after) {
    audit.target(id.toString());
    audit.state(before, after);
  }

  @Audited(value = "widget.widget.update", state = Widget.class, ignore = "note")
  @Transactional
  public void updateIgnoringNote(UUID id, Widget before, Widget after) {
    audit.target(id.toString());
    audit.state(before, after);
  }

  @Audited(value = "widget.widget.update", state = Widget.class, pathOnly = "note", ignore = "note")
  @Transactional
  public void updateWithNoteByPathAndIgnored(UUID id, Widget before, Widget after) {
    audit.target(id.toString());
    audit.state(before, after);
  }

  @Audited(value = "widget.widget.update", state = Widget.class, ignoreTypes = Dimensions.class)
  @Transactional
  public void updateIgnoringDimensions(UUID id, Widget before, Widget after) {
    audit.target(id.toString());
    audit.state(before, after);
  }

  /** The state itself is of a type annotated {@code @AuditIgnored}. */
  @Audited(value = "widget.profile.update", state = Profile.class)
  @Transactional
  public void updateProfile(UUID id, Profile before, Profile after) {
    audit.target(id.toString());
    audit.state(before, after);
  }

  /** Declares a state but has nothing to report, for example a change that turned out a no-op. */
  @Audited(value = "widget.widget.update", state = Widget.class)
  @Transactional
  public void updateWithoutReporting(UUID id) {
    audit.target(id.toString());
  }

  @Audited(value = "widget.widget.update", state = Widget.class)
  @Transactional
  public void updateReportingAnotherType(UUID id, Gadget before, Gadget after) {
    insert(id);
    audit.target(id.toString());
    audit.state(before, after);
  }

  @Audited("widget.widget.update")
  @Transactional
  public void updateReportingUndeclaredState(UUID id, Widget before, Widget after) {
    insert(id);
    audit.target(id.toString());
    audit.state(before, after);
  }

  @Audited(value = "widget.widget.update", state = Widget.class, pathOnly = "owner.emial")
  @Transactional
  public void updateWithUnknownPathOnly(UUID id) {
    insert(id);
    audit.target(id.toString());
  }

  @Audited(value = "widget.widget.update", state = Widget.class, ignore = "nope")
  @Transactional
  public void updateWithUnknownIgnore(UUID id) {
    insert(id);
    audit.target(id.toString());
  }

  /** Reports a change, then is refused, so the change never happened. */
  @Audited(
      value = "widget.widget.update",
      state = Widget.class,
      deniedOn = WidgetRefusedException.class)
  @Transactional
  public void updateAndRefuse(UUID id, Widget before, Widget after) {
    audit.target(id.toString());
    audit.state(before, after);
    throw new WidgetRefusedException("widget_locked");
  }

  private void insert(UUID id) {
    entityManager
        .createNativeQuery("INSERT INTO widget (id) VALUES (:id)")
        .setParameter("id", id)
        .executeUpdate();
  }
}
