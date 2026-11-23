package com.penvire.lab.audit;

/** A personal profile, ignored by every action wherever it appears. */
@AuditIgnored
record Profile(String displayName) {}
