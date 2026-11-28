package com.penvire.lab.fga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penvire.lab.fga.model.FgaModel;
import com.penvire.lab.fga.model.FgaRelation;
import com.penvire.lab.fga.model.FgaType;
import com.penvire.lab.fga.model.FgaValidation;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * Parsed view of an OpenFGA authorization model JSON for validating service {@link FgaType} and
 * {@link FgaRelation} enums against the pinned contract without starting Docker.
 *
 * <p>Use {@link #bundled()} to validate service enums against {@link FgaModel#json()} in unit
 * tests.
 */
public final class FgaModelSchema {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final String schemaVersion;
  private final Map<String, Map<String, Set<DirectSubjectSpec>>> definitions;

  private FgaModelSchema(
      String schemaVersion, Map<String, Map<String, Set<DirectSubjectSpec>>> definitions) {
    this.schemaVersion = schemaVersion;
    this.definitions = definitions;
  }

  /** Returns the cached schema parsed from the bundled {@link FgaModel#json()}. */
  public static FgaModelSchema bundled() {
    return Bundled.SCHEMA;
  }

  /** Parses a compiled OpenFGA authorization model JSON document. */
  public static FgaModelSchema parse(String modelJson) {
    Objects.requireNonNull(modelJson, "modelJson");
    if (modelJson.isBlank()) {
      throw new IllegalArgumentException("modelJson must not be blank");
    }

    JsonNode root;
    try {
      root = OBJECT_MAPPER.readTree(modelJson);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid OpenFGA model JSON", e);
    }

    if (root == null || !root.isObject()) {
      throw new IllegalArgumentException("OpenFGA model JSON must be an object");
    }

    String schemaVersion = root.path("schema_version").asText("");
    if (schemaVersion.isBlank()) {
      throw new IllegalArgumentException("OpenFGA model JSON missing 'schema_version'");
    }

    JsonNode typeDefs = root.get("type_definitions");
    if (typeDefs == null || !typeDefs.isArray()) {
      throw new IllegalArgumentException("OpenFGA model JSON missing 'type_definitions' array");
    }

    Map<String, Map<String, Set<DirectSubjectSpec>>> parsedTypes = new LinkedHashMap<>();
    for (JsonNode typeNode : typeDefs) {
      String typeName = typeNode.path("type").asText("");
      if (typeName.isBlank()) {
        throw new IllegalArgumentException("Type definition missing 'type' name");
      }
      Map<String, Set<DirectSubjectSpec>> relations = parseRelations(typeNode);
      parsedTypes.put(typeName, Map.copyOf(relations));
    }

    return new FgaModelSchema(schemaVersion, Map.copyOf(parsedTypes));
  }

  /** Schema version declared in the model (for example {@code 1.2}). */
  public String schemaVersion() {
    return schemaVersion;
  }

  /** Unmodifiable set of all type names defined in the model. */
  public Set<String> types() {
    return definitions.keySet();
  }

  /** Returns {@code true} if {@code type} is defined in the model. */
  public boolean hasType(FgaType type) {
    String name = FgaValidation.requireType(Objects.requireNonNull(type, "type").fgaType());
    return definitions.containsKey(name);
  }

  /** Unmodifiable set of relation names declared on {@code type}, or empty if unknown. */
  public Set<String> relationsOf(FgaType type) {
    String name = FgaValidation.requireType(Objects.requireNonNull(type, "type").fgaType());
    Map<String, Set<DirectSubjectSpec>> relations = definitions.get(name);
    return relations == null ? Set.of() : relations.keySet();
  }

  /** Returns {@code true} if {@code relation} is declared on {@code type}. */
  public boolean hasRelation(FgaType type, FgaRelation relation) {
    String typeName = FgaValidation.requireType(Objects.requireNonNull(type, "type").fgaType());
    String relationName =
        FgaValidation.requireRelation(Objects.requireNonNull(relation, "relation").fgaRelation());
    Map<String, Set<DirectSubjectSpec>> relations = definitions.get(typeName);
    return relations != null && relations.containsKey(relationName);
  }

  /**
   * Returns the formatted direct subject types allowed for stored tuples on {@code type#relation}
   * (for example {@code user}, {@code role#assignee}, or {@code user:*}). Computed-only relations
   * return an empty set.
   */
  public Set<String> directlyAssignableTypes(FgaType type, FgaRelation relation) {
    String typeName = FgaValidation.requireType(Objects.requireNonNull(type, "type").fgaType());
    String relationName =
        FgaValidation.requireRelation(Objects.requireNonNull(relation, "relation").fgaRelation());
    Map<String, Set<DirectSubjectSpec>> relations = definitions.get(typeName);
    if (relations == null) {
      return Set.of();
    }
    Set<DirectSubjectSpec> specs = relations.get(relationName);
    if (specs == null || specs.isEmpty()) {
      return Set.of();
    }
    Set<String> formatted = new LinkedHashSet<>();
    for (DirectSubjectSpec spec : specs) {
      formatted.add(spec.formatted());
    }
    return Set.copyOf(formatted);
  }

  /** Asserts that every {@link FgaType} constant of {@code enumClass} exists in the model. */
  public <E extends Enum<E> & FgaType> void assertTypesExist(Class<E> enumClass) {
    assertTypesExist(List.of(requireEnumConstants(enumClass)));
  }

  /** Asserts that every type in {@code types} exists in the model. */
  public void assertTypesExist(FgaType... types) {
    assertTypesExist(List.of(Objects.requireNonNull(types, "types")));
  }

  /** Asserts that every type in {@code types} exists in the model. */
  public void assertTypesExist(Collection<? extends FgaType> types) {
    Objects.requireNonNull(types, "types");
    Set<String> missing = new TreeSet<>();
    for (FgaType type : types) {
      String name = FgaValidation.requireType(Objects.requireNonNull(type, "type").fgaType());
      if (!definitions.containsKey(name)) {
        missing.add(name);
      }
    }
    if (!missing.isEmpty()) {
      throw new AssertionError(
          "Unknown OpenFGA type(s) "
              + missing
              + "; defined types: "
              + new TreeSet<>(definitions.keySet()));
    }
  }

  /**
   * Asserts that every {@link FgaRelation} constant of {@code enumClass} is declared on {@code
   * objectType}.
   */
  public <E extends Enum<E> & FgaRelation> void assertRelationsExist(
      FgaType objectType, Class<E> enumClass) {
    assertRelationsExist(objectType, List.of(requireEnumConstants(enumClass)));
  }

  /** Asserts that every relation in {@code relations} is declared on {@code objectType}. */
  public void assertRelationsExist(FgaType objectType, FgaRelation... relations) {
    assertRelationsExist(objectType, List.of(Objects.requireNonNull(relations, "relations")));
  }

  /** Asserts that every relation in {@code relations} is declared on {@code objectType}. */
  public void assertRelationsExist(
      FgaType objectType, Collection<? extends FgaRelation> relations) {
    Objects.requireNonNull(relations, "relations");
    String typeName = requireDefinedTypeName(objectType);
    Set<String> defined = definitions.getOrDefault(typeName, Map.of()).keySet();

    Set<String> missing = new TreeSet<>();
    for (FgaRelation relation : relations) {
      String relName =
          FgaValidation.requireRelation(Objects.requireNonNull(relation, "relation").fgaRelation());
      if (!defined.contains(relName)) {
        missing.add(relName);
      }
    }
    if (!missing.isEmpty()) {
      throw new AssertionError(
          "Unknown OpenFGA relation(s) on type '"
              + typeName
              + "': "
              + missing
              + "; defined relations: "
              + new TreeSet<>(defined));
    }
  }

  /**
   * Asserts that {@code enumClass} declares the exact set of relations defined on {@code
   * objectType} (neither missing nor extra).
   */
  public <E extends Enum<E> & FgaRelation> void assertExactRelations(
      FgaType objectType, Class<E> enumClass) {
    assertExactRelations(objectType, List.of(requireEnumConstants(enumClass)));
  }

  /**
   * Asserts that {@code relations} matches the exact set of relations defined on {@code objectType}
   * (neither missing nor extra).
   */
  public void assertExactRelations(FgaType objectType, FgaRelation... relations) {
    assertExactRelations(objectType, List.of(Objects.requireNonNull(relations, "relations")));
  }

  /**
   * Asserts that {@code relations} matches the exact set of relations defined on {@code objectType}
   * (neither missing nor extra).
   */
  public void assertExactRelations(
      FgaType objectType, Collection<? extends FgaRelation> relations) {
    Objects.requireNonNull(relations, "relations");
    String typeName = requireDefinedTypeName(objectType);
    Set<String> defined = definitions.getOrDefault(typeName, Map.of()).keySet();

    Set<String> declared = new TreeSet<>();
    for (FgaRelation relation : relations) {
      declared.add(
          FgaValidation.requireRelation(
              Objects.requireNonNull(relation, "relation").fgaRelation()));
    }

    Set<String> unknown = new TreeSet<>(declared);
    unknown.removeAll(defined);

    Set<String> unmapped = new TreeSet<>(defined);
    unmapped.removeAll(declared);

    if (!unknown.isEmpty() || !unmapped.isEmpty()) {
      throw new AssertionError(
          "Relations mismatch on OpenFGA type '"
              + typeName
              + "': unknown="
              + unknown
              + ", unmapped="
              + unmapped);
    }
  }

  /**
   * Asserts that {@code objectType#relation} accepts direct stored tuples for {@code subjectType}
   * (not computed-only and not userset-only).
   */
  public void assertDirectlyAssignable(
      FgaType objectType, FgaRelation relation, FgaType subjectType) {
    String subjectTypeName =
        FgaValidation.requireType(Objects.requireNonNull(subjectType, "subjectType").fgaType());
    assertDirectSubjectSpec(
        objectType, relation, new DirectSubjectSpec(subjectTypeName, null, false));
  }

  /**
   * Asserts that {@code objectType#relation} accepts direct stored tuples for userset subjects of
   * the form {@code subjectType:<id>#subjectRelation}.
   */
  public void assertDirectlyAssignable(
      FgaType objectType, FgaRelation relation, FgaType subjectType, FgaRelation subjectRelation) {
    String subjectTypeName =
        FgaValidation.requireType(Objects.requireNonNull(subjectType, "subjectType").fgaType());
    String subjectRelationName =
        FgaValidation.requireRelation(
            Objects.requireNonNull(subjectRelation, "subjectRelation").fgaRelation());
    assertDirectSubjectSpec(
        objectType, relation, new DirectSubjectSpec(subjectTypeName, subjectRelationName, false));
  }

  private void assertDirectSubjectSpec(
      FgaType objectType, FgaRelation relation, DirectSubjectSpec expected) {
    String typeName = requireDefinedTypeName(objectType);
    String relName =
        FgaValidation.requireRelation(Objects.requireNonNull(relation, "relation").fgaRelation());

    Map<String, Set<DirectSubjectSpec>> relations = definitions.getOrDefault(typeName, Map.of());
    if (!relations.containsKey(relName)) {
      throw new AssertionError(
          "Unknown OpenFGA relation '"
              + relName
              + "' on type '"
              + typeName
              + "'; defined relations: "
              + new TreeSet<>(relations.keySet()));
    }

    Set<DirectSubjectSpec> allowed = relations.getOrDefault(relName, Set.of());
    if (!allowed.contains(expected)) {
      Set<String> allowedFormatted = new TreeSet<>();
      for (DirectSubjectSpec spec : allowed) {
        allowedFormatted.add(spec.formatted());
      }
      throw new AssertionError(
          "OpenFGA relation '"
              + typeName
              + "#"
              + relName
              + "' does not allow direct subject '"
              + expected.formatted()
              + "'; allowed direct subjects: "
              + allowedFormatted);
    }
  }

  private String requireDefinedTypeName(FgaType type) {
    String typeName = FgaValidation.requireType(Objects.requireNonNull(type, "type").fgaType());
    if (!definitions.containsKey(typeName)) {
      throw new AssertionError(
          "Unknown OpenFGA type '"
              + typeName
              + "'; defined types: "
              + new TreeSet<>(definitions.keySet()));
    }
    return typeName;
  }

  private static <E extends Enum<E>> E[] requireEnumConstants(Class<E> enumClass) {
    E[] constants = Objects.requireNonNull(enumClass, "enumClass").getEnumConstants();
    if (constants == null || constants.length == 0) {
      throw new IllegalArgumentException(enumClass.getName() + " declares no enum constants");
    }
    return constants;
  }

  private static Map<String, Set<DirectSubjectSpec>> parseRelations(JsonNode typeNode) {
    Map<String, Set<DirectSubjectSpec>> relations = new LinkedHashMap<>();
    JsonNode relationsNode = typeNode.get("relations");
    if (relationsNode == null || !relationsNode.isObject()) {
      return relations;
    }

    JsonNode metadataRelations = typeNode.path("metadata").path("relations");
    relationsNode
        .fieldNames()
        .forEachRemaining(relationName -> {
          Set<DirectSubjectSpec> directSpecs =
              parseDirectSubjectSpecs(metadataRelations.path(relationName));
          relations.put(relationName, directSpecs);
        });
    return relations;
  }

  private static Set<DirectSubjectSpec> parseDirectSubjectSpecs(JsonNode relationMeta) {
    JsonNode directTypes = relationMeta.get("directly_related_user_types");
    if (directTypes == null || !directTypes.isArray()) {
      return Set.of();
    }

    Set<DirectSubjectSpec> specs = new LinkedHashSet<>();
    for (JsonNode entry : directTypes) {
      String subjectType = entry.path("type").asText("");
      if (subjectType.isBlank()) {
        continue;
      }
      String subjectRelation = entry.path("relation").asText("");
      boolean wildcard = entry.has("wildcard") && entry.get("wildcard").isObject();
      specs.add(
          new DirectSubjectSpec(
              subjectType, subjectRelation.isBlank() ? null : subjectRelation, wildcard));
    }
    return Set.copyOf(specs);
  }

  private record DirectSubjectSpec(String type, @Nullable String relation, boolean wildcard) {
    String formatted() {
      if (wildcard) {
        return type + ":*";
      }
      return relation == null ? type : type + "#" + relation;
    }
  }

  private static final class Bundled {
    private static final FgaModelSchema SCHEMA = FgaModelSchema.parse(FgaModel.json());
  }
}
