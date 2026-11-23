package com.penvire.lab.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import jakarta.inject.Singleton;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * The fields that differ between the state before and after an action (ADR 0006). Records are
 * walked into with dotted paths; anything else, collections included, is one JSON value written
 * with the service's {@link ObjectMapper}.
 */
@Singleton
class StateChanges {

  /** One changed path; values are JSON text, both null for a path recorded without values. */
  record Change(String path, @Nullable String oldValue, @Nullable String newValue) {}

  private final ObjectMapper mapper;

  StateChanges(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /**
   * Every path of a record type, nested records and ignored fields included, for checking {@code
   * pathOnly} and {@code ignore}.
   */
  static Set<String> paths(Class<?> type) {
    Set<String> paths = new TreeSet<>();
    collectPaths("", type, paths);
    return paths;
  }

  private static void collectPaths(String prefix, Class<?> type, Set<String> paths) {
    for (RecordComponent component : type.getRecordComponents()) {
      String path = prefix + component.getName();
      paths.add(path);

      if (component.getType().isRecord()) {
        collectPaths(path + ".", component.getType(), paths);
      }
    }
  }

  /**
   * The changed fields of the action's {@link Audited#state}, sorted by path. Ignored fields are
   * left out first, then {@code pathOnly} fields lose their values. A null side compares as JSON
   * {@code null} in every field.
   */
  List<Change> between(Audited audited, @Nullable Object before, @Nullable Object after) {
    Class<?> type = audited.state();
    if (ignoredType(audited, type)) {
      return List.of();
    }

    SortedMap<String, JsonNode> old = flatten(audited, type, before);
    SortedMap<String, JsonNode> current = flatten(audited, type, after);

    List<Change> changes = new ArrayList<>();
    for (Map.Entry<String, JsonNode> entry : old.entrySet()) {
      String path = entry.getKey();
      JsonNode oldValue = entry.getValue();
      JsonNode currentValue = current.getOrDefault(path, NullNode.getInstance());

      if (oldValue.equals(currentValue)) {
        continue;
      }

      changes.add(
          covers(audited.pathOnly(), path)
              ? new Change(path, null, null)
              : new Change(path, write(oldValue), write(currentValue)));
    }

    return changes;
  }

  private SortedMap<String, JsonNode> flatten(
      Audited audited, Class<?> type, @Nullable Object value) {
    SortedMap<String, JsonNode> values = new TreeMap<>();
    flatten("", audited, type, value, values);
    return values;
  }

  private void flatten(
      String prefix,
      Audited audited,
      Class<?> type,
      @Nullable Object value,
      SortedMap<String, JsonNode> values) {
    for (RecordComponent component : type.getRecordComponents()) {
      String path = prefix + component.getName();
      Class<?> fieldType = component.getType();
      if (component.isAnnotationPresent(AuditIgnored.class)
          || ignoredType(audited, fieldType)
          || covers(audited.ignore(), path)) {
        continue;
      }
      Object fieldValue = value == null ? null : read(component, value);
      if (fieldType.isRecord()) {
        flatten(path + ".", audited, fieldType, fieldValue, values);
      } else {
        values.put(
            path, fieldValue == null ? NullNode.getInstance() : mapper.valueToTree(fieldValue));
      }
    }
  }

  private static boolean ignoredType(Audited audited, Class<?> type) {
    if (type.isAnnotationPresent(AuditIgnored.class)) {
      return true;
    }
    for (Class<?> ignored : audited.ignoreTypes()) {
      if (ignored.isAssignableFrom(type)) {
        return true;
      }
    }
    return false;
  }

  /** Whether one of the paths is this path or a record above it. */
  private static boolean covers(String[] paths, String path) {
    for (String covering : paths) {
      if (path.equals(covering) || path.startsWith(covering + ".")) {
        return true;
      }
    }
    return false;
  }

  private static @Nullable Object read(RecordComponent component, Object value) {
    Method accessor = component.getAccessor();
    try {
      accessor.setAccessible(true);
      return accessor.invoke(value);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException("Cannot read " + accessor + " for the audit changes", e);
    }
  }

  private String write(JsonNode value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot write an audit value as JSON", e);
    }
  }
}
