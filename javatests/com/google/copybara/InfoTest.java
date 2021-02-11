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

package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.util.ExitCode.SUCCESS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.copybara.Info.MigrationReference;
import com.google.copybara.authoring.Author;
import com.google.copybara.config.Config;
import com.google.copybara.config.Migration;
import com.google.copybara.config.SkylarkParser.ConfigWithDependencies;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TestingEventMonitor;
import com.google.copybara.util.ExitCode;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.StarlarkMode;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class InfoTest {

  private SkylarkTestExecutor skylark;
  private InfoCmd info;
  private String configInfo;
  private TestingConsole console;

  private OptionsBuilder optionsBuilder;
  private TestingEventMonitor eventMonitor;
  private Migration migration;
  private Config config;
  private ConfigWithDependencies configWithDeps;
  private Path temp;
  private ImmutableMultimap<String, String> dummyOriginDescription;
  private ImmutableMultimap<String, String> dummyDestinationDescription;

  @Before
  public void setUp() throws IOException {
    dummyOriginDescription = ImmutableMultimap.of("origin", "foo");
    dummyDestinationDescription = ImmutableMultimap.of("dest", "bar");
    console = new TestingConsole();
    temp = Files.createTempDirectory("temp");
    optionsBuilder = new OptionsBuilder();
    optionsBuilder.setConsole(console);
    optionsBuilder.setWorkdirToRealTempDir();
    skylark = new SkylarkTestExecutor(optionsBuilder);
    eventMonitor = new TestingEventMonitor();
    optionsBuilder.general.enableEventMonitor("just testing", eventMonitor);
    optionsBuilder.general.starlarkMode = StarlarkMode.STRICT.name();
    migration = mock(Migration.class);
    config = new Config(ImmutableMap.of("workflow", migration),
        temp.resolve("copy.bara.sky").toString(),
        ImmutableMap.of());
    configWithDeps = mock(ConfigWithDependencies.class);
    when(configWithDeps.getConfig()).thenAnswer(i -> config);
    configInfo = ""
        + "core.workflow("
        + "    name = 'workflow',"
        + "    origin = git.origin(url = 'https://example.com/orig', ref = 'master'),"
        + "    destination = git.destination(url = 'https://example.com/dest'),"
        + "    authoring = authoring.overwrite('Foo <foo@example.com>')"
        + ")\n\n"
        + "";
    info = new InfoCmd(
        (configPath, sourceRef) -> new ConfigLoader(
            skylark.createModuleSet(),
            skylark.createConfigFile("copy.bara.sky", configInfo),
            optionsBuilder.general.getStarlarkMode()) {
          @Override
          protected Config doLoadForRevision(Console console, Revision revision)
              throws ValidationException {
            try {
              return skylark.loadConfig(configPath);
            } catch (IOException e) {
              throw new AssertionError("Should not fail", e);
            }
          }
        },  getFakeContextProvider());
  }

  @Test
  public void testInfoAll() throws Exception {
    configInfo = ""
        + "core.workflow("
        + "    name = 'workflow',"
        + "    origin = git.origin(url = 'https://example.com/orig'),"
        + "    destination = git.destination(url = 'https://example.com/dest'),"
        + "    authoring = authoring.overwrite('Foo <foo@example.com>'),"
        + ")\n\n"
        + "git.mirror("
        + "    name = 'example',"
        + "    description = 'This is a description',"
        + "    origin = 'https://example.com/mirror1',"
        + "    destination = 'https://example.com/mirror2',"
        + ")\n\n"
        + "";
    ExitCode code = info.run(new CommandEnv(temp,
            skylark.createModuleSet().getOptions(),
            ImmutableList.of("copy.bara.sky")));

    assertThat(code).isEqualTo(SUCCESS);

    console.assertThat()
        .matchesNextSkipAhead(MessageType.INFO,
            ".*example.*git\\.mirror \\(https://example.com/mirror1\\)"
                + ".*git\\.mirror \\(https://example.com/mirror2\\)"
                + ".*MIRROR.*This is a description.*")
        .matchesNext(MessageType.INFO,
        ".*workflow.*git\\.origin \\(https://example.com/orig\\)"
            + ".*git\\.destination \\(https://example.com/dest\\).*SQUASH.*");

    console.assertThat().onceInLog(MessageType.INFO,
        "To get information about the state of any migration run:(.|\n)*"
            + "copybara info copy.bara.sky \\[workflow_name\\](.|\n)*");
  }

  @Test
  public void testInfoUpToDate() throws Exception {
    info = new InfoCmd(
        (configPath, sourceRef) -> new ConfigLoader(
            skylark.createModuleSet(),
            skylark.createConfigFile("copy.bara.sky", configInfo),
            optionsBuilder.general.getStarlarkMode()) {
          @Override
          public Config load(Console console) {
            return config;
          }
          @Override
          public ConfigWithDependencies loadWithDependencies(Console console) {
            return configWithDeps;
          }
        }, getFakeContextProvider());
    MigrationReference<DummyRevision> workflow =
        MigrationReference.create("workflow", new DummyRevision("1111"), ImmutableList.of());
    Info<?> mockedInfo = Info.create(
        dummyOriginDescription,
        dummyDestinationDescription,
        ImmutableList.of(workflow));
    Mockito.<Info<? extends Revision>>when(migration.getInfo()).thenReturn(mockedInfo);
    info.run(new CommandEnv(temp,
        optionsBuilder.build(),
        ImmutableList.of("copy.bara.sky", "workflow")));
    assertThat(eventMonitor.infoFinishedEvent).isNotNull();
    assertThat(eventMonitor.infoFinishedEvent.getInfo()).isEqualTo(mockedInfo);
    console
        .assertThat()
        .onceInLog(MessageType.INFO, ".*last_migrated 1111 - last_available None.*");
  }

  @Test
  public void testInfoAvailableToMigrate() throws Exception {
    info = new InfoCmd(
        (configPath, sourceRef) -> new ConfigLoader(
            skylark.createModuleSet(),
            skylark.createConfigFile("copy.bara.sky", configInfo),
            optionsBuilder.general.getStarlarkMode()) {
          @Override
          public Config load(Console console) {
            return config;
          }
          @Override
          public ConfigWithDependencies loadWithDependencies(Console console) {
            return configWithDeps;
          }
        },  getFakeContextProvider());
    MigrationReference<DummyRevision> workflow =
        MigrationReference.create(
            "workflow",
            new DummyRevision("1111"),
            ImmutableList.of(
                newChange(
                    "2222",
                    "First change",
                    ZonedDateTime.ofInstant(
                        Instant.ofEpochSecond(1541631979), ZoneId.of("-08:00"))),
                newChange(
                    "3333",
                    "Second change",
                    ZonedDateTime.ofInstant(
                        Instant.ofEpochSecond(1541639979), ZoneId.of("-08:00")))));
    Info<?> mockedInfo = Info.create(
        dummyOriginDescription,
        dummyDestinationDescription,
        ImmutableList.of(workflow));
    Mockito.<Info<? extends Revision>>when(migration.getInfo()).thenReturn(mockedInfo);

    // Copybara copybara = new Copybara(new ConfigValidator() {}, migration -> {});
    // copybara.info(optionsBuilder.build(), config, "workflow");

    info.run(new CommandEnv(temp,
        optionsBuilder.build(),
        ImmutableList.of("copy.bara.sky", "workflow")));

    assertThat(eventMonitor.infoFinishedEvent).isNotNull();
    assertThat(eventMonitor.infoFinishedEvent.getInfo()).isEqualTo(mockedInfo);
    console
        .assertThat()
        .onceInLog(MessageType.INFO, ".*last_migrated 1111 - last_available 3333.*")
        .onceInLog(MessageType.INFO, ".*Date.*Revision.*Description.*Author.*")
        .onceInLog(MessageType.INFO, ".*2018-11-07 15:06:19.*2222.*First change.*Foo <Bar>.*")
        .onceInLog(MessageType.INFO, ".*2018-11-07 17:19:39.*3333.*Second change.*Foo <Bar>.*");
  }

  private Change<DummyRevision> newChange(
      String revision, String description, ZonedDateTime dateTime) {
    return new Change<>(
        new DummyRevision(revision),
        new Author("Foo", "Bar"),
        description,
        dateTime,
        ImmutableListMultimap.of());
  }

  private static ContextProvider getFakeContextProvider() {
    return (metaDataConfig, configFileArgs, configLoaderProvider, console) -> ImmutableMap.of();
  }
}
