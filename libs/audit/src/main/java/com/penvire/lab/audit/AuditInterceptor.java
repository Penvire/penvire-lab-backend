package com.penvire.lab.audit;

import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.jspecify.annotations.Nullable;

/**
 * Opens the frame of an {@link Audited} call and records {@code DENIED} or {@code FAILED} (ADR
 * 0008). It runs outside security checks such as {@code @RolesAllowed} and outside
 * {@code @Transactional}, so it sees their refusals and records a failure after the action's
 * transaction has rolled back. {@link AuditSuccessInterceptor} records {@code SUCCESS}.
 */
@Audited
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 100)
class AuditInterceptor {

  private final AuditScope scope;
  private final AuditRecorder recorder;

  @Inject
  AuditInterceptor(AuditScope scope, AuditRecorder recorder) {
    this.scope = scope;
    this.recorder = recorder;
  }

  @AroundInvoke
  Object record(InvocationContext context) throws Exception {
    Audited audited = declared(context);
    AuditScope.Frame frame = scope.enter(audited.value());

    try {
      return context.proceed();
    } catch (Exception failure) {
      if (!frame.recordingFailed) {
        recordFailure(audited, frame, failure);
      }
      throw failure;
    } finally {
      scope.exit();
    }
  }

  /** The method's own annotation, after rejecting class-level use and conflicting lists. */
  private static Audited declared(InvocationContext context) {
    if (!context.getMethod().isAnnotationPresent(Audited.class)) {
      throw new IllegalStateException(
          context.getMethod() + " must declare @Audited itself, not inherit it from its class");
    }

    Audited audited = context.getInterceptorBinding(Audited.class);
    for (Class<? extends Exception> denied : audited.deniedOn()) {
      for (Class<? extends Exception> failed : audited.failedOn()) {
        if (denied.equals(failed)) {
          throw new IllegalStateException(
              context.getMethod() + " lists " + denied.getName() + " as both denied and failed");
        }
      }
    }

    return audited;
  }

  /**
   * Records the failure in a new transaction if the action lists it. A record that cannot be
   * written is logged and attached to the failure as suppressed; the action's exception wins.
   */
  private void recordFailure(Audited audited, AuditScope.Frame frame, Exception failure) {
    Outcome outcome = outcomeOf(audited, failure);
    if (outcome == null) {
      return;
    }
    String reason = failure instanceof AuditReason known ? known.auditReason() : "error";

    try {
      QuarkusTransaction.requiringNew().run(() -> recorder.record(frame, outcome, reason));
    } catch (RuntimeException recording) {
      Log.errorf(recording, "Could not record %s of %s", outcome, frame.action);
      failure.addSuppressed(recording);
    }
  }

  /** {@code DENIED} or {@code FAILED} from the action's lists, or null if it records neither. */
  private @Nullable Outcome outcomeOf(Audited audited, Exception failure) {
    if (matches(audited.deniedOn(), failure)) {
      return Outcome.DENIED;
    }
    if (matches(audited.failedOn(), failure)) {
      return Outcome.FAILED;
    }
    return null;
  }

  private boolean matches(Class<? extends Exception>[] types, Exception failure) {
    for (Class<? extends Exception> type : types) {
      if (type.isInstance(failure)) {
        return true;
      }
    }
    return false;
  }
}
