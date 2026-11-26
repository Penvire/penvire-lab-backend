package com.penvire.lab.identity.application.port.in.signingkey;

import com.penvire.lab.identity.domain.signingkey.SigningKey;
import java.util.List;

public interface GetJwksSetUseCase {
  List<SigningKey> getJwksSet();
}
