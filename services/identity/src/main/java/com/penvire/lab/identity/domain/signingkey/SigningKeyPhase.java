package com.penvire.lab.identity.domain.signingkey;

/** Where a signing key stands at a given instant; derived from its lifetime, never stored. */
public enum SigningKeyPhase {
  /** Published, or about to be, but not signing yet. */
  PENDING,
  /** Signs new tokens. */
  SIGNING,
  /** Signs nothing, but stays published while its tokens are valid. */
  RETIRED,
  /** Past its publication; its row can be deleted. */
  EXPIRED,
  /** Revoked before it expired; its tokens no longer verify. */
  REVOKED
}
