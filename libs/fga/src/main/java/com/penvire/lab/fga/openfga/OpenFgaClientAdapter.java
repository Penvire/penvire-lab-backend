package com.penvire.lab.fga.openfga;

import com.penvire.lab.fga.FgaClient;
import com.penvire.lab.fga.FgaException;
import com.penvire.lab.fga.FgaUnavailableException;
import com.penvire.lab.fga.config.FgaConfig;
import com.penvire.lab.fga.model.FgaObject;
import com.penvire.lab.fga.model.FgaPrincipal;
import com.penvire.lab.fga.model.FgaRelation;
import com.penvire.lab.fga.model.FgaResource;
import com.penvire.lab.fga.model.FgaTuple;
import com.penvire.lab.fga.model.FgaType;
import com.penvire.lab.fga.model.FgaValidation;
import com.penvire.lab.fga.query.FgaTupleFilter;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import dev.openfga.sdk.api.client.model.ClientCheckResponse;
import dev.openfga.sdk.api.client.model.ClientListObjectsRequest;
import dev.openfga.sdk.api.client.model.ClientListObjectsResponse;
import dev.openfga.sdk.api.client.model.ClientReadRequest;
import dev.openfga.sdk.api.client.model.ClientReadResponse;
import dev.openfga.sdk.api.client.model.ClientWriteRequest;
import dev.openfga.sdk.api.configuration.ClientCheckOptions;
import dev.openfga.sdk.api.configuration.ClientListObjectsOptions;
import dev.openfga.sdk.api.configuration.ClientReadOptions;
import dev.openfga.sdk.api.configuration.ClientWriteOptions;
import dev.openfga.sdk.api.model.Tuple;
import dev.openfga.sdk.api.model.WriteRequestDeletes;
import dev.openfga.sdk.api.model.WriteRequestWrites;
import dev.openfga.sdk.errors.FgaInvalidParameterException;
import dev.openfga.sdk.errors.FgaValidationError;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Synchronous, blocking {@link FgaClient} implementation backed by {@link OpenFgaClient} (ADR
 * 0010). Must run on worker or virtual threads, never on the Vert.x event loop.
 *
 * <p>Applies the configured consistency preference to every read and check, caps each call with
 * {@link FgaConfig#requestTimeout()}, configures idempotent writes and deletes ({@code IGNORE} on
 * duplicate or missing tuples), follows pagination tokens on {@link #read}, and translates outages,
 * timeouts, rate limits, and 5xx responses into {@link FgaUnavailableException}.
 */
@ApplicationScoped
public class OpenFgaClientAdapter implements FgaClient {

  private static final int READ_PAGE_SIZE = 100;

  private final OpenFgaClient sdkClient;
  private final FgaConfig config;

  @Inject
  public OpenFgaClientAdapter(OpenFgaClient sdkClient, FgaConfig config) {
    this.sdkClient = Objects.requireNonNull(sdkClient, "sdkClient");
    this.config = Objects.requireNonNull(config, "config");
  }

  @Override
  public boolean check(FgaPrincipal subject, FgaRelation relation, FgaResource object) {
    ClientCheckRequest request =
        new ClientCheckRequest()
            .user(subject.toFgaSubject().formatted())
            .relation(FgaValidation.requireRelation(relation.fgaRelation()))
            ._object(object.toFgaObject().formatted());

    ClientCheckOptions sdkOptions = new ClientCheckOptions().consistency(config.consistency());
    ClientCheckResponse response = execute("check", () -> sdkClient.check(request, sdkOptions));
    return Boolean.TRUE.equals(response.getAllowed());
  }

  @Override
  public void writeAndDelete(List<FgaTuple> writes, List<FgaTuple> deletes) {
    Objects.requireNonNull(writes, "writes");
    Objects.requireNonNull(deletes, "deletes");
    if (writes.isEmpty() && deletes.isEmpty()) {
      return;
    }

    ClientWriteRequest request = new ClientWriteRequest();
    if (!writes.isEmpty()) {
      request.writes(OpenFgaSdkMapper.toClientTupleKeys(writes));
    }

    if (!deletes.isEmpty()) {
      request.deletes(OpenFgaSdkMapper.toClientTupleKeysWithoutCondition(deletes));
    }

    ClientWriteOptions sdkOptions =
        new ClientWriteOptions()
            .onDuplicate(WriteRequestWrites.OnDuplicateEnum.IGNORE)
            .onMissing(WriteRequestDeletes.OnMissingEnum.IGNORE);

    execute("write", () -> sdkClient.write(request, sdkOptions));
  }

  @Override
  public List<FgaTuple> read(FgaTupleFilter filter) {
    Objects.requireNonNull(filter, "filter");

    ClientReadRequest request = new ClientReadRequest();
    if (filter.subject() != null) {
      request.user(filter.subject().formatted());
    }

    if (filter.relation() != null) {
      request.relation(filter.relation());
    }

    String objectFilter = filter.formattedObjectFilter();
    if (objectFilter != null) {
      request._object(objectFilter);
    }

    List<FgaTuple> all = new ArrayList<>();
    String continuationToken = null;
    do {
      ClientReadOptions sdkOptions =
          new ClientReadOptions().consistency(config.consistency()).pageSize(READ_PAGE_SIZE);

      if (continuationToken != null) {
        sdkOptions.continuationToken(continuationToken);
      }

      ClientReadResponse response = execute("read", () -> sdkClient.read(request, sdkOptions));
      if (response.getTuples() != null) {
        for (Tuple tuple : response.getTuples()) {
          all.add(OpenFgaSdkMapper.fromSdkTuple(tuple));
        }
      }

      String next = response.getContinuationToken();
      continuationToken = (next == null || next.isBlank()) ? null : next;
    } while (continuationToken != null);

    return List.copyOf(all);
  }

  @Override
  public List<FgaObject> listObjects(
      FgaPrincipal subject, FgaRelation relation, FgaType objectType) {
    ClientListObjectsRequest request =
        new ClientListObjectsRequest()
            .user(subject.toFgaSubject().formatted())
            .relation(FgaValidation.requireRelation(relation.fgaRelation()))
            .type(FgaValidation.requireType(objectType.fgaType()));

    ClientListObjectsOptions sdkOptions =
        new ClientListObjectsOptions().consistency(config.consistency());
    ClientListObjectsResponse response =
        execute("listObjects", () -> sdkClient.listObjects(request, sdkOptions));

    List<String> rawObjects = response.getObjects();
    if (rawObjects == null || rawObjects.isEmpty()) {
      return List.of();
    }

    return rawObjects.stream().map(OpenFgaSdkMapper::parseObject).toList();
  }

  private <T> T execute(String operation, SdkCall<T> call) {
    CompletableFuture<T> future;
    try {
      future = call.invoke();
    } catch (FgaInvalidParameterException | FgaValidationError e) {
      throw new FgaException("Invalid OpenFGA " + operation + " request", e);
    }

    try {
      return future.get(config.requestTimeout().toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      throw new FgaUnavailableException(
          "OpenFGA " + operation + " timed out after " + config.requestTimeout(), e);
    } catch (InterruptedException e) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      throw new FgaUnavailableException("Interrupted while calling OpenFGA " + operation, e);
    } catch (ExecutionException | CompletionException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      throw OpenFgaSdkMapper.translate(operation, cause);
    }
  }

  @FunctionalInterface
  private interface SdkCall<T> {
    CompletableFuture<T> invoke() throws FgaInvalidParameterException, FgaValidationError;
  }
}
