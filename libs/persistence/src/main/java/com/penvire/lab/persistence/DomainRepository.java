package com.penvire.lab.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.util.Optional;

/**
 * Base for out-adapters that store domain objects as JPA entities. Callers own the transaction;
 * every method requires one.
 */
public abstract class DomainRepository<D, E, ID> {

  /** Panache access to entities, by composition so adapters don't expose it. */
  protected abstract PanacheRepositoryBase<E, ID> entities();

  protected abstract EntityMapper<D, E> mapper();

  /** The domain object's identity; the domain assigns it, not the database. */
  protected abstract ID idOf(D domain);

  @Transactional(Transactional.TxType.MANDATORY)
  public Optional<D> findById(ID id) {
    return entities().findByIdOptional(id).map(mapper()::toDomain);
  }

  /**
   * Locks the row with {@code SELECT ... FOR UPDATE}. The lock lasts until the caller's transaction
   * commits or rolls back, so read, change and {@link #update} in that same transaction.
   */
  @Transactional(Transactional.TxType.MANDATORY)
  public Optional<D> findByIdForUpdate(ID id) {
    return entities().findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE).map(mapper()::toDomain);
  }

  /** Stores a new domain object. A duplicate ID fails at flush or commit. */
  @Transactional(Transactional.TxType.MANDATORY)
  public D insert(D domain) {
    E entity = mapper().toEntity(domain);
    entities().persist(entity);
    return mapper().toDomain(entity);
  }

  /**
   * Copies the domain state into the stored entity. Hibernate writes the change at commit, so there
   * is no persist or merge.
   *
   * @throws IllegalStateException if no row has the domain object's ID; load it first
   */
  @Transactional(Transactional.TxType.MANDATORY)
  public D update(D domain) {
    ID id = idOf(domain);
    E entity =
        entities()
            .findByIdOptional(id)
            .orElseThrow(() -> new IllegalStateException("No stored entity with ID " + id));
    mapper().updateEntity(domain, entity);
    return mapper().toDomain(entity);
  }
}
