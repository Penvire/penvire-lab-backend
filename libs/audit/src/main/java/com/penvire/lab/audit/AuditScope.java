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

  /** Opens the frame of an audited call on this thread; {@link AuditInterceptor} calls it. */
  Frame enter(String action) {
    Frame frame = new Frame(action);
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

  /** One audited call: its action and what the use case reported. */
  static final class Frame {
    final String action;

    /**
     * Set when recording {@code SUCCESS} failed, so {@link AuditInterceptor} rethrows that error
     * instead of recording it as the action's failure.
     */
    boolean recordingFailed;

    @Nullable String targetId;
    @Nullable AuditActor actor;

    private Frame(String action) {
      this.action = action;
    }
  }
}
