package com.google.copybara.profiler;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import java.util.Objects;

/**
 * Represents a task run by Copybara
 */
public final class Task {

  private static final int NOT_FINISHED = -1;
  private final String description;
  private final long startNanos;
  private final long finishNanos;

  Task(String description, long startNanos) {
    this(description, startNanos, NOT_FINISHED);
  }

  Task(String description, long startNanos, long finishNanos) {
    this.description = Preconditions.checkNotNull(description);
    this.startNanos = startNanos;
    this.finishNanos = finishNanos;
  }

  Task finish(long finishNanos) {
    Preconditions.checkArgument(finishNanos != -1, "Already finished!");
    return new Task(description, startNanos, finishNanos);
  }

  /**
   * Description of the task. Follows a pattern like:
   * <pre>
   *   //copybara/task/subtask/subsubtask
   * </pre>
   */
  public String getDescription() {
    return description;
  }

  /**
   * Time elapsedNanos running the task. Should only be called if {@link #isFinished()}
   * returns true.
   */
  public long elapsedNanos() {
    Preconditions.checkState(finishNanos != NOT_FINISHED, "Not finished!");
    return finishNanos - startNanos;
  }

  /**
   * Returns true if the task is finished.
   */
  public boolean isFinished() {
    return finishNanos != NOT_FINISHED;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Task task = (Task) o;
    return startNanos == task.startNanos &&
        finishNanos == task.finishNanos &&
        Objects.equals(description, task.description);
  }

  @Override
  public int hashCode() {
    return Objects.hash(description, startNanos, finishNanos);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("description", description)
        .add("startNanos", startNanos)
        .add("finishNanos", finishNanos)
        .toString();
  }
}
