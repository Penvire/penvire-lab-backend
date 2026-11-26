package com.penvire.lab.identity.application.port.in.account;

import java.util.UUID;

public interface ChangePasswordUseCase {
  void changePassword(UUID accountId, String currentPassword, String newPassword);
}
