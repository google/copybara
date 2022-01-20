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

import com.google.common.flogger.FluentLogger;
import java.time.Duration;

/**
 * A simple callback for the profiler that logs the execution of the tasks when they finish.
 */
public class LogProfilerListener implements Listener {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  public void taskStarted(Task task) {
    // Ignored. We only record the finish event
  }

  @Override
  public void taskFinished(Task task) {
    StackWalker.getInstance()
        .walk(
            stream ->
                stream
                    .skip(2)
                    .dropWhile(
                        frame ->
                            frame.getClassName().equals("com.google.copybara.profiler.Profiler")
                                || frame.getMethodName().startsWith("$"))
                    .findFirst())
        .ifPresentOrElse(
            caller ->
                logger
                    .atInfo()
                    .withInjectedLogSite(
                        caller.getClassName(),
                        caller.getMethodName(),
                        caller.getLineNumber(),
                        caller.getFileName())
                    .log(
                        "PROFILE: %6d %s",
                        Duration.ofNanos(task.elapsedNanos()).toMillis(), task.getDescription()),
            () ->
                logger.atInfo().log(
                    "PROFILE: %6d %s",
                    Duration.ofNanos(task.elapsedNanos()).toMillis(), task.getDescription()));
  }
}
