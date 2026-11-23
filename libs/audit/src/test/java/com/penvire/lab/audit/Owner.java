package com.penvire.lab.audit;

/** A nested record holding personal data: an email and an ignored profile. */
record Owner(String email, Profile profile) {}
