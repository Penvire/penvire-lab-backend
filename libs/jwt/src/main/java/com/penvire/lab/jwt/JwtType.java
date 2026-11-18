package com.penvire.lab.jwt;

public enum JwtType {
  ACCESS("at+jwt"),
  REFRESH("rt+jwt"); // Only used by identity service

  private final String headerValue;

  JwtType(String headerValue) {
    this.headerValue = headerValue;
  }

  public String headerValue() {
    return headerValue;
  }
}
