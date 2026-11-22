package com.penvire.lab.persistence;

/**
 * Maps a domain object to its JPA entity and back. Implement it by hand or generate it with
 * MapStruct, using {@code @MappingTarget} on {@link #updateEntity}.
 */
public interface EntityMapper<D, E> {

  /** Creates a new, unmanaged entity for a domain object that is not stored yet. */
  E toEntity(D domain);

  D toDomain(E entity);

  /**
   * Copies the domain state into a managed entity, which Hibernate writes at commit. Never changes
   * the entity's identity.
   */
  void updateEntity(D domain, E entity);
}
