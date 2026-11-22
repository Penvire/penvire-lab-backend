package com.penvire.lab.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import org.jspecify.annotations.NonNull;

@ApplicationScoped
class WidgetMapper implements EntityMapper<Widget, WidgetEntity> {

  @Override
  public WidgetEntity toEntity(@NonNull Widget domain) {
    WidgetEntity entity = new WidgetEntity();
    entity.id = domain.id();
    entity.name = domain.name();
    return entity;
  }

  @Override
  public Widget toDomain(@NonNull WidgetEntity entity) {
    return new Widget(entity.id, entity.name);
  }

  @Override
  public void updateEntity(@NonNull Widget domain, @NonNull WidgetEntity managedEntity) {
    managedEntity.name = domain.name();
  }
}
