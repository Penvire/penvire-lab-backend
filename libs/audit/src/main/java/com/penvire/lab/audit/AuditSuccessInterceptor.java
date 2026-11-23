package com.penvire.lab.audit;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

/**
 * Records {@code SUCCESS} for an {@link Audited} call inside the method's {@code @Transactional},
 * so the record commits or rolls back with the change (ADR 0008). {@link AuditInterceptor} opened
 * the frame. If recording fails, it marks the frame and rethrows, and the action fails with that
 * error.
 */
@Audited
@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE)
class AuditSuccessInterceptor {

  private final AuditScope scope;
  private final AuditRecorder recorder;

  AuditSuccessInterceptor(AuditScope scope, AuditRecorder recorder) {
    this.scope = scope;
    this.recorder = recorder;
  }

  @AroundInvoke
  Object record(InvocationContext context) throws Exception {
    Object result = context.proceed();
    AuditScope.Frame frame = scope.current();

    try {
      recorder.record(frame, Outcome.SUCCESS, null);
    } catch (RuntimeException recording) {
      frame.recordingFailed = true;
      throw recording;
    }
    return result;
  }
}
