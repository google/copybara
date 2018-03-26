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

package com.google.copybara.feedback;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.Endpoint;
import com.google.copybara.GeneralOptions;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.Migration;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.profiler.Profiler;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.transform.SkylarkConsole;
import java.nio.file.Path;
import javax.annotation.Nullable;

/**
 * A migration of feedback or other metadata between an origin and destination.
 */
public class Feedback implements Migration {

  private final String name;
  private final ConfigFile<?> configFile;
  private final Endpoint origin;
  private final Endpoint destination;
  private final Iterable<Action> actions;
  private final GeneralOptions generalOptions;

  public Feedback(
      String name,
      ConfigFile<?> configFile,
      Endpoint origin,
      Endpoint destination,
      ImmutableList<Action> actions,
      GeneralOptions generalOptions) {
    this.name = Preconditions.checkNotNull(name);
    this.configFile = Preconditions.checkNotNull(configFile);
    this.origin = Preconditions.checkNotNull(origin);
    this.destination = Preconditions.checkNotNull(destination);
    this.actions = Preconditions.checkNotNull(actions);
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
  }

  @Override
  public void run(Path workdir, @Nullable String sourceRef)
      throws RepoException, ValidationException {
    // TODO(danielromero): Handle correctly null sourceRefs
    SkylarkConsole console = new SkylarkConsole(generalOptions.console());
    Profiler profiler = generalOptions.profiler();
    try (ProfilerTask ignore = profiler.start("run/" + name)) {
      for (Action action : actions) {
        try (ProfilerTask ignore2 = profiler.start(action.getName())) {
          action.run(new FeedbackContext(origin, destination, sourceRef, console));
        }
      }
    }
    ValidationException.checkCondition(console.getErrorCount() == 0,
        "%d errors executing the feedback migration", console.getErrorCount());
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getModeString() {
    return "feedback";
  }

  @Override
  public ConfigFile<?> getMainConfigFile() {
    return configFile;
  }

  @Override
  public ImmutableSetMultimap<String, String> getOriginDescription() {
    return origin.describe();
  }

  @Override
  public ImmutableSetMultimap<String, String> getDestinationDescription() {
    return destination.describe();
  }
}
