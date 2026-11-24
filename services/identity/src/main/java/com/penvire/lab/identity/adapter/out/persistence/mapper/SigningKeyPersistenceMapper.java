package com.penvire.lab.identity.adapter.out.persistence.mapper;

import com.penvire.lab.identity.adapter.out.persistence.entity.SigningKeyEntity;
import com.penvire.lab.identity.domain.signingkey.SigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import com.penvire.lab.identity.domain.signingkey.SigningKeyLifetime;
import com.penvire.lab.persistence.EntityMapper;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

/**
 * Maps the plain columns of a signing key. The private key, its nonce, the KEK ID and the row MAC
 * are cryptographic; the persistence adapter sets and checks them, never this mapper.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.JAKARTA_CDI)
public interface SigningKeyPersistenceMapper extends EntityMapper<SigningKey, SigningKeyEntity> {

  @Override
  @Mapping(target = "signingKeyAlgorithm", source = "algorithm")
  @Mapping(target = "publishFrom", source = "lifetime.publishFrom")
  @Mapping(target = "signFrom", source = "lifetime.signFrom")
  @Mapping(target = "signUntil", source = "lifetime.signUntil")
  @Mapping(target = "publishUntil", source = "lifetime.publishUntil")
  @Mapping(target = "revokedAt", source = "lifetime.revokedAt")
  @Mapping(target = "encryptedPrivateKey", ignore = true)
  @Mapping(target = "privateKeyNonce", ignore = true)
  @Mapping(target = "kekId", ignore = true)
  @Mapping(target = "rowMac", ignore = true)
  SigningKeyEntity toEntity(SigningKey domain);

  @Override
  @Mapping(target = "algorithm", source = "signingKeyAlgorithm")
  @Mapping(
      target = "publicKey",
      expression = "java(decodePublicKey(entity.getSigningKeyAlgorithm(), entity.getPublicKey()))")
  @Mapping(target = "lifetime", source = "entity")
  SigningKey toDomain(SigningKeyEntity entity);

  /** Only what retiring and revoking change; everything else is fixed at insert. */
  @Override
  @BeanMapping(ignoreByDefault = true)
  @Mapping(target = "signUntil", source = "lifetime.signUntil")
  @Mapping(target = "publishUntil", source = "lifetime.publishUntil")
  @Mapping(target = "revokedAt", source = "lifetime.revokedAt")
  void updateEntity(SigningKey domain, @MappingTarget SigningKeyEntity entity);

  /** The lifetime's components have the same names as the entity's columns. */
  SigningKeyLifetime toLifetime(SigningKeyEntity entity);

  default byte[] encodePublicKey(PublicKey publicKey) {
    return publicKey.getEncoded();
  }

  default PublicKey decodePublicKey(SigningKeyAlgorithm algorithm, byte[] encoded) {
    var keyFactoryAlgorithm =
        switch (algorithm) {
          case ML_DSA_65 -> "ML-DSA";
          case RS256 -> "RSA";
        };
    try {
      return KeyFactory.getInstance(keyFactoryAlgorithm)
          .generatePublic(new X509EncodedKeySpec(encoded));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Stored " + algorithm + " public key is unreadable", e);
    }
  }
}
