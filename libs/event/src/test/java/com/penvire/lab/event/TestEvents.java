package com.penvire.lab.event;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecordBase;
import org.jspecify.annotations.Nullable;

/**
 * Hand-written stand-ins for classes generated from a contract package. Their revisions are in
 * src/test/resources/avro. Each event is its own class because SchemaCatalog caches per class.
 */
public final class TestEvents {

  private TestEvents() {}

  /** Matches its latest revision 2. Strings carry avro.java.string, like generated classes. */
  public static final class WidgetCreated extends TestRecord {
    public static final Schema SCHEMA =
        parse(
            """
            {"type": "record", "name": "WidgetCreated", "namespace": "dev.penvire.lab.widget.events.v1",
             "fields": [
               {"name": "widgetId", "type": {"type": "string", "avro.java.string": "String"}},
               {"name": "colour", "type": ["null", {"type": "string", "avro.java.string": "String"}],
                "default": null}]}
            """);

    public WidgetCreated(String widgetId, @Nullable String colour) {
      super(widgetId, colour);
    }

    @Override
    public Schema getSchema() {
      return SCHEMA;
    }
  }

  /** Built from revision 1 although the package ships revision 2: a classpath mix-up. */
  public static final class WidgetStale extends TestRecord {
    public static final Schema SCHEMA =
        parse(
            """
            {"type": "record", "name": "WidgetStale", "namespace": "dev.penvire.lab.widget.events.v1",
             "fields": [{"name": "widgetId", "type": "string"}]}
            """);

    public WidgetStale(String widgetId) {
      super(widgetId);
    }

    @Override
    public Schema getSchema() {
      return SCHEMA;
    }
  }

  /** No revision of it is on the classpath. */
  public static final class WidgetUnshipped extends TestRecord {
    public static final Schema SCHEMA =
        parse(
            """
            {"type": "record", "name": "WidgetUnshipped", "namespace": "dev.penvire.lab.widget.events.v1",
             "fields": [{"name": "widgetId", "type": "string"}]}
            """);

    public WidgetUnshipped(String widgetId) {
      super(widgetId);
    }

    @Override
    public Schema getSchema() {
      return SCHEMA;
    }
  }

  /** Positional fields, the way Avro's generated classes expose them. */
  abstract static class TestRecord extends SpecificRecordBase {
    private final @Nullable Object[] values;

    TestRecord(@Nullable Object... values) {
      this.values = values.clone();
    }

    @Override
    public @Nullable Object get(int field) {
      return values[field];
    }

    @Override
    public void put(int field, @Nullable Object value) {
      values[field] = value;
    }
  }

  private static Schema parse(String json) {
    return new Schema.Parser().parse(json);
  }
}
