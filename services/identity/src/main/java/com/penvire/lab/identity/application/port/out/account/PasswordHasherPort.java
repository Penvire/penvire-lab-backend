package com.penvire.lab.identity.application.port.out.account;

import com.penvire.lab.identity.domain.account.PasswordHash;

public interface PasswordHasherPort {

  PasswordHash hash(String password);

  boolean verify(String password, PasswordHash hash);
}
