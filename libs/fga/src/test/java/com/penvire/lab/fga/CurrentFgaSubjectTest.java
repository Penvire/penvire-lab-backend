package com.penvire.lab.fga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penvire.lab.fga.model.FgaSubject;
import io.quarkus.security.UnauthorizedException;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CurrentFgaSubjectTest {

  @Inject CurrentFgaSubject currentCaller;
  @Inject CurrentIdentityAssociation identityAssociation;

  @Test
  void returnsEmptyOutsideRequestContext() {
    assertTrue(currentCaller.current().isEmpty());
    assertThrows(UnauthorizedException.class, currentCaller::require);
  }

  @Test
  @ActivateRequestContext
  void resolvesAuthenticatedAndAnonymousIdentities() {
    identityAssociation.setIdentity(QuarkusSecurityIdentity.builder().setAnonymous(true).build());
    assertTrue(currentCaller.current().isEmpty());
    assertThrows(UnauthorizedException.class, currentCaller::require);

    identityAssociation.setIdentity(
        QuarkusSecurityIdentity.builder().setPrincipal(() -> "alice").build());
    assertEquals(Optional.of(FgaSubject.user("alice")), currentCaller.current());
    assertEquals(FgaSubject.user("alice"), currentCaller.require());
  }

  @Test
  void fixedAndAnonymousFactoriesWorkOutsideCdi() {
    FgaSubject bob = FgaSubject.user("bob");
    CurrentFgaSubject fixed = CurrentFgaSubject.fixed(bob);
    assertEquals(Optional.of(bob), fixed.current());
    assertEquals(bob, fixed.require());

    CurrentFgaSubject anon = CurrentFgaSubject.anonymous();
    assertTrue(anon.current().isEmpty());
    assertThrows(UnauthorizedException.class, anon::require);
  }
}
