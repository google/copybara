/*
 * Copyright (C) 2021 Google Inc.
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

package com.google.copybara.onboard;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.copybara.CommandEnv;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TestingEventMonitor;
import com.google.copybara.util.console.Message;
import com.google.copybara.util.console.testing.TestingConsole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CommandLineGuideTest {

  private final OptionsBuilder optionsBuilder = new OptionsBuilder();
  private final TestingEventMonitor eventMonitor = new TestingEventMonitor();
  private Path temp;
  private SkylarkTestExecutor skylark;

  @Before
  public void setUp() throws Exception {
    optionsBuilder.general.enableEventMonitor("just testing", eventMonitor);
    temp = Files.createTempDirectory("temp");
    skylark = new SkylarkTestExecutor(optionsBuilder);
  }

  @Test
  public void testGitToGitTemplate() {
    TestingConsole console = new TestingConsole();
    String urlPrefix = "https://github.com/google/";
    console
        .respondWithString(urlPrefix.concat("origin"))
        .respondWithString(urlPrefix.concat("destination"))
        .respondWithString("Copybara <copy@bara.com>");
    optionsBuilder.setConsole(console);

    CommandLineGuide.runForCommandLine(
        new CommandEnv(
            temp, skylark.createModuleSet().getOptions(), ImmutableList.of("copy.bara.sky")));

    ConfigBuilder expectedConfig = new ConfigBuilder(new GitToGitTemplate());
    expectedConfig.setNamedStringParameter("origin_url", urlPrefix.concat("origin"));
    expectedConfig.setNamedStringParameter("destination_url", urlPrefix.concat("destination"));
    expectedConfig.setNamedStringParameter("email", "Copybara <copy@bara.com>");
    assertThat(
            Joiner.on('\n')
                .join(
                    console.getMessages().stream()
                        .map(Message::getText)
                        .collect(Collectors.toList())))
        .contains(expectedConfig.build());
  }

  @Test
  public void testGitToGitTemplatePredicateNotSatisfied() {
    String urlPrefix = "https://github.com/google/";

    TestingConsole console = new TestingConsole();
    console
        .respondWithString(urlPrefix.concat("origin"))
        .respondWithString(urlPrefix.concat("destination"))
        .respondWithString("not a valid email");
    optionsBuilder.setConsole(console);

    CommandLineGuide.runForCommandLine(
        new CommandEnv(
            temp, skylark.createModuleSet().getOptions(), ImmutableList.of("copy.bara.sky")));
    assertThat(
            Joiner.on('\n')
                .join(
                    console.getMessages().stream()
                        .map(Message::getText)
                        .collect(Collectors.toList())))
        .contains("Invalid response");
  }
}
