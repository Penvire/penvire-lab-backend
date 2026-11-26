package com.penvire.lab.identity.application.port.out.account;

import com.penvire.lab.identity.domain.account.PasswordCredential;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasswordCredentialPersistencePort {

  PasswordCredential insert(PasswordCredential credential);

  Optional<PasswordCredential> findActiveByAccountId(UUID accountId);

  List<PasswordCredential> findRecentByAccountId(UUID accountId, int limit);

  void changePassword(UUID accountId, PasswordCredential newCredential, Instant now);
}
