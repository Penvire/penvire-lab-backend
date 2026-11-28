package com.penvire.lab.fga;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penvire.lab.fga.model.FgaRelation;
import com.penvire.lab.fga.model.FgaType;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FgaModelSchemaTest {

  private enum BundledTypes implements FgaType {
    USER("user"),
    CLIENT("client"),
    PLATFORM("platform");

    private final String value;

    BundledTypes(String value) {
      this.value = value;
    }

    @Override
    public String fgaType() {
      return value;
    }
  }

  private enum PlatformRelations implements FgaRelation {
    ADMIN("admin"),
    ACCOUNT_READ("account_read"),
    ACCOUNT_START_RECOVERY("account_start_recovery"),
    ACCOUNT_SET_STATUS("account_set_status"),
    ACCOUNT_EDIT_DETAILS("account_edit_details"),
    ACCOUNT_DELETE("account_delete"),
    MANAGE_PLATFORM_GRANTS("manage_platform_grants");

    private final String value;

    PlatformRelations(String value) {
      this.value = value;
    }

    @Override
    public String fgaRelation() {
      return value;
    }
  }

  private enum SubsetPlatformRelations implements FgaRelation {
    ADMIN("admin"),
    ACCOUNT_READ("account_read");

    private final String value;

    SubsetPlatformRelations(String value) {
      this.value = value;
    }

    @Override
    public String fgaRelation() {
      return value;
    }
  }

  private enum UnknownRelation implements FgaRelation {
    DOES_NOT_EXIST("does_not_exist");

    private final String value;

    UnknownRelation(String value) {
      this.value = value;
    }

    @Override
    public String fgaRelation() {
      return value;
    }
  }

  @Test
  void validatesServiceEnumsAgainstBundledFgaModel() {
    FgaModelSchema schema = FgaModelSchema.bundled();

    assertEquals("1.2", schema.schemaVersion());
    assertTrue(schema.hasType(BundledTypes.PLATFORM));
    assertTrue(schema.hasRelation(BundledTypes.PLATFORM, PlatformRelations.ADMIN));
    assertFalse(schema.hasType(FgaType.of("nonexistent")));
    assertFalse(schema.hasRelation(BundledTypes.PLATFORM, UnknownRelation.DOES_NOT_EXIST));

    assertDoesNotThrow(() -> schema.assertTypesExist(BundledTypes.class));
    assertDoesNotThrow(
        () -> schema.assertRelationsExist(BundledTypes.PLATFORM, SubsetPlatformRelations.class));
    assertDoesNotThrow(
        () -> schema.assertExactRelations(BundledTypes.PLATFORM, PlatformRelations.class));

    assertEquals(
        Set.of("user"),
        schema.directlyAssignableTypes(BundledTypes.PLATFORM, PlatformRelations.ADMIN));
    assertEquals(
        Set.of(),
        schema.directlyAssignableTypes(
            BundledTypes.PLATFORM, PlatformRelations.MANAGE_PLATFORM_GRANTS));

    assertDoesNotThrow(() ->
        schema.assertDirectlyAssignable(
            BundledTypes.PLATFORM, PlatformRelations.ADMIN, BundledTypes.USER));
    assertDoesNotThrow(() ->
        schema.assertDirectlyAssignable(
            BundledTypes.PLATFORM, PlatformRelations.ACCOUNT_READ, BundledTypes.USER));
  }

  @Test
  void rejectsUnknownTypesRelationsMismatchesAndDisallowedDirectAssignments() {
    FgaModelSchema schema = FgaModelSchema.bundled();

    AssertionError unknownType =
        assertThrows(
            AssertionError.class, () -> schema.assertTypesExist(FgaType.of("missing_type")));
    assertMessageContains(unknownType, "missing_type");

    AssertionError unknownRel =
        assertThrows(
            AssertionError.class,
            () -> schema.assertRelationsExist(BundledTypes.PLATFORM, UnknownRelation.class));
    assertMessageContains(unknownRel, "does_not_exist");

    AssertionError partialMismatch =
        assertThrows(
            AssertionError.class,
            () ->
                schema.assertExactRelations(BundledTypes.PLATFORM, SubsetPlatformRelations.class));
    assertMessageContains(partialMismatch, "unmapped=", "manage_platform_grants");

    AssertionError clientNotAllowed =
        assertThrows(
            AssertionError.class,
            () ->
                schema.assertDirectlyAssignable(
                    BundledTypes.PLATFORM, PlatformRelations.ADMIN, BundledTypes.CLIENT));
    assertMessageContains(clientNotAllowed, "client");

    AssertionError computedOnly =
        assertThrows(
            AssertionError.class,
            () ->
                schema.assertDirectlyAssignable(
                    BundledTypes.PLATFORM,
                    PlatformRelations.MANAGE_PLATFORM_GRANTS,
                    BundledTypes.USER));
    assertMessageContains(computedOnly, "manage_platform_grants");
  }

  @Test
  void supportsUsersetDirectAssignmentsAndRejectsMalformedJson() {
    FgaModelSchema custom =
        FgaModelSchema.parse(
            """
            {
              "schema_version": "1.1",
              "type_definitions": [
                { "type": "user" },
                { "type": "role", "relations": { "assignee": { "this": {} } } },
                {
                  "type": "tenant",
                  "relations": { "member": { "this": {} } },
                  "metadata": {
                    "relations": {
                      "member": {
                        "directly_related_user_types": [
                          { "type": "user" },
                          { "type": "role", "relation": "assignee" }
                        ]
                      }
                    }
                  }
                }
              ]
            }
            """);

    FgaType tenant = FgaType.of("tenant");
    FgaType role = FgaType.of("role");
    FgaRelation member = FgaRelation.of("member");
    FgaRelation assignee = FgaRelation.of("assignee");

    assertEquals(Set.of("user", "role#assignee"), custom.directlyAssignableTypes(tenant, member));
    assertDoesNotThrow(() -> custom.assertDirectlyAssignable(tenant, member, role, assignee));
    assertThrows(AssertionError.class, () -> custom.assertDirectlyAssignable(tenant, member, role));

    assertThrows(IllegalArgumentException.class, () -> FgaModelSchema.parse(" "));
    assertThrows(IllegalArgumentException.class, () -> FgaModelSchema.parse("not-json"));
    assertThrows(IllegalArgumentException.class, () -> FgaModelSchema.parse("{}"));
    assertThrows(
        IllegalArgumentException.class, () -> FgaModelSchema.parse("{\"schema_version\":\"1.2\"}"));
  }

  private static void assertMessageContains(Throwable error, String... fragments) {
    String message = error.getMessage();
    assertNotNull(message);
    for (String fragment : fragments) {
      assertTrue(message.contains(fragment), "Expected '" + fragment + "' in: " + message);
    }
  }
}
