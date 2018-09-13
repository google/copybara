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

package com.google.copybara.monitor;

import com.google.common.base.Preconditions;
import com.google.copybara.util.console.Console;

public class ConsoleEventMonitor implements EventMonitor {

  private final Console console;
  private final EventMonitor delegate;

  public ConsoleEventMonitor(Console console, EventMonitor delegate) {
    this.console = Preconditions.checkNotNull(console);
    this.delegate = Preconditions.checkNotNull(delegate);
  }

  @Override
  public void onMigrationStarted(MigrationStartedEvent event) {
    console.verboseFmt("onMigrationStarted(): %s", event);
    delegate.onMigrationStarted(event);
  }

  @Override
  public void onChangeMigrationStarted(ChangeMigrationStartedEvent event) {
    console.verboseFmt("onChangeMigrationStarted(): %s", event);
    delegate.onChangeMigrationStarted(event);
  }

  @Override
  public void onChangeMigrationFinished(ChangeMigrationFinishedEvent event) {
    console.verboseFmt("onChangeMigrationFinished(): %s", event);
    delegate.onChangeMigrationFinished(event);
  }

  @Override
  public void onMigrationFinished(MigrationFinishedEvent event) {
    console.verboseFmt("onMigrationFinished(): %s", event);
    delegate.onMigrationFinished(event);
  }

  @Override
  public void onInfoFinished(InfoFinishedEvent event) {
    console.verboseFmt("onInfoFinished(): %s", event);
    delegate.onInfoFinished(event);
  }
}
