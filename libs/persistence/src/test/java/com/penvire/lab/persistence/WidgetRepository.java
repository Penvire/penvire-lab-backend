package com.penvire.lab.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

/** The adapter shape a service writes: Panache by composition, the mapper, and the ID rule. */
@ApplicationScoped
class WidgetRepository extends DomainRepository<Widget, WidgetEntity, UUID> {

  private final WidgetEntityRepository entities;
  private final WidgetMapper mapper;

  WidgetRepository(WidgetEntityRepository entities, WidgetMapper mapper) {
    this.entities = entities;
    this.mapper = mapper;
  }

  @Override
  protected PanacheRepositoryBase<WidgetEntity, UUID> entities() {
    return entities;
  }

  @Override
  protected EntityMapper<Widget, WidgetEntity> mapper() {
    return mapper;
  }

  @Override
  protected UUID idOf(Widget domain) {
    return domain.id();
  }
}
