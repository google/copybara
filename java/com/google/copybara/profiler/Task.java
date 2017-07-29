/*
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.copybara.profiler;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import java.util.Objects;

/**
 * Represents a task run by Copybara
 */
public final class Task {

  private static final int NOT_FINISHED = -1;
  private final String description;
  private final ImmutableMap<String, String> fields;
  private final long startNanos;
  private final long finishNanos;

  Task(String description, long startNanos) {
    this(description, startNanos, NOT_FINISHED);
  }

  Task(String description, ImmutableMap<String, String> fields, long startNanos) {
    this(description, fields, startNanos, NOT_FINISHED);
  }

  Task(String description, long startNanos, long finishNanos) {
    this(description, ImmutableMap.of(), startNanos, finishNanos);
  }

  Task(String description, ImmutableMap<String, String> fields, long startNanos, long finishNanos) {
    this.description = Preconditions.checkNotNull(description);
    this.fields = Preconditions.checkNotNull(fields);
    this.startNanos = startNanos;
    this.finishNanos = finishNanos;
  }

  Task finish(long finishNanos) {
    Preconditions.checkArgument(finishNanos != -1, "Already finished!");
    return new Task(description, fields, startNanos, finishNanos);
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
   * Context fields of the task.
   *
   * <p>They are not part of the profiler path, but they give more context information on this task
   * and it's type. Might be used to implement more extensive monitoring.
   */
  public ImmutableMap<String, String> getFields() {
    return fields;
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
