/**
 * The one time source for services: inject {@link java.time.Clock} instead of calling {@code
 * Instant.now()}. Defaults to UTC system time; tests use MutableClock from the test fixtures.
 */
@NullMarked
package com.penvire.lab.clock;

import org.jspecify.annotations.NullMarked;
