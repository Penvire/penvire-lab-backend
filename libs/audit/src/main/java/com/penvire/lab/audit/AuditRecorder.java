package com.penvire.lab.audit;

import com.penvire.lab.event.EventType;
import com.penvire.lab.event.outbox.EventPublisher;
import io.quarkus.runtime.Startup;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificRecordBase;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jspecify.annotations.Nullable;

/**
 * Builds the service's own {@code AuditRecorded} from a frame and writes it to {@code
 * <service>.audit} through the outbox. Loaded at startup, so a service without its contract package
 * fails at boot instead of on the first audited call.
 */
@Startup
@Singleton
class AuditRecorder {

  private static final Pattern ACTION =
      Pattern.compile("([a-z][a-z0-9-]*)\\.([a-z][a-z0-9-]*)\\.([a-z][a-z0-9-]*)");

  private final EventPublisher publisher;
  private final CallerResolver callers;

  private final StateChanges states;
  private final Schema changeSchema;

  private final SpecificData data;
  private final Schema schema;
  private final String service;
  private final String topic;

  AuditRecorder(
      EventPublisher publisher,
      CallerResolver callers,
      StateChanges states,
      @ConfigProperty(name = "penvire.audit.service") String service) {
    Class<? extends SpecificRecordBase> type = recordClass(service);
    this.callers = callers;
    this.publisher = publisher;

    this.states = states;

    this.data = SpecificData.getForClass(type);
    this.schema = data.getSchema(type);
    this.service = EventType.of(schema).service();
    this.topic = this.service + ".audit";

    this.changeSchema = schema.getField("changes").schema().getElementType();
  }

  /**
   * Writes the frame's audit event to the outbox in the current transaction.
   *
   * @param reason the reason code for {@code DENIED} and {@code FAILED}; null for {@code SUCCESS}
   * @throws IllegalArgumentException if the action is not {@code <service>.<resource>.<verb>} of
   *     this service
   */
  void record(AuditScope.Frame frame, Outcome outcome, @Nullable String reason) {
    String targetType = targetTypeOf(frame.action);
    AuditActor explicit = frame.actor;
    AuditActor actor = explicit != null ? explicit : callers.caller();
    String targetId = frame.targetId;

    SpecificRecordBase event = (SpecificRecordBase) data.newRecord(null, schema);
    event.put("action", frame.action);
    event.put("outcome", symbol("outcome", outcome.name()));
    event.put("actorType", symbol("actorType", actor.type().name()));
    event.put("actorId", actor.id());
    event.put("clientId", actor.clientId());
    event.put("targetType", targetType);
    event.put("targetId", targetId);
    event.put("reason", reason);

    event.put("changes", outcome == Outcome.SUCCESS ? changes(frame) : List.of());

    String subject = targetId != null ? targetId : actor.id();
    if (subject != null) {
      publisher.publish(topic, subject, event);
    } else {
      publisher.publishKeyedById(topic, null, event);
    }
  }

  /**
   * The service's {@code AuditChange} records for the state the use case reported; none if it
   * reported none. A refused or failed action changed nothing, so only {@code SUCCESS} has changes.
   */
  private List<Object> changes(AuditScope.Frame frame) {
    if (!frame.stateReported) {
      return List.of();
    }
    List<Object> changes = new ArrayList<>();
    for (StateChanges.Change change : states.between(frame.audited, frame.before, frame.after)) {
      SpecificRecordBase record = (SpecificRecordBase) data.newRecord(null, changeSchema);
      record.put("fieldPath", change.path());
      record.put("oldValue", change.oldValue());
      record.put("newValue", change.newValue());
      changes.add(record);
    }
    return changes;
  }

  /** The resource of {@code <service>.<resource>.<verb>}, after checking the service is ours. */
  private String targetTypeOf(String action) {
    Matcher matcher = ACTION.matcher(action);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(action + " is not <service>.<resource>.<verb>");
    }
    if (!matcher.group(1).equals(service)) {
      throw new IllegalArgumentException(
          action + " belongs to " + matcher.group(1) + ", not " + service);
    }
    return matcher.group(2);
  }

  /** The generated enum constant, for example the service's {@code AuditOutcome.SUCCESS}. */
  private Object symbol(String field, String name) {
    return data.createEnum(name, schema.getField(field).schema());
  }

  /**
   * The service's generated {@code AuditRecorded}, loaded by name: the library cannot depend on any
   * one service's contract package.
   */
  private static Class<? extends SpecificRecordBase> recordClass(String service) {
    String name = "dev.penvire.lab." + service + ".events.v1.AuditRecorded";
    try {
      return Class.forName(name, false, AuditRecorder.class.getClassLoader())
          .asSubclass(SpecificRecordBase.class);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(
          name + " is not on the classpath; add the " + service + " events contract package", e);
    }
  }
}
