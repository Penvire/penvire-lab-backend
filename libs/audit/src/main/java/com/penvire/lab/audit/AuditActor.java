package com.penvire.lab.audit;

import org.jspecify.annotations.Nullable;

/**
 * Who performed an audited action. Use the factories; the constructor rejects combinations ADR 0006
 * does not allow.
 *
 * @param type what kind of actor this is
 * @param id the user's {@code sub} or the client's {@code client_id}; null for {@code SYSTEM} and
 *     {@code ANONYMOUS}
 * @param clientId the client a user acted through; only for {@code USER}
 */
public record AuditActor(Type type, @Nullable String id, @Nullable String clientId) {

  /**
   * Checks the combination; prefer the factories.
   *
   * @throws IllegalArgumentException if {@code USER} or {@code CLIENT} has no ID, {@code SYSTEM} or
   *     {@code ANONYMOUS} has one, or any type but {@code USER} has a client
   */
  public AuditActor(Type type, @Nullable String id, @Nullable String clientId) {
    if (type == null) {
      throw new IllegalArgumentException("type cannot be null");
    }
    if (type == Type.USER && id == null) {
      throw new IllegalArgumentException("id cannot be null for USER type");
    }
    if (type == Type.CLIENT && (id == null || clientId != null)) {
      throw new IllegalArgumentException("a CLIENT acts for itself, not through another client");
    }
    if (type == Type.SYSTEM && (id != null || clientId != null)) {
      throw new IllegalArgumentException("id and clientId cannot be set for SYSTEM type");
    }
    if (type == Type.ANONYMOUS && (id != null || clientId != null)) {
      throw new IllegalArgumentException("id and clientId cannot be set for ANONYMOUS type");
    }

    this.type = type;
    this.id = id;
    this.clientId = clientId;
  }

  /**
   * A user, by their {@code sub}.
   *
   * @param clientId the client the user acted through, or null
   */
  public static AuditActor user(String sub, @Nullable String clientId) {
    return new AuditActor(Type.USER, sub, clientId);
  }

  /** A client acting for itself, including another service, by its {@code client_id}. */
  public static AuditActor client(String clientId) {
    return new AuditActor(Type.CLIENT, clientId, null);
  }

  /** The service on its own, for example in a scheduled job. */
  public static AuditActor system() {
    return new AuditActor(Type.SYSTEM, null, null);
  }

  /** A caller that is not authenticated. */
  public static AuditActor anonymous() {
    return new AuditActor(Type.ANONYMOUS, null, null);
  }

  /** The kinds of actor; the same symbols as the contract's {@code AuditActorType}. */
  public enum Type {
    USER,
    CLIENT,
    SYSTEM,
    ANONYMOUS
  }
}
