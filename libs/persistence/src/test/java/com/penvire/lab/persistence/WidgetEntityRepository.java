package com.penvire.lab.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

@ApplicationScoped
class WidgetEntityRepository implements PanacheRepositoryBase<WidgetEntity, UUID> {}
