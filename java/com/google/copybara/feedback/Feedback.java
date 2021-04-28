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

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSetMultimap.Builder;
import com.google.copybara.DestinationEffect;
import com.google.copybara.Endpoint;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Trigger;
import com.google.copybara.action.Action;
import com.google.copybara.action.ActionResult;
import com.google.copybara.action.ActionResult.Result;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.Migration;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.monitor.EventMonitor.ChangeMigrationFinishedEvent;
import com.google.copybara.monitor.EventMonitor.ChangeMigrationStartedEvent;
import com.google.copybara.monitor.EventMonitor.EventMonitors;
import com.google.copybara.profiler.Profiler;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.transform.SkylarkConsole;
import java.nio.file.Path;
import java.util.ArrayList;
import javax.annotation.Nullable;

/**
 * A migration of feedback or other metadata between an origin and destination.
 */
public class Feedback implements Migration {

  private final String name;
  @Nullable private final String description;
  private final ConfigFile configFile;
  private final Trigger trigger;
  private final Endpoint destination;
  private final Iterable<Action> actions;
  private final GeneralOptions generalOptions;

  public Feedback(
      String name,
      @Nullable String description,
      ConfigFile configFile,
      Trigger trigger,
      Endpoint destination,
      ImmutableList<Action> actions,
      GeneralOptions generalOptions) {
    this.name = Preconditions.checkNotNull(name);
    this.description = description;
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
    String suffix = Joiner.on('_').join(sourceRefs).replaceAll("([/ ])", "_");
    String root = "run/" + name + "/" + suffix.substring(0, Math.min(suffix.length(), 20));
    try (ProfilerTask ignore = profiler().start(root)) {
      for (Action action : actions) {
        ArrayList<DestinationEffect> effects = new ArrayList<>();
        try (ProfilerTask ignore2 = profiler().start(action.getName())) {
          SkylarkConsole console = new SkylarkConsole(generalOptions.console());
          eventMonitors().dispatchEvent(
              m -> m.onChangeMigrationStarted(new ChangeMigrationStartedEvent()));
          FeedbackMigrationContext context =
              new FeedbackMigrationContext(
                  this, action, generalOptions.cliLabels(), sourceRefs, console);
          action.run(context);
          effects.addAll(context.getNewDestinationEffects());
          ActionResult actionResult = context.getActionResult();
          allResultsBuilder.add(actionResult);
          // First error aborts the execution of the other actions
          ValidationException.checkCondition(
              actionResult.getResult() != Result.ERROR,
              "Feedback migration '%s' action '%s' returned error: %s. Aborting execution.",
              name, action.getName(), actionResult.getMsg());
        } finally {
          eventMonitors().dispatchEvent(m -> m.onChangeMigrationFinished(
              new ChangeMigrationFinishedEvent(ImmutableList.copyOf(effects),
                  getOriginDescription(), getDestinationDescription())));
        }
      }
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

  @Override
  public String getName() {
    return name;
  }

  @Nullable
  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public String getModeString() {
    return "feedback";
  }

  @Override
  public ConfigFile getMainConfigFile() {
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

  /**
   * Returns a multimap containing enough data to fingerprint the actions for validation
   * purposes.
   */
  public ImmutableSetMultimap<String, ImmutableSetMultimap<String, String>>
      getActionsDescription() {
    Builder<String, ImmutableSetMultimap<String, String>> descriptionBuilder =
        ImmutableSetMultimap.builder();
    for (Action action : actions) {
      descriptionBuilder.put(action.getName(), action.describe());
    }
    return descriptionBuilder.build();
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

  private EventMonitors eventMonitors() {
    return generalOptions.eventMonitors();
  }
}
