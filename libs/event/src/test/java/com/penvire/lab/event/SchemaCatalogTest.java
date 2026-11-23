package com.penvire.lab.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.penvire.lab.widget.events.v1.WidgetCreated;
import dev.penvire.lab.widget.events.v1.WidgetStale;
import dev.penvire.lab.widget.events.v1.WidgetUnshipped;
import org.junit.jupiter.api.Test;

class SchemaCatalogTest {

  private final SchemaCatalog catalog = new SchemaCatalog();

  @Test
  void producersWriteTheLatestShippedRevision() {
    SchemaRevision revision = catalog.latestOf(new WidgetCreated("w-1", null));

    assertEquals(2, revision.revision());
    assertEquals(
        "urn:penvire-lab:schema:dev.penvire.lab.widget.events.v1.WidgetCreated:2",
        revision.dataSchema().urn());
  }

  @Test
  void looksUpEachClassOnce() {
    SchemaRevision first = catalog.latestOf(new WidgetCreated("w-1", null));
    SchemaRevision second = catalog.latestOf(new WidgetCreated("w-2", "red"));

    assertSame(first, second);
  }

  @Test
  void rejectsAClassThatDoesNotMatchItsLatestRevision() {
    var stale = new WidgetStale("w-1");

    var e = assertThrows(IllegalStateException.class, () -> catalog.latestOf(stale));
    assertTrue(
        String.valueOf(e.getMessage()).contains("does not match its latest revision 2"),
        e.getMessage());
  }

  @Test
  void rejectsAClassWithoutShippedRevisions() {
    var unshipped = new WidgetUnshipped("w-1");

    var e = assertThrows(IllegalStateException.class, () -> catalog.latestOf(unshipped));
    assertTrue(String.valueOf(e.getMessage()).contains("No schema revisions"), e.getMessage());
  }

  @Test
  void consumersReadWithTheRevisionAnEventNames() {
    SchemaRevision writer =
        catalog.writerOf(new DataSchema(TestCloudEvents.WIDGET_CREATED, 1), WidgetCreated.class);

    assertEquals(1, writer.revision());
    assertEquals(1, writer.schema().getFields().size(), "revision 1 has no colour yet");
  }

  @Test
  void looksUpEachWriterRevisionOnce() {
    DataSchema dataSchema = new DataSchema(TestCloudEvents.WIDGET_CREATED, 2);

    assertSame(
        catalog.writerOf(dataSchema, WidgetCreated.class),
        catalog.writerOf(dataSchema, WidgetCreated.class));
  }

  @Test
  void rejectsARevisionTheConsumersPackageDoesNotShip() {
    DataSchema newer = new DataSchema(TestCloudEvents.WIDGET_CREATED, 3);

    var e =
        assertThrows(
            IllegalStateException.class, () -> catalog.writerOf(newer, WidgetCreated.class));
    assertTrue(String.valueOf(e.getMessage()).contains("upgrade"), e.getMessage());
  }
}
