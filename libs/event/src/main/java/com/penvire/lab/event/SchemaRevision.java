package com.penvire.lab.event;

import org.apache.avro.Schema;

/** One revision of an event schema, as shipped in its contract package (ADR 0004). */
public record SchemaRevision(Schema schema, int revision) {

  /**
   * The CloudEvents {@code dataschema} attribute, e.g. {@code urn:penvire-lab:schema:<full
   * name>:2}.
   */
  public DataSchema dataSchema() {
    return new DataSchema(schema.getFullName(), revision);
  }
}
