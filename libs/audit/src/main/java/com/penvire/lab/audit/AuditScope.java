package com.penvire.lab.audit;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayDeque;
import java.util.Deque;
import org.jspecify.annotations.Nullable;

/**
 * What the running {@link Audited} method reports about its action. Each audited call has its own
 * frame on the calling thread, so a nested audited call reports to its own record.
 */
@ApplicationScoped
public class AuditScope {
  private final ThreadLocal<Deque<Frame>> frames = ThreadLocal.withInitial(ArrayDeque::new);

  /**
   * Reports the resource the action acts on.
   *
   * @throws IllegalStateException outside an {@link Audited} method
   */
  public void target(String targetId) {
    current().targetId = targetId;
  }

  /**
   * Sets the actor, for a use case that authenticates the caller itself, such as a token endpoint.
   *
   * @throws IllegalStateException outside an {@link Audited} method
   */
  public void actor(AuditActor actor) {
    current().actor = actor;
  }

  /**
   * Reports the state before and after the change; a {@code SUCCESS} records the fields that
   * differ. Pass null as before for a create and as after for a delete. A later call replaces an
   * earlier one; without a call, the event has no changes.
   *
   * @throws IllegalStateException outside an {@link Audited} method, if the method declares no
   *     {@link Audited#state}, or if a side is not of that type
   */
  public void state(@Nullable Object before, @Nullable Object after) {
    Frame frame = current();
    Class<?> type = frame.audited.state();

    if (type == void.class) {
      throw new IllegalStateException(frame.action + " declares no state in @Audited");
    }

    requireType(type, before);
    requireType(type, after);
    frame.before = before;
    frame.after = after;
    frame.stateReported = true;
  }

  /** Opens the frame of an audited call on this thread; {@link AuditInterceptor} calls it. */
  Frame enter(Audited audited) {
    Frame frame = new Frame(audited);
    frames.get().push(frame);
    return frame;
  }

  /**
   * The frame of the innermost audited call on this thread.
   *
   * @throws IllegalStateException outside an {@link Audited} method
   */
  Frame current() {
    Frame frame = frames.get().peek();
    if (frame == null) {
      frames.remove();
      throw new IllegalStateException("No @Audited method is running on this thread");
    }
    return frame;
  }

  /** Closes the innermost frame, and forgets the thread's stack once it is empty. */
  void exit() {
    Deque<Frame> stack = frames.get();
    stack.pop();
    if (stack.isEmpty()) {
      frames.remove();
    }
  }

  private static void requireType(Class<?> type, @Nullable Object side) {
    if (side != null && !type.isInstance(side)) {
      throw new IllegalStateException(
          side.getClass().getName()
              + " reported as state, but @Audited declares "
              + type.getName());
    }
  }

  /** One audited call: its declaration and what the use case reported. */
  static final class Frame {
    /** The method's {@link Audited}, for its state rules. */
    final Audited audited;

    /** {@link Audited#value}. */
    final String action;

    /**
     * Set when recording {@code SUCCESS} failed, so {@link AuditInterceptor} rethrows that error
     * instead of recording it as the action's failure.
     */
    boolean recordingFailed;

    @Nullable String targetId;
    @Nullable AuditActor actor;

    /**
     * Whether the use case called {@link AuditScope#state}; tells a create and delete pair of nulls
     * from no report at all.
     */
    boolean stateReported;

    /** The reported state; null for a create, or if none was reported. */
    @Nullable Object before;

    /** The reported state; null for a delete, or if none was reported. */
    @Nullable Object after;

    private Frame(Audited audited) {
      this.audited = audited;
      this.action = audited.value();
    }
  }
}
