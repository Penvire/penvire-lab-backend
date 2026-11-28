package com.penvire.lab.fga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penvire.lab.fga.model.FgaModel;
import com.penvire.lab.fga.model.FgaObject;
import com.penvire.lab.fga.model.FgaRelation;
import com.penvire.lab.fga.model.FgaSubject;
import com.penvire.lab.fga.model.FgaTuple;
import com.penvire.lab.fga.model.FgaType;
import io.quarkus.security.ForbiddenException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FgaModelContractIntegrationTest {

  private static final FgaType PLATFORM = FgaType.of("platform");
  private static final FgaObject PLATFORM_GLOBAL = FgaObject.of(PLATFORM, "global");

  private static final FgaRelation ADMIN = FgaRelation.of("admin");
  private static final FgaRelation ACCOUNT_READ = FgaRelation.of("account_read");
  private static final FgaRelation ACCOUNT_START_RECOVERY =
      FgaRelation.of("account_start_recovery");
  private static final FgaRelation ACCOUNT_SET_STATUS = FgaRelation.of("account_set_status");
  private static final FgaRelation ACCOUNT_EDIT_DETAILS = FgaRelation.of("account_edit_details");
  private static final FgaRelation ACCOUNT_DELETE = FgaRelation.of("account_delete");
  private static final FgaRelation MANAGE_PLATFORM_GRANTS =
      FgaRelation.of("manage_platform_grants");

  private static final List<FgaRelation> ACCOUNT_ACTIONS =
      List.of(
          ACCOUNT_READ,
          ACCOUNT_START_RECOVERY,
          ACCOUNT_SET_STATUS,
          ACCOUNT_EDIT_DETAILS,
          ACCOUNT_DELETE);

  private static OpenFgaTestServer openfga;

  @BeforeAll
  static void startOpenFga() {
    openfga = OpenFgaTestServer.start();
  }

  @AfterAll
  static void stopOpenFga() {
    if (openfga != null) {
      openfga.close();
    }
  }

  @Test
  void exposesPinnedFgaModelMetadataAndCompiledSchema() throws Exception {
    assertEquals("0.1.0", FgaModel.version());
    assertTrue(openfga.apiUrl().toString().startsWith("http://"));
    assertFalse(openfga.storeId().isBlank());
    assertFalse(openfga.authorizationModelId().isBlank());
    assertEquals(openfga.storeId(), openfga.config().storeId());

    JsonNode model = new ObjectMapper().readTree(FgaModel.json());
    assertEquals("1.2", model.path("schema_version").asText());

    Set<String> types =
        StreamSupport.stream(model.path("type_definitions").spliterator(), false)
            .map(node -> node.path("type").asText())
            .collect(Collectors.toSet());
    assertTrue(types.containsAll(Set.of("user", "client", "platform")));
  }

  @Test
  void adminHoldsAllAccountActionsAndManagePlatformGrantsWhileOperatorCannotManageGrants() {
    OpenFgaTestServer.ProvisionedStore store = openfga.createStore();
    assertNotEquals(openfga.storeId(), store.storeId());
    FgaClient client = store.client();

    FgaSubject admin = FgaSubject.user(UUID.randomUUID());
    FgaSubject operator = FgaSubject.user(UUID.randomUUID());

    client.write(FgaTuple.of(admin, ADMIN, PLATFORM_GLOBAL));
    client.write(
        ACCOUNT_ACTIONS.stream()
            .map(relation -> FgaTuple.of(operator, relation, PLATFORM_GLOBAL))
            .toList());

    for (FgaRelation action : ACCOUNT_ACTIONS) {
      assertTrue(client.check(admin, action, PLATFORM_GLOBAL), "admin should hold " + action);
      assertTrue(client.check(operator, action, PLATFORM_GLOBAL), "operator should hold " + action);
    }

    assertTrue(client.check(admin, MANAGE_PLATFORM_GRANTS, PLATFORM_GLOBAL));
    assertFalse(client.check(operator, MANAGE_PLATFORM_GRANTS, PLATFORM_GLOBAL));
    assertThrows(
        ForbiddenException.class,
        () -> client.require(operator, MANAGE_PLATFORM_GRANTS, PLATFORM_GLOBAL));
  }

  @Test
  void eachIndividualPlatformAccountGrantAuthorizesOnlyItsOwnAction() {
    FgaClient client = openfga.createStore().client();

    for (FgaRelation granted : ACCOUNT_ACTIONS) {
      FgaSubject holder = FgaSubject.user(UUID.randomUUID());
      FgaTuple tuple = FgaTuple.of(holder, granted, PLATFORM_GLOBAL);
      client.write(tuple);

      for (FgaRelation candidate : ACCOUNT_ACTIONS) {
        assertEquals(
            candidate.fgaRelation().equals(granted.fgaRelation()),
            client.check(holder, candidate, PLATFORM_GLOBAL),
            "grant " + granted.fgaRelation() + " checked against " + candidate.fgaRelation());
      }
      assertFalse(client.check(holder, MANAGE_PLATFORM_GRANTS, PLATFORM_GLOBAL));

      client.delete(tuple);
      assertFalse(client.check(holder, granted, PLATFORM_GLOBAL));
    }
  }

  @Test
  void rejectsClientPrincipalsAndDirectWritesToComputedPlatformGrantRelation() {
    FgaClient client = openfga.createStore().client();

    String sharedId = UUID.randomUUID().toString();
    FgaSubject adminUser = FgaSubject.user(sharedId);
    FgaSubject sameIdClient = FgaSubject.client(sharedId);
    FgaSubject stranger = FgaSubject.user(UUID.randomUUID());

    client.write(FgaTuple.of(adminUser, ADMIN, PLATFORM_GLOBAL));

    for (FgaRelation relation : ACCOUNT_ACTIONS) {
      assertFalse(client.check(sameIdClient, relation, PLATFORM_GLOBAL));
      assertFalse(client.check(stranger, relation, PLATFORM_GLOBAL));
    }
    assertFalse(client.check(sameIdClient, MANAGE_PLATFORM_GRANTS, PLATFORM_GLOBAL));
    assertFalse(client.check(stranger, MANAGE_PLATFORM_GRANTS, PLATFORM_GLOBAL));

    FgaException clientGrantError =
        assertThrows(
            FgaException.class,
            () -> client.write(FgaTuple.of(sameIdClient, ADMIN, PLATFORM_GLOBAL)));
    assertFalse(clientGrantError instanceof FgaUnavailableException);

    FgaException computedRelationError =
        assertThrows(
            FgaException.class,
            () -> client.write(FgaTuple.of(stranger, MANAGE_PLATFORM_GRANTS, PLATFORM_GLOBAL)));
    assertFalse(computedRelationError instanceof FgaUnavailableException);
  }
}
