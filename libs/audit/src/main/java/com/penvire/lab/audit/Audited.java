package com.penvire.lab.audit;

import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Records an audit event for every call of the annotated use case method (ADR 0006, ADR 0008).
 *
 * <p>Declare it on each method. It may appear on a type only because the library's interceptors
 * carry it; a use case class annotated with it fails on every call, since one action for all its
 * methods is never right.
 */
@InterceptorBinding
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

  /**
   * The action, {@code <service>.<resource>.<verb>}, for example {@code identity.user.update}. The
   * service must be this service; the resource becomes the event's {@code targetType}.
   */
  @Nonbinding
  String value() default "";

  /**
   * Exceptions recorded as {@code DENIED}; subclasses match too, and this list wins over {@code
   * failedOn}. Defaults to the refusals of Quarkus security: a caller without permission or one
   * that is not authenticated. Listing exceptions replaces the default.
   */
  @Nonbinding
  Class<? extends Exception>[] deniedOn() default {
    ForbiddenException.class, UnauthorizedException.class
  };

  /**
   * Exceptions recorded as {@code FAILED}; subclasses match too. None by default: errors belong in
   * logs and traces, and an action lists only failures that are security-relevant, such as a key
   * rotation that failed. An exception that matches neither list is not recorded, and a type listed
   * in both fails the call before the use case runs.
   */
  @Nonbinding
  Class<? extends Exception>[] failedOn() default {};

  /**
   * The record type of the state the use case reports through {@link AuditScope#state}; its changed
   * fields become the event's {@code changes}. Nested records are walked into, with dotted paths
   * such as {@code owner.email}; any other field, a collection too, is one value. None by default:
   * a use case that reports state without declaring it fails, and a type that is no record fails
   * the call before the use case runs.
   */
  @Nonbinding
  Class<?> state() default void.class;

  /**
   * Paths of {@link #state} recorded without their values, for example {@code owner.email}. A path
   * naming a nested record covers every field under it. An unknown path fails the call before the
   * use case runs.
   */
  @Nonbinding
  String[] pathOnly() default {};

  /**
   * Paths of {@link #state} never recorded; wins over {@link #pathOnly}. A path naming a nested
   * record covers every field under it. An unknown path fails the call before the use case runs.
   */
  @Nonbinding
  String[] ignore() default {};

  /**
   * Types never recorded wherever they appear in {@link #state}, subtypes included, as if they were
   * annotated {@link AuditIgnored}; for types the service does not own.
   */
  @Nonbinding
  Class<?>[] ignoreTypes() default {};
}
