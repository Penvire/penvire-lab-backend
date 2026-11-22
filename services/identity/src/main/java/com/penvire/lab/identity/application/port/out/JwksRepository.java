package com.penvire.lab.identity.application.port.out;

import com.penvire.lab.identity.domain.signingkey.PublicSigningKey;
import java.time.Instant;
import java.util.List;

public interface JwksRepository {
  List<PublicSigningKey> findAllPublishablePublicKeys(Instant now);
}
