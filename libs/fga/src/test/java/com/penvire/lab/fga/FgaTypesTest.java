package com.penvire.lab.fga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.penvire.lab.fga.model.FgaObject;
import com.penvire.lab.fga.model.FgaRelation;
import com.penvire.lab.fga.model.FgaSubject;
import com.penvire.lab.fga.model.FgaTuple;
import com.penvire.lab.fga.model.FgaType;
import com.penvire.lab.fga.query.FgaTupleFilter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FgaTypesTest {

  private static final FgaType TENANT = FgaType.of("tenant");
  private static final FgaType ENGAGEMENT = FgaType.of("engagement");
  private static final FgaType ROLE = FgaType.of("role");
  private static final FgaRelation ADMIN = FgaRelation.of("admin");
  private static final FgaRelation ASSIGNEE = FgaRelation.of("assignee");
  private static final FgaRelation CAN_VIEW = FgaRelation.of("can_view");

  @Test
  void formatsDirectAndUsersetSubjects() {
    UUID id = UUID.fromString("01960000-0000-7000-8000-000000000001");

    FgaSubject user = FgaSubject.user(id);
    assertInstanceOf(FgaSubject.Direct.class, user);
    assertEquals("user:01960000-0000-7000-8000-000000000001", user.formatted());
    assertEquals(user.formatted(), user.toString());

    FgaSubject client = FgaSubject.client("cli-app");
    assertEquals("client:cli-app", client.formatted());

    FgaObject role = FgaObject.of(ROLE, "pentester");
    FgaSubject userset = FgaSubject.userset(role, ASSIGNEE);
    assertInstanceOf(FgaSubject.Userset.class, userset);
    assertEquals("role:pentester#assignee", userset.formatted());
    assertEquals(userset.formatted(), userset.toString());

    FgaSubject parentSubject = FgaSubject.of(FgaObject.of(TENANT, "acme"));
    assertEquals("tenant:acme", parentSubject.formatted());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "*", "alice:bob", "alice#admin", "alice bob", "alice\tbob"})
  void rejectsInvalidAndWildcardIds(String badId) {
    assertThrows(IllegalArgumentException.class, () -> FgaSubject.user(badId));
    assertThrows(IllegalArgumentException.class, () -> new FgaSubject.Direct("user", badId));
    assertThrows(
        IllegalArgumentException.class, () -> new FgaSubject.Userset("role", badId, "assignee"));
    assertThrows(IllegalArgumentException.class, () -> FgaObject.of(TENANT, badId));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "Tenant", "1tenant", "tenant-a", "tenant a"})
  void rejectsInvalidTypeAndRelationIdentifiers(String badIdentifier) {
    assertThrows(IllegalArgumentException.class, () -> FgaType.of(badIdentifier));
    assertThrows(IllegalArgumentException.class, () -> FgaRelation.of(badIdentifier));
  }

  @Test
  void formatsObjectsAndTuples() {
    UUID id = UUID.fromString("01960000-0000-7000-8000-000000000002");

    FgaObject obj = FgaObject.of(ENGAGEMENT, id);
    assertEquals("engagement:01960000-0000-7000-8000-000000000002", obj.formatted());
    assertEquals(obj.formatted(), obj.toString());

    FgaTuple tuple = FgaTuple.of(FgaSubject.user("alice"), CAN_VIEW, obj);
    assertEquals("can_view", tuple.relation());
    assertEquals(obj, tuple.object());
  }

  @Test
  void validatesTupleFilter() {
    FgaObject tenant = FgaObject.of(TENANT, "t1");
    FgaSubject alice = FgaSubject.user("alice");

    assertNull(FgaTupleFilter.ALL.formattedObjectFilter());
    assertEquals("tenant:t1", FgaTupleFilter.forObject(tenant).formattedObjectFilter());
    assertEquals("tenant:t1", FgaTupleFilter.forObject(tenant, ADMIN).formattedObjectFilter());
    assertEquals("tenant:", FgaTupleFilter.forSubject(alice, TENANT).formattedObjectFilter());
    assertEquals(
        "tenant:", FgaTupleFilter.forSubject(alice, ADMIN, TENANT).formattedObjectFilter());

    assertThrows(
        IllegalArgumentException.class, () -> new FgaTupleFilter(null, null, tenant, "tenant"));
    assertThrows(
        IllegalArgumentException.class, () -> new FgaTupleFilter(null, null, null, "tenant"));
    assertThrows(IllegalArgumentException.class, () -> new FgaTupleFilter(alice, null, null, null));
    assertThrows(
        IllegalArgumentException.class, () -> new FgaTupleFilter(null, "admin", null, null));
  }
}
