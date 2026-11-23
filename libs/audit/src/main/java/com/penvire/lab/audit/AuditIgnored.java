package com.penvire.lab.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Leaves a value out of an audit event's {@code changes}. On a record component, that field is
 * never recorded, for example a password hash. On a type, no value of that type is recorded
 * wherever it appears in a state; a state of that type records no changes.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE, ElementType.RECORD_COMPONENT})
public @interface AuditIgnored {}
