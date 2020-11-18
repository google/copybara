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

import static com.google.copybara.util.console.Message.MessageType.VERBOSE;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.copybara.Change;
import com.google.copybara.DestinationEffect;
import com.google.copybara.DestinationEffect.DestinationRef;
import com.google.copybara.DestinationEffect.OriginRef;
import com.google.copybara.DestinationEffect.Type;
import com.google.copybara.Info;
import com.google.copybara.Info.MigrationReference;
import com.google.copybara.authoring.Author;
import com.google.copybara.monitor.EventMonitor.ChangeMigrationFinishedEvent;
import com.google.copybara.monitor.EventMonitor.ChangeMigrationStartedEvent;
import com.google.copybara.monitor.EventMonitor.InfoFinishedEvent;
import com.google.copybara.monitor.EventMonitor.MigrationFinishedEvent;
import com.google.copybara.monitor.EventMonitor.MigrationStartedEvent;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.util.ExitCode;
import com.google.copybara.util.console.testing.TestingConsole;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ConsoleEventMonitorTest {

  private TestingConsole console;
  private EventMonitor eventMonitor;

  @Before
  public void setUp() throws Exception {
    console = new TestingConsole();
    eventMonitor = new ConsoleEventMonitor(console, EventMonitor.EMPTY_MONITOR);
  }

  @Test
  public void testEventMonitorPrintsToConsole() {
    eventMonitor.onMigrationStarted(new MigrationStartedEvent());
    eventMonitor.onMigrationFinished(new MigrationFinishedEvent(ExitCode.SUCCESS));
    eventMonitor.onChangeMigrationStarted(new ChangeMigrationStartedEvent());
    DestinationEffect destinationEffect =
        new DestinationEffect(
            Type.CREATED,
            "Created revision 1234",
            ImmutableList.of(new OriginRef("ABCD")),
            new DestinationRef("1234", "commit", /*url=*/ null));
    eventMonitor.onChangeMigrationFinished(
        new ChangeMigrationFinishedEvent(ImmutableList.of(destinationEffect),
            ImmutableMultimap.of(), ImmutableMultimap.of()));

    MigrationReference<DummyRevision> workflow =
        MigrationReference.create(
            "workflow",
            new DummyRevision("1111"),
            ImmutableList.of(newChange("2222"), newChange("3333")));
    Info<?> info = Info.create(ImmutableMultimap.of("origin", "foo"),
        ImmutableMultimap.of("dest", "bar"), ImmutableList.of(workflow));
    eventMonitor.onInfoFinished(new InfoFinishedEvent(info,
        ImmutableMap.of("foo_a", "foo_b")));
    console
        .assertThat()
        .equalsNext(VERBOSE, "onMigrationStarted(): MigrationStartedEvent")
        .equalsNext(VERBOSE, "onMigrationFinished(): "
            + "MigrationFinishedEvent{exitCode=SUCCESS, profiler=null}")
        .equalsNext(VERBOSE, "onChangeMigrationStarted(): ChangeMigrationStartedEvent{}")
        .matchesNext(
            VERBOSE, "onChangeMigrationFinished[(][)]: ChangeMigrationFinishedEvent[{].*[}]")
        .matchesNext(VERBOSE, "onInfoFinished[(][)]: InfoFinishedEvent[{].*(origin).*"
            + "(foo).*(dest).*(bar).*(foo_a).*(foo_b).*[}]")
        .containsNoMoreMessages();
  }

  private Change<DummyRevision> newChange(String revision) {
    return new Change<>(
        new DummyRevision(revision),
        new Author("Foo", "Bar"),
        "Lorem Ipsum",
        ZonedDateTime.now(ZoneId.systemDefault()),
        ImmutableListMultimap.of());
  }
}
