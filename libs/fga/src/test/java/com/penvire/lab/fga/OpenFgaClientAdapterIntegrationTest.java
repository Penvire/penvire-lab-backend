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
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OpenFgaClientAdapterIntegrationTest {

  private static final FgaType PLATFORM = FgaType.of("platform");
  private static final FgaType TENANT = FgaType.of("tenant");
  private static final FgaType ROLE = FgaType.of("role");
  private static final FgaType ENGAGEMENT = FgaType.of("engagement");

  private static final FgaRelation ADMIN = FgaRelation.of("admin");
  private static final FgaRelation MEMBER = FgaRelation.of("member");
  private static final FgaRelation ASSIGNEE = FgaRelation.of("assignee");
  private static final FgaRelation TENANT_REL = FgaRelation.of("tenant");
  private static final FgaRelation CAN_MANAGE_ACCOUNTS = FgaRelation.of("can_manage_accounts");
  private static final FgaRelation CAN_VIEW = FgaRelation.of("can_view");

  private static final String TEST_MODEL_JSON =
      """
      {
        "schema_version": "1.1",
        "type_definitions": [
          { "type": "user" },
          { "type": "client" },
          {
            "type": "role",
            "relations": {
              "assignee": { "this": {} }
            },
            "metadata": {
              "relations": {
                "assignee": {
                  "directly_related_user_types": [
                    { "type": "user" }
                  ]
                }
              }
            }
          },
          {
            "type": "platform",
            "relations": {
              "admin": { "this": {} },
              "can_manage_accounts": { "computedUserset": { "relation": "admin" } }
            },
            "metadata": {
              "relations": {
                "admin": {
                  "directly_related_user_types": [
                    { "type": "user" },
                    { "type": "client" }
                  ]
                }
              }
            }
          },
          {
            "type": "tenant",
            "relations": {
              "admin": { "this": {} },
              "member": {
                "union": {
                  "child": [
                    { "this": {} },
                    { "computedUserset": { "relation": "admin" } }
                  ]
                }
              }
            },
            "metadata": {
              "relations": {
                "admin": {
                  "directly_related_user_types": [
                    { "type": "user" }
                  ]
                },
                "member": {
                  "directly_related_user_types": [
                    { "type": "user" },
                    { "type": "role", "relation": "assignee" }
                  ]
                }
              }
            }
          },
          {
            "type": "engagement",
            "relations": {
              "tenant": { "this": {} },
              "can_view": {
                "tupleToUserset": {
                  "tupleset": { "relation": "tenant" },
                  "computedUserset": { "relation": "member" }
                }
              }
            },
            "metadata": {
              "relations": {
                "tenant": {
                  "directly_related_user_types": [
                    { "type": "tenant" }
                  ]
                }
              }
            }
          }
        ]
      }
      """;

  private static OpenFgaTestServer openfga;
  private static FgaClient client;

  @BeforeAll
  static void startOpenFga() {
    openfga = OpenFgaTestServer.start(TEST_MODEL_JSON);
    client = openfga.client();
  }

  @AfterAll
  static void stopOpenFga() {
    if (openfga != null) {
      openfga.close();
    }
  }

  @Test
  void checksDirectInheritedAndClientRelations() {
    FgaSubject adminUser = FgaSubject.user(UUID.randomUUID());
    FgaSubject serviceClient = FgaSubject.client("tenant-provisioning");
    FgaSubject outsider = FgaSubject.user(UUID.randomUUID());
    FgaObject platform = FgaObject.of(PLATFORM, "penvire");

    client.write(
        FgaTuple.of(adminUser, ADMIN, platform), FgaTuple.of(serviceClient, ADMIN, platform));

    assertTrue(client.check(adminUser, CAN_MANAGE_ACCOUNTS, platform));
    assertTrue(client.check(serviceClient, CAN_MANAGE_ACCOUNTS, platform));
    assertFalse(client.check(outsider, CAN_MANAGE_ACCOUNTS, platform));

    assertDoesNotThrow(() -> client.require(adminUser, CAN_MANAGE_ACCOUNTS, platform));
    assertThrows(
        ForbiddenException.class, () -> client.require(outsider, CAN_MANAGE_ACCOUNTS, platform));
  }

  @Test
  void writesAndDeletesAreIdempotentAndAtomic() {
    FgaSubject user = FgaSubject.user(UUID.randomUUID());
    FgaObject tenant = FgaObject.of(TENANT, UUID.randomUUID());
    FgaTuple member = FgaTuple.of(user, MEMBER, tenant);
    FgaTuple admin = FgaTuple.of(user, ADMIN, tenant);

    client.write(member);
    client.write(member);
    assertTrue(client.check(user, MEMBER, tenant));
    assertFalse(client.check(user, ADMIN, tenant));

    client.writeAndDelete(List.of(admin), List.of(member));
    assertTrue(client.check(user, ADMIN, tenant));
    assertTrue(client.check(user, MEMBER, tenant));

    client.delete(admin);
    client.delete(admin);
    assertFalse(client.check(user, ADMIN, tenant));
    assertFalse(client.check(user, MEMBER, tenant));
  }

  @Test
  void supportsDynamicRoleUsersetsReadAndListObjects() {
    FgaSubject alice = FgaSubject.user(UUID.randomUUID());
    FgaObject tenant = FgaObject.of(TENANT, UUID.randomUUID());
    FgaObject customRole = FgaObject.of(ROLE, UUID.randomUUID());
    FgaSubject roleAssignees = FgaSubject.userset(customRole, ASSIGNEE);
    FgaObject eng1 = FgaObject.of(ENGAGEMENT, UUID.randomUUID());
    FgaObject eng2 = FgaObject.of(ENGAGEMENT, UUID.randomUUID());

    FgaTuple roleGrant = FgaTuple.of(roleAssignees, MEMBER, tenant);
    client.write(
        FgaTuple.of(alice, ASSIGNEE, customRole),
        roleGrant,
        FgaTuple.of(FgaSubject.of(tenant), TENANT_REL, eng1),
        FgaTuple.of(FgaSubject.of(tenant), TENANT_REL, eng2));

    assertEquals(Set.of(eng1, eng2), Set.copyOf(client.listObjects(alice, CAN_VIEW, ENGAGEMENT)));
    assertEquals(List.of(roleGrant), client.read(FgaTupleFilter.forObject(tenant)));
    assertEquals(List.of(roleGrant), client.read(FgaTupleFilter.forSubject(roleAssignees, TENANT)));

    // Clean up all tuples pointing to eng1 using read(forObject) + delete.
    List<FgaTuple> eng1Tuples = client.read(FgaTupleFilter.forObject(eng1));
    assertEquals(1, eng1Tuples.size());
    client.delete(eng1Tuples);
    assertEquals(List.of(eng2), client.listObjects(alice, CAN_VIEW, ENGAGEMENT));
  }
}
