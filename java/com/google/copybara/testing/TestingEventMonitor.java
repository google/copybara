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

package com.google.copybara.testing;

import com.google.copybara.util.EventMonitor;
import java.util.ArrayList;
import java.util.List;

public class TestingEventMonitor implements EventMonitor {

  public List<ChangeMigrationStartedEvent> changeMigrationStartedEvents = new ArrayList<>();
  public List<ChangeMigrationFinishedEvent> changeMigrationFinishedEvents = new ArrayList<>();

  @Override
  public void onChangeMigrationStarted(ChangeMigrationStartedEvent event) {
    changeMigrationStartedEvents.add(event);
  }

  @Override
  public void onChangeMigrationFinished(ChangeMigrationFinishedEvent event) {
    changeMigrationFinishedEvents.add(event);
  }

  public int changeMigrationStartedEventCount() {
    return changeMigrationStartedEvents.size();
  }

  public int changeMigrationFinishedEventCount() {
    return changeMigrationFinishedEvents.size();
  }
}
