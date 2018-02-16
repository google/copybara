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

package com.google.copybara.util;

import com.google.common.base.Preconditions;

/**
 * A monitor that allows triggering actions when high-level actions take place during the execution.
 */
public interface EventMonitor {

  /** Invoked when the migration starts, only once at the beginning of the execution */
  default void onMigrationStarted(MigrationStartedEvent event) {}

  /** Invoked when each change migration starts. */
  default void onChangeMigrationStarted(ChangeMigrationStartedEvent event) {}

  /** Invoked when each change migration finishes. */
  default void onChangeMigrationFinished(ChangeMigrationFinishedEvent event) {}

  /** Invoked when the migration finishes, only once at the end of the execution */
  void onMigrationFinished(MigrationFinishedEvent event);

  /** Event that happens for every migration that is started. */
  class MigrationStartedEvent {}

  /** Event that happens for every change migration that is started. */
  class ChangeMigrationStartedEvent {}

  /** Event that happens for every change migration that is finished. */
  class ChangeMigrationFinishedEvent {}

  /** Event that happens for every migration that is finished. */
  class MigrationFinishedEvent {

    private final ExitCode exitCode;

    public MigrationFinishedEvent(ExitCode exitCode) {
      this.exitCode = Preconditions.checkNotNull(exitCode);
    }

    public ExitCode getExitCode() {
      return exitCode;
    }
  }
}
