package com.penvire.lab.identity.adapter.out.crypto;

import javax.crypto.SecretKey;

/** One KEK: its ID and the two keys derived from it, so AES and HMAC never share a key. */
public record KeyEncryptionKey(String id, SecretKey encryption, SecretKey mac) {

  @Override
  public String toString() {
    return "KeyEncryptionKey[iid=" + id + "]";
  }
}
