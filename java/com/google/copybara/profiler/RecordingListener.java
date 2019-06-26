/*
 * Copyright (C) 2019 Google Inc.
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

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;

/**
 * A profiler listener storing all completed task
 */
public class RecordingListener implements Listener {

  private final List<Task> finishedTasks = new ArrayList<>();

  @Override
  public void taskStarted(Task task) {
    // Ignored. We only record the finish event
  }

  @Override
  public void taskFinished(Task task) {
    // For now, just finished tasks. In the future we should consider exporting open tasks as they
    // might help pinpoint timeouts.
    finishedTasks.add(task);
  }

  /**
   * List of all completed tasks, immutable.
   */
  public List<Task> getCompletedTasks() {
    return ImmutableList.copyOf(finishedTasks);
  }
}
