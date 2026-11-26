package com.penvire.lab.audit;

import org.jspecify.annotations.Nullable;

/**
 * Drop-in {@link AuditScope} for plain unit tests that call {@code @Audited} services directly
 * without Quarkus CDI interceptors.
 */
public final class TestAuditScope extends AuditScope {

  private @Nullable String lastTargetId;
  private @Nullable AuditActor lastActor;
  private @Nullable Object lastBefore;
  private @Nullable Object lastAfter;

  @Override
  public void target(String targetId) {
    this.lastTargetId = targetId;
  }

  @Override
  public void actor(AuditActor actor) {
    this.lastActor = actor;
  }

  @Override
  public void state(@Nullable Object before, @Nullable Object after) {
    this.lastBefore = before;
    this.lastAfter = after;
  }

  public @Nullable String lastTargetId() {
    return lastTargetId;
  }

  public @Nullable AuditActor lastActor() {
    return lastActor;
  }

  public @Nullable Object lastBefore() {
    return lastBefore;
  }

  public @Nullable Object lastAfter() {
    return lastAfter;
  }
}
