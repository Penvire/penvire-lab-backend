package com.penvire.lab.persistence;

import java.util.UUID;

/**
 * Creates identities for new domain objects before they are stored. Inject it into application
 * services and pass the result to the domain constructor; the domain never generates IDs itself.
 */
@FunctionalInterface
public interface IdGenerator {

  /** A new, unique, time-ordered ID. */
  UUID next();
}
