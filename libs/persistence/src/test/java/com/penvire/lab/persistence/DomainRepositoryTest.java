package com.penvire.lab.persistence;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import jakarta.transaction.TransactionalException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DomainRepositoryTest {

  /** Bounds every wait on another transaction, so a lock bug fails the test instead of hanging. */
  private static final long WAIT_SECONDS = 10;

  @Inject WidgetRepository repository;

  @Inject EntityManager entityManager;

  @Test
  void insertedWidgetCanBeReadInALaterTransaction() {
    Widget widget = new Widget(UUID.randomUUID(), "first");

    Widget inserted = QuarkusTransaction.requiringNew().call(() -> repository.insert(widget));

    assertEquals(widget, inserted);
    assertEquals(Optional.of(widget), findInNewTransaction(widget.id()));
  }

  @Test
  void findByIdReturnsEmptyForAnUnknownId() {
    assertEquals(Optional.empty(), findInNewTransaction(UUID.randomUUID()));
  }

  @Test
  void updateChangesStateAndKeepsTheId() {
    Widget widget = insertInNewTransaction("before");
    Widget renamed = new Widget(widget.id(), "after");

    Widget updated = QuarkusTransaction.requiringNew().call(() -> repository.update(renamed));

    assertEquals(renamed, updated);
    assertEquals(Optional.of(renamed), findInNewTransaction(widget.id()));
  }

  @Test
  void updateOfAMissingRowThrowsAndWritesNothing() {
    Widget missing = new Widget(UUID.randomUUID(), "missing");

    assertThrows(
        IllegalStateException.class,
        () -> QuarkusTransaction.requiringNew().run(() -> repository.update(missing)));

    assertEquals(Optional.empty(), findInNewTransaction(missing.id()));
  }

  @Test
  void everyMethodRequiresATransactionOpenedByTheCaller() {
    Widget widget = new Widget(UUID.randomUUID(), "outside");

    assertAll(
        () -> assertThrows(TransactionalException.class, () -> repository.findById(widget.id())),
        () ->
            assertThrows(
                TransactionalException.class, () -> repository.findByIdForUpdate(widget.id())),
        () -> assertThrows(TransactionalException.class, () -> repository.insert(widget)),
        () -> assertThrows(TransactionalException.class, () -> repository.update(widget)));
  }

  @Test
  void findByIdForUpdateBlocksASecondLockingTransaction() throws Exception {
    Widget widget = insertInNewTransaction("locked");

    try (ExecutorService competitor = Executors.newSingleThreadExecutor()) {
      QuarkusTransaction.requiringNew()
          .run(() -> {
            repository.findByIdForUpdate(widget.id());

            Future<Optional<Widget>> secondLock =
                competitor.submit(() ->
                    QuarkusTransaction.requiringNew()
                        .call(() -> {
                          failLockWaitsAfterHalfASecond();
                          return repository.findByIdForUpdate(widget.id());
                        }));

            ExecutionException failure =
                assertThrows(
                    ExecutionException.class, () -> secondLock.get(WAIT_SECONDS, TimeUnit.SECONDS));
            assertTrue(
                causedByLockFailure(failure),
                () -> "Expected a lock failure, got " + failure.getCause());
          });
    }
  }

  @Test
  void findByIdDoesNotWaitForALockedRow() throws Exception {
    Widget widget = insertInNewTransaction("readable");

    try (ExecutorService reader = Executors.newSingleThreadExecutor()) {
      QuarkusTransaction.requiringNew()
          .run(() -> {
            repository.findByIdForUpdate(widget.id());

            Future<Optional<Widget>> plainRead =
                reader.submit(() ->
                    QuarkusTransaction.requiringNew()
                        .call(() -> {
                          failLockWaitsAfterHalfASecond();
                          return repository.findById(widget.id());
                        }));

            assertEquals(
                Optional.of(widget),
                assertDoesNotThrow(
                    () -> plainRead.get(WAIT_SECONDS, TimeUnit.SECONDS),
                    "A plain read must not wait for or fail on a row lock"));
          });
    }
  }

  private Widget insertInNewTransaction(String name) {
    Widget widget = new Widget(UUID.randomUUID(), name);
    return QuarkusTransaction.requiringNew().call(() -> repository.insert(widget));
  }

  private Optional<Widget> findInNewTransaction(UUID id) {
    return QuarkusTransaction.requiringNew().call(() -> repository.findById(id));
  }

  /**
   * PostgreSQL waits for row locks indefinitely by default; this bounds the current transaction.
   */
  private void failLockWaitsAfterHalfASecond() {
    entityManager.createNativeQuery("SET LOCAL lock_timeout = '500ms'").executeUpdate();
  }

  private static boolean causedByLockFailure(Throwable failure) {
    for (@Nullable Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof PessimisticLockException || cause instanceof LockTimeoutException) {
        return true;
      }
    }
    return false;
  }
}
