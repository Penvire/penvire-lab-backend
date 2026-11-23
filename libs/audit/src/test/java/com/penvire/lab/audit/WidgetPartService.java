package com.penvire.lab.audit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.UUID;

/** An audited use case that another audited use case calls. */
@ApplicationScoped
public class WidgetPartService {

  private final AuditScope audit;

  WidgetPartService(AuditScope audit) {
    this.audit = audit;
  }

  @Audited("widget.part.attach")
  @Transactional
  public void attach(UUID partId) {
    audit.target(partId.toString());
  }
}
