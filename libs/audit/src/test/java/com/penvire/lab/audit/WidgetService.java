package com.penvire.lab.audit;

import io.quarkus.security.ForbiddenException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.UUID;

/** Audited use cases as a service writes them. */
@ApplicationScoped
public class WidgetService {

  private final EntityManager entityManager;
  private final AuditScope audit;
  private final WidgetPartService parts;

  WidgetService(EntityManager entityManager, AuditScope audit, WidgetPartService parts) {
    this.entityManager = entityManager;
    this.audit = audit;
    this.parts = parts;
  }

  @Audited("widget.widget.create")
  @Transactional
  public void create(UUID id) {
    insert(id);
    audit.target(id.toString());
  }

  /** Stores the widget, then fails, so the change must roll back. Records refusals and errors. */
  @Audited(
      value = "widget.widget.create",
      deniedOn = WidgetRefusedException.class,
      failedOn = Exception.class)
  @Transactional
  public void createAndFail(UUID id, RuntimeException failure) {
    insert(id);
    audit.target(id.toString());
    throw failure;
  }

  /** Refuses before a widget exists, so there is no target. */
  @Audited(value = "widget.widget.create", deniedOn = WidgetRefusedException.class)
  @Transactional
  public void refuseBeforeCreating(RuntimeException refusal) {
    throw refusal;
  }

  /** Lists no exceptions, so the defaults apply: only refusals by Quarkus security are recorded. */
  @Audited("widget.widget.create")
  @Transactional
  public void createAndFailWithDefaults(UUID id, RuntimeException failure) {
    insert(id);
    audit.target(id.toString());
    throw failure;
  }

  /** Records refusals and an unavailable store, nothing else. */
  @Audited(
      value = "widget.widget.update",
      deniedOn = WidgetRefusedException.class,
      failedOn = WidgetUnavailableException.class)
  @Transactional
  public void updateAndFail(UUID id, RuntimeException failure) {
    insert(id);
    audit.target(id.toString());
    throw failure;
  }

  /** Records refusals only. */
  @Audited(
      value = "widget.widget.update",
      deniedOn = WidgetRefusedException.class,
      failedOn = {})
  @Transactional
  public void updateAndFailRecordingRefusalsOnly(UUID id, RuntimeException failure) {
    insert(id);
    audit.target(id.toString());
    throw failure;
  }

  /** A refusal matches both lists; deniedOn wins. */
  @Audited(
      value = "widget.widget.update",
      deniedOn = WidgetRefusedException.class,
      failedOn = RuntimeException.class)
  @Transactional
  public void updateAndFailWithOverlappingLists(UUID id, RuntimeException failure) {
    insert(id);
    audit.target(id.toString());
    throw failure;
  }

  /** The same class in both lists is a mistake, rejected even when the action would succeed. */
  @Audited(
      value = "widget.widget.update",
      deniedOn = WidgetRefusedException.class,
      failedOn = WidgetRefusedException.class)
  @Transactional
  public void updateWithConflictingLists(UUID id) {
    insert(id);
    audit.target(id.toString());
  }

  /** Quarkus refuses a caller without the role before or after our interceptor runs. */
  @Audited(value = "widget.widget.delete", deniedOn = ForbiddenException.class)
  @RolesAllowed("admin")
  @Transactional
  public void deleteAsAdmin(UUID id) {
    audit.target(id.toString());
  }

  /** Lists no exceptions, so a refusal by Quarkus security is DENIED by default. */
  @Audited("widget.widget.delete")
  @RolesAllowed("admin")
  @Transactional
  public void deleteAsAdminWithDefaults(UUID id) {
    audit.target(id.toString());
  }

  @Audited("widget.widget.create")
  @Transactional
  public void createAs(UUID id, AuditActor actor) {
    insert(id);
    audit.target(id.toString());
    audit.actor(actor);
  }

  @Audited("widget.widget.create")
  @Transactional
  public void createWithPart(UUID id, UUID partId) {
    insert(id);
    parts.attach(partId);
    audit.target(id.toString());
  }

  @Audited("widget.widget.read")
  public void readWithoutTransaction(UUID id) {
    audit.target(id.toString());
  }

  @Audited("gadget.widget.create")
  @Transactional
  public void createWithForeignAction(UUID id) {
    insert(id);
    audit.target(id.toString());
  }

  @Audited("widget.create")
  @Transactional
  public void createWithMalformedAction(UUID id) {
    insert(id);
    audit.target(id.toString());
  }

  private void insert(UUID id) {
    entityManager
        .createNativeQuery("INSERT INTO widget (id) VALUES (:id)")
        .setParameter("id", id)
        .executeUpdate();
  }
}
