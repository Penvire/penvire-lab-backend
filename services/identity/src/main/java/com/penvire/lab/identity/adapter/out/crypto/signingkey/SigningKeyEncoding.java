package com.penvire.lab.identity.adapter.out.crypto.signingkey;

import com.penvire.lab.identity.domain.signingkey.SigningKey;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Canonical bytes for the GCM associated data and the row MAC. Every field is length-prefixed, so
 * no two different keys encode alike. Instants count microseconds: PostgreSQL stores no more, and a
 * MAC over nanoseconds would fail on every key read back.
 */
final class SigningKeyEncoding {

  private static final byte[] AAD_CONTEXT =
      "penvire signing-key private-key v1".getBytes(StandardCharsets.UTF_8);
  private static final byte[] MAC_CONTEXT =
      "penvire signing-key row-mac v1".getBytes(StandardCharsets.UTF_8);

  private SigningKeyEncoding() {}

  static byte[] associatedData(SigningKey key, String kekId) {
    return new Writer()
        .bytes(AAD_CONTEXT)
        .uuid(key.id())
        .string(key.algorithm().name())
        .string(kekId)
        .bytes(key.publicKey().getEncoded())
        .toByteArray();
  }

  static byte[] macInput(SigningKey key) {
    var lifetime = key.lifetime();
    return new Writer()
        .bytes(MAC_CONTEXT)
        .uuid(key.id())
        .string(key.algorithm().name())
        .bytes(key.publicKey().getEncoded())
        .instant(lifetime.publishFrom())
        .instant(lifetime.signFrom())
        .instant(lifetime.signUntil())
        .instant(lifetime.publishUntil())
        .optionalInstant(lifetime.revokedAt())
        .toByteArray();
  }

  private static final class Writer {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    Writer bytes(byte[] value) {
      out.writeBytes(ByteBuffer.allocate(Integer.BYTES).putInt(value.length).array());
      out.writeBytes(value);
      return this;
    }

    Writer string(String value) {
      return bytes(value.getBytes(StandardCharsets.UTF_8));
    }

    Writer uuid(UUID value) {
      return fixed(
          ByteBuffer.allocate(2 * Long.BYTES)
              .putLong(value.getMostSignificantBits())
              .putLong(value.getLeastSignificantBits())
              .array());
    }

    Writer instant(Instant value) {
      return fixed(
          ByteBuffer.allocate(Long.BYTES)
              .putLong(ChronoUnit.MICROS.between(Instant.EPOCH, value))
              .array());
    }

    Writer optionalInstant(@Nullable Instant value) {
      if (value == null) {
        out.write(0);
        return this;
      }
      out.write(1);
      return instant(value);
    }

    byte[] toByteArray() {
      return out.toByteArray();
    }

    private Writer fixed(byte[] value) {
      out.writeBytes(value);
      return this;
    }
  }
}
