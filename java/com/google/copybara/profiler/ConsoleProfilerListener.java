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

import com.google.common.base.Preconditions;
import com.google.copybara.util.console.Console;
import java.time.Duration;

/**
 * A profiler {@link Listener} that prints profiling stats to the console in verbose mode.
 */
public class ConsoleProfilerListener implements Listener {

  private final Console console;

  public ConsoleProfilerListener(Console console) {
    this.console = Preconditions.checkNotNull(console);
  }

  @Override
  public void taskStarted(Task task) {
    // Ignored. We only record the finish event
  }

  @Override
  public void taskFinished(Task task) {
    console.verboseFmt("PROFILE: %6d %s",
        Duration.ofNanos(task.elapsedNanos()).toMillis(), task.getDescription());
  }
}
