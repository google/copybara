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

/**
 * A listener that, when registered in a {@link Profiler}, will be notified everytime
 * a task is started/finished.
 */
public interface Listener {

  /**
   * A notification about a task that has started.
   */
  void taskStarted(Task task);

  /**
   * A notification about a task finish. It is guaranteed that {@link Task#isFinished()}
   * will return true.
   */
  void taskFinished(Task task);
}
