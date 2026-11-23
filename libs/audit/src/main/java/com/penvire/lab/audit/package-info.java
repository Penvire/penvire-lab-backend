/**
 * Recording audit events through the service's outbox (ADR 0006, ADR 0008).
 *
 * <p>A use case declares its action with {@link com.penvire.lab.audit.Audited} and reports its
 * target to {@link com.penvire.lab.audit.AuditScope}:
 *
 * <pre>{@code
 * @Audited(value = "identity.client.update", deniedOn = AuthorizationException.class)
 * @Transactional
 * public void update(ClientId id, ...) {
 *   audit.target(id.toString());
 *   ...
 * }
 * }</pre>
 *
 * <p>When the method returns, the library records {@code SUCCESS} in the method's transaction, so
 * an {@code @Audited} method needs one; without it the action fails. When the method throws, the
 * action's {@code deniedOn} and {@code failedOn} decide whether the failure is recorded as {@code
 * DENIED}, as {@code FAILED} or not at all. By default, refusals by Quarkus security are {@code
 * DENIED} and errors are left to logs and traces. A recorded failure is written in a new
 * transaction after the action's transaction has rolled back, and the exception is rethrown
 * unchanged. An exception implementing {@link com.penvire.lab.audit.AuditReason} supplies the
 * reason code; any other gives {@code error}. A checked exception does not roll back a
 * {@code @Transactional} method by default, so its change commits beside the {@code FAILED} record.
 *
 * <p>The actor is the authenticated caller ({@code USER}), an unauthenticated HTTP caller ({@code
 * ANONYMOUS}), or the service itself outside an HTTP request ({@code SYSTEM}), unless the use case
 * sets it with {@link com.penvire.lab.audit.AuditScope#actor}. The record is the service's own
 * {@code dev.penvire.lab.<service>.events.v1.AuditRecorded} from its contract package, published to
 * {@code <service>.audit}; {@code penvire.audit.service} names the service and defaults to {@code
 * quarkus.application.name}. The record key is the target ID, else the actor ID, else the event ID.
 *
 * <p>Not recorded yet: {@code changes}, which stays empty, the {@code tenantid} attribute, and the
 * client a user acted through.
 */
@NullMarked
package com.penvire.lab.audit;

import org.jspecify.annotations.NullMarked;
