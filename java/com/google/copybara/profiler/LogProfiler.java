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

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * A simple callback for the profiler that logs the execution of the tasks when they finish.
 */
public class LogProfiler implements Listener {

  private final Logger logger = Logger.getLogger(this.getClass().getName());

  @Override
  public void taskStarted(Task task) {
    // Ignored. We only record the finish event
  }

  @Override
  public void taskFinished(Task task) {
    logger.info("PROFILE: "
        + TimeUnit.NANOSECONDS.toMillis(task.elapsedNanos()) + " "
        + task.getDescription());
  }
}
