package com.penvire.lab.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "widget")
@SuppressWarnings("NullAway.Init") // Hibernate fills the fields after the no-arg constructor.
class WidgetEntity {

  @Id UUID id;

  String name;
}
