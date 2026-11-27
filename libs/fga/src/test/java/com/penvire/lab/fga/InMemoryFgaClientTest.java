package com.penvire.lab.fga;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penvire.lab.fga.model.FgaObject;
import com.penvire.lab.fga.model.FgaRelation;
import com.penvire.lab.fga.model.FgaSubject;
import com.penvire.lab.fga.model.FgaTuple;
import com.penvire.lab.fga.model.FgaType;
import com.penvire.lab.fga.query.FgaTupleFilter;
import io.quarkus.security.ForbiddenException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InMemoryFgaClientTest {

  private static final FgaType TENANT = FgaType.of("tenant");
  private static final FgaRelation ADMIN = FgaRelation.of("admin");
  private static final FgaRelation MEMBER = FgaRelation.of("member");

  @Test
  void checksWritesDeletesReadsAndListsTuples() {
    InMemoryFgaClient client = new InMemoryFgaClient();
    FgaSubject alice = FgaSubject.user("alice");
    FgaSubject bob = FgaSubject.user("bob");
    FgaObject tenant = FgaObject.of(TENANT, "t1");
    FgaObject otherTenant = FgaObject.of(TENANT, "t2");

    assertFalse(client.check(alice, ADMIN, tenant));
    assertThrows(ForbiddenException.class, () -> client.require(alice, ADMIN, tenant));

    client.write(FgaTuple.of(alice, MEMBER, tenant), FgaTuple.of(bob, ADMIN, tenant));
    client.write(FgaTuple.of(alice, MEMBER, tenant));
    assertTrue(client.check(alice, MEMBER, tenant));
    assertDoesNotThrow(() -> client.require(alice, MEMBER, tenant));

    client.writeAndDelete(
        List.of(FgaTuple.of(alice, ADMIN, tenant)), List.of(FgaTuple.of(alice, MEMBER, tenant)));
    assertFalse(client.check(alice, MEMBER, tenant));
    assertTrue(client.check(alice, ADMIN, tenant));

    assertEquals(List.of(tenant), client.listObjects(alice, ADMIN, TENANT));
    assertEquals(
        Set.of(FgaTuple.of(alice, ADMIN, tenant), FgaTuple.of(bob, ADMIN, tenant)),
        Set.copyOf(client.read(FgaTupleFilter.forObject(tenant))));
    assertEquals(
        List.of(FgaTuple.of(alice, ADMIN, tenant)),
        client.read(FgaTupleFilter.forSubject(alice, ADMIN, TENANT)));

    client.grant(alice, MEMBER, otherTenant);
    assertEquals(
        Set.of(
            FgaTuple.of(alice, ADMIN, tenant),
            FgaTuple.of(bob, ADMIN, tenant),
            FgaTuple.of(alice, MEMBER, otherTenant)),
        Set.copyOf(client.read(FgaTupleFilter.ALL)));
    assertEquals(
        Set.of(FgaTuple.of(alice, ADMIN, tenant), FgaTuple.of(alice, MEMBER, otherTenant)),
        Set.copyOf(client.read(FgaTupleFilter.forSubject(alice, TENANT))));

    client.revoke(alice, MEMBER, otherTenant);
    assertFalse(client.check(alice, MEMBER, otherTenant));

    client.delete(FgaTuple.of(alice, ADMIN, tenant));
    client.delete(FgaTuple.of(alice, ADMIN, tenant));
    assertFalse(client.check(alice, ADMIN, tenant));

    client.clear();
    assertTrue(client.tuples().isEmpty());
  }

  @Test
  void failsClosedWhenSimulatedUnavailable() {
    InMemoryFgaClient client = new InMemoryFgaClient();
    FgaSubject alice = FgaSubject.user("alice");
    FgaObject tenant = FgaObject.of(TENANT, "t1");

    client.setUnavailable(true);
    // Test setup helpers still work during a simulated outage.
    client.grant(alice, ADMIN, tenant);

    assertThrows(FgaUnavailableException.class, () -> client.check(alice, ADMIN, tenant));
    assertThrows(FgaUnavailableException.class, () -> client.require(alice, ADMIN, tenant));
    assertThrows(
        FgaUnavailableException.class, () -> client.write(FgaTuple.of(alice, ADMIN, tenant)));
    assertThrows(
        FgaUnavailableException.class, () -> client.read(FgaTupleFilter.forObject(tenant)));
    assertThrows(FgaUnavailableException.class, () -> client.listObjects(alice, ADMIN, TENANT));
  }

  @Test
  void writeAndDeleteValidatesBeforePublishingAtomically() {
    InMemoryFgaClient client = new InMemoryFgaClient();
    FgaSubject alice = FgaSubject.user("alice");
    FgaObject tenant = FgaObject.of(TENANT, "t1");
    FgaTuple member = FgaTuple.of(alice, MEMBER, tenant);
    client.write(member);

    List<FgaTuple> badWrites = java.util.Arrays.asList((FgaTuple) null);
    assertThrows(
        NullPointerException.class, () -> client.writeAndDelete(badWrites, List.of(member)));
    // Delete must not have leaked when validation of writes failed.
    assertTrue(client.check(alice, MEMBER, tenant));
  }
}
