package com.penvire.lab.audit;

import io.quarkus.arc.Arc;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

/** The actor of an audited action when the use case does not set one (ADR 0006). */
@Singleton
class CallerResolver {

  private final Instance<SecurityIdentity> identity;
  private final Instance<CurrentVertxRequest> request;

  CallerResolver(Instance<SecurityIdentity> identity, Instance<CurrentVertxRequest> request) {
    this.identity = identity;
    this.request = request;
  }

  /**
   * The authenticated caller as {@code USER}; an unauthenticated caller of an HTTP request as
   * {@code ANONYMOUS}; otherwise {@code SYSTEM}, for example in a scheduled job or an event
   * consumer, which have a request context but no HTTP request. The client a user acted through is
   * not known yet.
   */
  AuditActor caller() {
    if (!Arc.container().requestContext().isActive()) {
      return AuditActor.system();
    }

    SecurityIdentity caller = identity.get();
    if (!caller.isAnonymous()) {
      return AuditActor.user(caller.getPrincipal().getName(), null);
    }

    return request.get().getCurrent() != null ? AuditActor.anonymous() : AuditActor.system();
  }
}
