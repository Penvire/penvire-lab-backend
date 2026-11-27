package com.penvire.lab.fga;

import com.penvire.lab.fga.model.FgaPrincipal;
import com.penvire.lab.fga.model.FgaSubject;
import io.quarkus.security.UnauthorizedException;
import java.util.Optional;

/**
 * Resolves the authenticated caller of the current request as an {@link FgaSubject}.
 *
 * <p>Inject this into use cases that enforce OpenFGA checks ({@link FgaClient#require}) or write
 * caller-owned tuples (for example granting initial ownership on resource creation or assigning
 * roles). The default CDI implementation maps authenticated {@code SecurityIdentity} principals to
 * {@code user:<principal.getName()>}; {@code client:<client_id>} mapping will be added once {@code
 * BearerPrincipal} distinguishes client-credentials callers.
 */
@FunctionalInterface
public interface CurrentFgaSubject {

  /**
   * Returns the authenticated caller, or empty when unauthenticated or outside a request context.
   */
  Optional<FgaSubject> current();

  /**
   * Returns the authenticated caller.
   *
   * @throws UnauthorizedException when unauthenticated or outside a request context
   */
  default FgaSubject require() {
    return current().orElseThrow(() -> new UnauthorizedException("Authentication required"));
  }

  /** Returns a fixed caller provider, useful in unit tests outside CDI. */
  static CurrentFgaSubject fixed(FgaPrincipal principal) {
    FgaSubject subject = principal.toFgaSubject();
    return () -> Optional.of(subject);
  }

  /** Returns an unauthenticated caller provider, useful in unit tests outside CDI. */
  static CurrentFgaSubject anonymous() {
    return Optional::empty;
  }
}
