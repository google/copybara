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

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.copybara.DestinationEffect;
import com.google.copybara.Info;
import com.google.copybara.Revision;
import com.google.copybara.profiler.Task;
import com.google.copybara.util.ExitCode;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A monitor that allows triggering actions when high-level actions take place during the execution.
 *
 * <p>Default implementation logs in the console the events in verbose mode only.
 */
public interface EventMonitor {

  EventMonitor EMPTY_MONITOR = new EventMonitor() {};

  /** Invoked when the migration starts, only once at the beginning of the execution */
  default void onMigrationStarted(MigrationStartedEvent event) {}

  /** Invoked when each change migration starts. */
  default void onChangeMigrationStarted(ChangeMigrationStartedEvent event) {}

  /** Invoked when each change migration finishes. */
  default void onChangeMigrationFinished(ChangeMigrationFinishedEvent event) {}

  /** Invoked when the migration finishes, only once at the end of the execution */
  default void onMigrationFinished(MigrationFinishedEvent event) {}

  /** Invoked when an info subcommand finishes, only once at the end of the execution */
  default void onInfoFinished(InfoFinishedEvent event) {};

  /** Invoked when an info subcommand fails, only once at the end of the execution */
  default void onInfoFailed(InfoFailedEvent event) {};

  /** Event that happens for every migration that is started. */
  class MigrationStartedEvent {
    @Override
    public String toString() {
      return "MigrationStartedEvent";
    }
  }

  /** Event that happens for every change migration that is started. */
  class ChangeMigrationStartedEvent {
    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).toString();
    }
  }

  /** Event that happens for every change migration that is finished. */
  class ChangeMigrationFinishedEvent {
    private final ImmutableList<DestinationEffect> destinationEffects;
    private final ImmutableMultimap<String, String> originDescription;
    private final ImmutableMultimap<String, String> destinationDescription;

    public ChangeMigrationFinishedEvent(ImmutableList<DestinationEffect> destinationEffects,
        ImmutableMultimap<String, String> originDescription,
        ImmutableMultimap<String, String> destinationDescription) {
      this.destinationEffects = destinationEffects;
      this.originDescription = originDescription;
      this.destinationDescription = destinationDescription;
    }

    public ImmutableList<DestinationEffect> getDestinationEffects() {
      return destinationEffects;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("destinationEffects", destinationEffects)
          .toString();
    }

    public ImmutableMultimap<String, String> getDestinationDescription() {
      return destinationDescription;
    }

    public ImmutableMultimap<String, String> getOriginDescription() {
      return originDescription;
    }
  }

  /** Event that happens for every migration that is finished. */
  class MigrationFinishedEvent {

    private final ExitCode exitCode;
    private final Optional<List<Task>> profileData;

    public MigrationFinishedEvent(ExitCode exitCode) {
      this(exitCode, null);
    }

    public MigrationFinishedEvent(
        ExitCode exitCode, @Nullable List<Task> profileData) {
      this.exitCode = Preconditions.checkNotNull(exitCode);
      this.profileData = Optional.ofNullable(
          profileData == null ? null : ImmutableList.copyOf(profileData));
    }

    public ExitCode getExitCode() {
      return exitCode;
    }
    public Optional<List<Task>> getProfileData() {
      return profileData;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("exitCode", exitCode)
          .add("profiler", profileData.orElse(null))
          .toString();
    }
  }

  /** Event that happens for every info subcommand that is finished. */
  class InfoFinishedEvent {

    private final Info<? extends Revision> info;
    private final ImmutableMap<String, String> context;

    public InfoFinishedEvent(Info<? extends Revision> info, ImmutableMap<String, String> context) {
      this.info = Preconditions.checkNotNull(info);
      this.context = Preconditions.checkNotNull(context);
    }

    public InfoFinishedEvent(Info<? extends Revision> info) {
      this(info, ImmutableMap.of());
    }

    public Info<? extends Revision> getInfo() {
      return info;
    }
    public ImmutableMap<String, String> getContext() {
      return context;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("info", info)
          .add("context", context)
          .toString();
    }

  }

  /** Event that happens for every info subcommand that failed. */
  class InfoFailedEvent {

    private final String error;
    private final ImmutableMap<String, String> context;

    public InfoFailedEvent(String error, ImmutableMap<String, String> context) {
      this.error = Preconditions.checkNotNull(error);
      this.context = Preconditions.checkNotNull(context);
    }

    public String getError() {
      return error;
    }

    public ImmutableMap<String, String> getContext() {
      return context;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("error", error)
          .add("context", context)
          .toString();
    }

  }

  /**
   * Holder for all active event monitors
   */
  class EventMonitors {
    private final List<EventMonitor> monitors;

    public EventMonitors(List<EventMonitor> monitors) {
      this.monitors = monitors;
    }

    /** Accepts a functional to apply to all active monitors */
    public void dispatchEvent(Dispatch event) {
      for (EventMonitor monitor : monitors) {
        event.dispatch(monitor);
      }
    }

  }

  /**
   * A functional to dispatch an event to monitors.
   */
  interface Dispatch {
    void dispatch(EventMonitor monitor);
  }
}
