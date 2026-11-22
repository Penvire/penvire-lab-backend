package com.penvire.lab.identity.adapter.in.rest;

import com.penvire.lab.identity.domain.signingkey.PublicSigningKey;
import com.penvire.lab.identity.server.model.Jwk;
import com.penvire.lab.identity.server.model.JwkSet;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.JAKARTA_CDI)
public interface JwkMapper {
  Jwk toJwk(PublicSigningKey key);

  List<Jwk> toJwks(List<PublicSigningKey> keys);

  // MapStruct cannot map a list straight onto a bean, so the wrapper is built by hand.
  default JwkSet toJwkSet(List<PublicSigningKey> keys) {
    return new JwkSet(toJwks(keys));
  }
}
