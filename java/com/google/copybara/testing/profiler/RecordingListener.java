/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.copybara.testing.profiler;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.MoreObjects;
import com.google.copybara.profiler.Listener;
import com.google.copybara.profiler.Task;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RecordingListener implements Listener {

  public final List<TaskWithType> events = new ArrayList<>();
  private int index = 0;

  @Override
  public void taskStarted(Task task) {
    events.add(new TaskWithType(EventType.START, task));
  }

  @Override
  public void taskFinished(Task task) {
    events.add(new TaskWithType(EventType.END, task));
  }

  public static class TaskWithType {

    private final EventType type;
    private final Task task;

    public TaskWithType(EventType type, Task task) {
      this.type = type;
      this.task = task;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("type", type).add("task", task).toString();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      TaskWithType that = (TaskWithType) o;
      return type == that.type && Objects.equals(task, that.task);
    }

    @Override
    public int hashCode() {
      return Objects.hash(type, task);
    }
  }

  public enum EventType {
    START,
    END
  }

  /**
   * Assert that this listener matches the expected event.
   */
  public RecordingListener assertMatchesNext(EventType eventType, String description) {
    assertWithMessage("No more recorded events")
        .that(events.size())
        .isGreaterThan(index);
    TaskWithType taskWithType = events.get(index);
    assertThat(taskWithType.type).isEqualTo(eventType);
    assertThat(taskWithType.task.getDescription()).isEqualTo(description);
    index++;
    return this;
  }
}
