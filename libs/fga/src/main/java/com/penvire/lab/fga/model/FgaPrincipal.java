package com.penvire.lab.fga.model;

/**
 * Seam for domain principals (users, service clients, roles) that map to an OpenFGA {@link
 * FgaSubject}.
 */
@FunctionalInterface
public interface FgaPrincipal {

  /** Converts this principal into its validated OpenFGA subject representation. */
  FgaSubject toFgaSubject();
}
