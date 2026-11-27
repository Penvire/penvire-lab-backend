package com.penvire.lab.fga;

import com.penvire.lab.fga.model.FgaSubject;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;

/**
 * Default {@link CurrentFgaSubject} backed by Quarkus {@link SecurityIdentity}. Mirrors {@code
 * libs/audit}'s {@code CallerResolver}: returns empty outside an active request context or when the
 * identity is anonymous, and otherwise maps {@code principal.getName()} to {@code user:<name>}.
 */
@Singleton
class SecurityIdentityCurrentFgaSubject implements CurrentFgaSubject {

  private final Instance<SecurityIdentity> identity;

  @Inject
  SecurityIdentityCurrentFgaSubject(Instance<SecurityIdentity> identity) {
    this.identity = identity;
  }

  @Override
  public Optional<FgaSubject> current() {
    ArcContainer container = Arc.container();
    if (container == null || !container.requestContext().isActive()) {
      return Optional.empty();
    }

    SecurityIdentity caller = identity.get();
    if (caller.isAnonymous()) {
      return Optional.empty();
    }

    String name = caller.getPrincipal().getName();
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }

    return Optional.of(FgaSubject.user(name));
  }
}
