package com.penvire.lab.fga.openfga;

import com.penvire.lab.fga.FgaException;
import com.penvire.lab.fga.FgaUnavailableException;
import com.penvire.lab.fga.model.FgaObject;
import com.penvire.lab.fga.model.FgaSubject;
import com.penvire.lab.fga.model.FgaTuple;
import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.api.client.model.ClientTupleKeyWithoutCondition;
import dev.openfga.sdk.api.model.Tuple;
import dev.openfga.sdk.api.model.TupleKey;
import dev.openfga.sdk.errors.ApiException;
import dev.openfga.sdk.errors.FgaError;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

/**
 * Pure conversion and error-translation helpers between {@code libs:fga} types and OpenFGA Java SDK
 * models. Wire-string parsing stays internal to this package so callers cannot bypass {@link
 * FgaSubject} or {@link FgaObject} construction rules.
 */
final class OpenFgaSdkMapper {

  private OpenFgaSdkMapper() {}

  static FgaException translate(String operation, Throwable cause) {
    if (cause instanceof FgaError err) {
      int status = err.getStatusCode();
      if (err.isServerError() || err.isRateLimitError() || status >= 500 || status == 429) {
        return new FgaUnavailableException(
            "OpenFGA " + operation + " unavailable (HTTP " + status + ")", err);
      }
      return new FgaException("OpenFGA " + operation + " failed (HTTP " + status + ")", err);
    }

    if (cause instanceof ApiException api) {
      int status = api.getStatusCode();
      if (status == 0 || status >= 500 || status == 429) {
        return new FgaUnavailableException(
            "OpenFGA " + operation + " unavailable (HTTP " + status + ")", api);
      }
      return new FgaException("OpenFGA " + operation + " failed (HTTP " + status + ")", api);
    }

    if (hasUnavailableCause(cause)) {
      return new FgaUnavailableException("OpenFGA " + operation + " unreachable", cause);
    }

    return new FgaException("OpenFGA " + operation + " failed", cause);
  }

  static List<ClientTupleKey> toClientTupleKeys(List<FgaTuple> tuples) {
    return tuples.stream()
        .map(t ->
            new ClientTupleKey()
                .user(t.subject().formatted())
                .relation(t.relation())
                ._object(t.object().formatted()))
        .toList();
  }

  static List<ClientTupleKeyWithoutCondition> toClientTupleKeysWithoutCondition(
      List<FgaTuple> tuples) {
    return tuples.stream()
        .map(t ->
            new ClientTupleKeyWithoutCondition()
                .user(t.subject().formatted())
                .relation(t.relation())
                ._object(t.object().formatted()))
        .toList();
  }

  static FgaTuple fromSdkTuple(Tuple tuple) {
    TupleKey key = Objects.requireNonNull(tuple.getKey(), "OpenFGA tuple key is null");
    FgaSubject subject = parseSubject(Objects.requireNonNull(key.getUser(), "tuple user"));
    String relation = Objects.requireNonNull(key.getRelation(), "tuple relation");
    FgaObject object = parseObject(Objects.requireNonNull(key.getObject(), "tuple object"));
    return new FgaTuple(subject, relation, object);
  }

  static FgaObject parseObject(String raw) {
    int colon = raw.indexOf(':');
    if (colon <= 0 || colon == raw.length() - 1) {
      throw new FgaException("Invalid OpenFGA object returned by server: " + raw);
    }
    try {
      return new FgaObject(raw.substring(0, colon), raw.substring(colon + 1));
    } catch (IllegalArgumentException e) {
      throw new FgaException("Invalid OpenFGA object returned by server: " + raw, e);
    }
  }

  static FgaSubject parseSubject(String raw) {
    int colon = raw.indexOf(':');
    if (colon <= 0 || colon == raw.length() - 1) {
      throw new FgaException("Invalid OpenFGA subject returned by server: " + raw);
    }

    String type = raw.substring(0, colon);
    String rest = raw.substring(colon + 1);
    int hash = rest.indexOf('#');
    try {
      if (hash >= 0) {
        return new FgaSubject.Userset(type, rest.substring(0, hash), rest.substring(hash + 1));
      }
      return new FgaSubject.Direct(type, rest);
    } catch (IllegalArgumentException e) {
      throw new FgaException("Unsupported OpenFGA subject returned by server: " + raw, e);
    }
  }

  private static boolean hasUnavailableCause(Throwable error) {
    Throwable cursor = error;
    while (cursor != null) {
      if (cursor instanceof IOException
          || cursor instanceof HttpTimeoutException
          || cursor instanceof TimeoutException) {
        return true;
      }
      cursor = cursor.getCause();
    }
    return false;
  }
}
