package com.penvire.lab.identity.application.port.out;

import com.penvire.lab.identity.domain.account.PasswordCredential;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasswordCredentialPersistencePort {

  Optional<PasswordCredential> findActiveByAccountId(UUID accountId);

  List<PasswordCredential> findRecentByAccountId(UUID accountId, int limit);
}
