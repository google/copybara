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

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.DestinationEffect;
import com.google.copybara.Endpoint;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Trigger;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.Migration;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.feedback.ActionResult.Result;
import com.google.copybara.monitor.EventMonitor;
import com.google.copybara.monitor.EventMonitor.ChangeMigrationFinishedEvent;
import com.google.copybara.monitor.EventMonitor.ChangeMigrationStartedEvent;
import com.google.copybara.profiler.Profiler;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.transform.SkylarkConsole;
import java.nio.file.Path;
import java.util.Collections;
import javax.annotation.Nullable;

/**
 * A migration of feedback or other metadata between an origin and destination.
 */
public class Feedback implements Migration {

  private final String name;
  private final ConfigFile<?> configFile;
  private final Trigger trigger;
  private final Endpoint destination;
  private final Iterable<Action> actions;
  private final GeneralOptions generalOptions;

  public Feedback(
      String name,
      ConfigFile<?> configFile,
      Trigger trigger,
      Endpoint destination,
      ImmutableList<Action> actions,
      GeneralOptions generalOptions) {
    this.name = Preconditions.checkNotNull(name);
    this.configFile = Preconditions.checkNotNull(configFile);
    this.trigger = Preconditions.checkNotNull(trigger);
    this.destination = Preconditions.checkNotNull(destination);
    this.actions = Preconditions.checkNotNull(actions);
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
  }

  @Override
  public void run(Path workdir, ImmutableList<String> sourceRefs)
      throws RepoException, ValidationException {
    ImmutableList.Builder<ActionResult> allResultsBuilder = ImmutableList.builder();

    for (String sourceRef :
        sourceRefs.isEmpty() ? Collections.singletonList((String) null) : sourceRefs) {
      allResultsBuilder.addAll(runForRef(sourceRef));
    }

    ImmutableList<ActionResult> allResults = allResultsBuilder.build();
    // This check also returns true if there are no actions
    if (allResults.stream().allMatch(a -> a.getResult() == Result.NO_OP)) {
      String detailedMessage = allResults.isEmpty()
          ? "actions field is empty"
          :  allResults.stream().map(ActionResult::getMsg)
              .collect(ImmutableList.toImmutableList()).toString();
      throw new EmptyChangeException(
          String.format(
              "Feedback migration '%s' was noop. Detailed messages: %s", name, detailedMessage));
    }
  }

  /**
   * Run this migration for a single ref.
   */
  private ImmutableList<ActionResult> runForRef(@Nullable String sourceRef)
      throws ValidationException, RepoException {
    ImmutableList.Builder<ActionResult> allResults = ImmutableList.builder();
    String root = sourceRef == null
        ? "run/" + name
        : "run/" + name + "/" + sourceRef.replaceAll("([/ ])", "_");
    try (ProfilerTask ignore = profiler().start(root)) {
      for (Action action : actions) {
        ImmutableList<DestinationEffect> effects = ImmutableList.of();
        try (ProfilerTask ignore2 = profiler().start(action.getName())) {
          SkylarkConsole console = new SkylarkConsole(generalOptions.console());
          eventMonitor().onChangeMigrationStarted(new ChangeMigrationStartedEvent());
          FeedbackMigrationContext context =
              new FeedbackMigrationContext(this, action, sourceRef, console);
          action.run(context);
          effects = context.getNewDestinationEffects();
          ActionResult actionResult = context.getActionResult();
          allResults.add(actionResult);
          // First error aborts the execution of the other actions
          ValidationException.checkCondition(
              actionResult.getResult() != Result.ERROR,
              "Feedback migration '%s' action '%s' returned error: %s. Aborting execution.",
              name, action.getName(), actionResult.getMsg());
        } finally {
          eventMonitor().onChangeMigrationFinished(new ChangeMigrationFinishedEvent(effects));
        }
      }
    }
    return allResults.build();
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
    return trigger.describe();
  }

  @Override
  public ImmutableSetMultimap<String, String> getDestinationDescription() {
    return destination.describe();
  }

  Trigger getTrigger() {
    return trigger;
  }

  Endpoint getDestination() {
    return destination;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name)
        .add("trigger", trigger)
        .add("destination", destination)
        .add("actions", actions)
        .toString();
  }

  private Profiler profiler() {
    return generalOptions.profiler();
  }

  private EventMonitor eventMonitor() {
    return generalOptions.eventMonitor();
  }
}
