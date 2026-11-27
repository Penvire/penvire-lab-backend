/**
 * Fine-grained, relationship-based authorization through OpenFGA (ADR 0010).
 *
 * <p>Wraps {@code dev.openfga:openfga-sdk} behind a small, synchronous CDI facade ({@link
 * com.penvire.lab.fga.FgaClient}) with validated value types ({@link
 * com.penvire.lab.fga.model.FgaSubject}, {@link com.penvire.lab.fga.model.FgaObject}, {@link
 * com.penvire.lab.fga.model.FgaTuple}) to prevent tuple-string injection. Supports checks, {@code
 * require(...)} enforcement, {@link com.penvire.lab.fga.CurrentFgaSubject} caller injection,
 * idempotent writes and deletes, atomic write-and-delete updates, paginated tuple reads, and
 * bounded {@code listObjects}.
 *
 * <p>All operations block the calling thread up to the configured {@code request-timeout} deadline
 * and must run on worker or virtual threads, never on the Vert.x event loop. Reads and checks
 * default to {@code HIGHER_CONSISTENCY} for immediate revocation and read-your-own-writes
 * semantics. Authorization refusals throw Quarkus {@code ForbiddenException} or {@code
 * UnauthorizedException} (recorded as {@code DENIED} by {@code @Audited} actions), and OpenFGA
 * outages or timeouts fail closed with {@link com.penvire.lab.fga.FgaUnavailableException}.
 */
@NullMarked
package com.penvire.lab.fga;

import org.jspecify.annotations.NullMarked;
