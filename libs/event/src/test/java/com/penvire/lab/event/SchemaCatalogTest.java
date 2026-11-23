package com.penvire.lab.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SchemaCatalogTest {

  private final SchemaCatalog catalog = new SchemaCatalog();

  @Test
  void producersWriteTheLatestShippedRevision() {
    SchemaRevision revision = catalog.latestOf(new TestEvents.WidgetCreated("w-1", null));

    assertEquals(2, revision.revision());
    assertEquals(
        "urn:penvire-lab:schema:dev.penvire.lab.widget.events.v1.WidgetCreated:2",
        revision.dataSchema());
  }

  @Test
  void looksUpEachClassOnce() {
    SchemaRevision first = catalog.latestOf(new TestEvents.WidgetCreated("w-1", null));
    SchemaRevision second = catalog.latestOf(new TestEvents.WidgetCreated("w-2", "red"));

    assertSame(first, second);
  }

  @Test
  void rejectsAClassThatDoesNotMatchItsLatestRevision() {
    var stale = new TestEvents.WidgetStale("w-1");

    var e = assertThrows(IllegalStateException.class, () -> catalog.latestOf(stale));
    assertTrue(
        String.valueOf(e.getMessage()).contains("does not match its latest revision 2"),
        e.getMessage());
  }

  @Test
  void rejectsAClassWithoutShippedRevisions() {
    var unshipped = new TestEvents.WidgetUnshipped("w-1");

    var e = assertThrows(IllegalStateException.class, () -> catalog.latestOf(unshipped));
    assertTrue(String.valueOf(e.getMessage()).contains("No schema revisions"), e.getMessage());
  }
}
