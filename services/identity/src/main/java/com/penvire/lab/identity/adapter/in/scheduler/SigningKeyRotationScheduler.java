package com.penvire.lab.identity.adapter.in.scheduler;

import com.penvire.lab.identity.application.port.in.RotateSigningKeyUseCase;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

/** Ensures signing keys exist on boot and runs the daily rotation check (ADR 0007). */
@ApplicationScoped
public class SigningKeyRotationScheduler {
  private final RotateSigningKeyUseCase rotateSigningKey;

  public SigningKeyRotationScheduler(RotateSigningKeyUseCase rotateSigningKey) {
    this.rotateSigningKey = rotateSigningKey;
  }

  /**
   * Runs synchronously at startup after Flyway migrations, so Identity serves keys before accepting
   * traffic and fails fast if key storage is broken.
   */
  @Startup
  void onStart() {
    rotateSigningKey.rotate();
  }

  @Scheduled(
      every = "${penvire.jwks.rotation-interval:24h}",
      delayed = "${penvire.jwks.rotation-delay:24h}",
      concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  void onSchedule() {
    rotateSigningKey.rotate();
  }
}
