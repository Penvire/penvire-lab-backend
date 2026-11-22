package com.penvire.lab.identity.application.port.in;

import com.penvire.lab.identity.domain.signingkey.PublicSigningKey;
import java.util.List;

public interface GetJwksSetUseCase {
  List<PublicSigningKey> getJwksSet();
}
