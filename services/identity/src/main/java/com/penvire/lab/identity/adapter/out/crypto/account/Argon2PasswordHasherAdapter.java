package com.penvire.lab.identity.adapter.out.crypto.account;

import com.penvire.lab.identity.application.port.out.account.PasswordHasherPort;
import com.penvire.lab.identity.domain.account.PasswordHash;
import jakarta.enterprise.context.ApplicationScoped;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

@ApplicationScoped
public class Argon2PasswordHasherAdapter implements PasswordHasherPort {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Base64.Encoder B64E = Base64.getEncoder().withoutPadding();
  private static final Base64.Decoder B64D = Base64.getDecoder();

  private static final String PHC_DELIMITER = "$";
  private static final Pattern PHC_SPLIT = Pattern.compile(Pattern.quote(PHC_DELIMITER));
  private static final Pattern PARAM_SPLIT = Pattern.compile(",");

  private static final int HASH_BYTES = 32;
  private static final int SALT_BYTES = 16;
  private static final int MEMORY_KIB = 19_456;
  private static final int ITERATIONS = 2;
  private static final int PARALLELISM = 1;

  @Override
  public PasswordHash hash(String password) {
    byte[] salt = new byte[SALT_BYTES];
    RANDOM.nextBytes(salt);

    byte[] hash =
        derive(
            password,
            salt,
            Argon2Parameters.ARGON2_VERSION_13,
            MEMORY_KIB,
            ITERATIONS,
            PARALLELISM,
            HASH_BYTES);

    String phc =
        String.join(
            PHC_DELIMITER,
            "",
            "argon2id",
            "v=" + Argon2Parameters.ARGON2_VERSION_13,
            "m=" + MEMORY_KIB + ",t=" + ITERATIONS + ",p=" + PARALLELISM,
            B64E.encodeToString(salt),
            B64E.encodeToString(hash));

    return new PasswordHash(phc);
  }

  @Override
  public boolean verify(String password, PasswordHash hash) {
    return parse(hash.value())
        .map(parsedHash -> {
          byte[] candidate =
              derive(
                  password,
                  parsedHash.salt,
                  parsedHash.version,
                  parsedHash.memoryKiB,
                  parsedHash.iterations,
                  parsedHash.parallelism,
                  parsedHash.hash.length);
          return MessageDigest.isEqual(parsedHash.hash, candidate);
        })
        .orElse(false);
  }

  private byte[] derive(
      String password,
      byte[] salt,
      int version,
      int memoryKiB,
      int iterations,
      int parallelism,
      int hashLength) {

    Argon2Parameters params =
        new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(version)
            .withMemoryAsKB(memoryKiB)
            .withIterations(iterations)
            .withParallelism(parallelism)
            .withSalt(salt)
            .build();

    Argon2BytesGenerator generator = new Argon2BytesGenerator();
    generator.init(params);

    byte[] hash = new byte[hashLength];

    char[] chars = password.toCharArray();
    try {
      generator.generateBytes(chars, hash);
    } finally {
      Arrays.fill(chars, '\0');
    }

    return hash;
  }

  private Optional<ParsedHash> parse(String phc) {
    String[] parts = PHC_SPLIT.split(phc, -1);
    if (parts.length != 6 || !parts[0].isEmpty() || !"argon2id".equals(parts[1])) {
      return Optional.empty();
    }

    if (!parts[2].startsWith("v=")) {
      return Optional.empty();
    }

    String[] costParts = PARAM_SPLIT.split(parts[3], -1);
    if (costParts.length != 3
        || !costParts[0].startsWith("m=")
        || !costParts[1].startsWith("t=")
        || !costParts[2].startsWith("p=")) {
      return Optional.empty();
    }

    try {
      int version = Integer.parseInt(parts[2].substring(2));
      int memoryKiB = Integer.parseInt(costParts[0].substring(2));
      int iterations = Integer.parseInt(costParts[1].substring(2));
      int parallelism = Integer.parseInt(costParts[2].substring(2));

      if (version != Argon2Parameters.ARGON2_VERSION_13
          || memoryKiB <= 0
          || iterations <= 0
          || parallelism <= 0) {
        return Optional.empty();
      }

      byte[] salt = B64D.decode(parts[4]);
      byte[] hash = B64D.decode(parts[5]);
      if (salt.length == 0 || hash.length == 0) {
        return Optional.empty();
      }

      return Optional.of(new ParsedHash(version, memoryKiB, iterations, parallelism, salt, hash));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static final class ParsedHash {
    final int version;
    final int memoryKiB;
    final int iterations;
    final int parallelism;
    final byte[] salt;
    final byte[] hash;

    ParsedHash(
        int version, int memoryKiB, int iterations, int parallelism, byte[] salt, byte[] hash) {
      this.version = version;
      this.memoryKiB = memoryKiB;
      this.iterations = iterations;
      this.parallelism = parallelism;
      this.salt = salt;
      this.hash = hash;
    }
  }
}
