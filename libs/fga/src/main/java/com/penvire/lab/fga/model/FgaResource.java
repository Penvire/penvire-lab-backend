package com.penvire.lab.fga.model;

/**
 * Seam for domain resources (tenants, engagements, evidence) that map to an OpenFGA {@link
 * FgaObject}.
 */
@FunctionalInterface
public interface FgaResource {

  /** Converts this resource into its validated OpenFGA object representation. */
  FgaObject toFgaObject();
}
