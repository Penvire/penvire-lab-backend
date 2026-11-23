package com.penvire.lab.audit;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.util.UUID;

/** Calls the use cases from an HTTP request, where an unauthenticated caller is ANONYMOUS. */
@Path("/widgets")
public class WidgetResource {

  private final WidgetService widgets;

  WidgetResource(WidgetService widgets) {
    this.widgets = widgets;
  }

  @POST
  @Path("/{id}")
  public void create(@PathParam("id") UUID id) {
    widgets.create(id);
  }

  @POST
  @Path("/refused")
  public void refuse() {
    widgets.refuseBeforeCreating(new WidgetRefusedException("invalid_client"));
  }
}
