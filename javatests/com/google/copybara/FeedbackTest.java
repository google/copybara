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

package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.config.Config;
import com.google.copybara.config.MapConfigFile;
import com.google.copybara.config.SkylarkParser;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.feedback.Feedback;
import com.google.copybara.testing.DummyEndpoint;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.TestingModule;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FeedbackTest {

  private SkylarkParser skylark;
  private TestingConsole console;
  private OptionsBuilder options;
  private DummyEndpoint dummyEndpoint;
  private Path workdir;

  @Before
  public void setup() throws Exception {
    workdir = Files.createTempDirectory("workdir");
    Files.createDirectories(workdir);
    console = new TestingConsole();
    options = new OptionsBuilder();
    dummyEndpoint = new DummyEndpoint();
    options.setConsole(console);
    options.testingOptions.feedbackEndpoint = dummyEndpoint;
    skylark = new SkylarkParser(ImmutableSet.of(Core.class, TestingModule.class));
  }

  @Test
  public void testParsing() throws IOException, ValidationException {
    Feedback feedback = skylarFeedback("sync_comments", "[]");
    assertThat(feedback.getName()).isEqualTo("sync_comments");
    assertThat(feedback.getModeString()).isEqualTo("feedback");
    assertThat(feedback.getMainConfigFile()).isNotNull();
    assertThat(feedback.getOriginDescription()).isEqualTo(dummyEndpoint.describe());
    assertThat(feedback.getDestinationDescription()).isEqualTo(dummyEndpoint.describe());
  }

  @Test
  public void testAction() throws IOException, ValidationException, RepoException {
    Feedback feedback = skylarFeedback("sync_comments", "[dummy_action]\n");

    feedback.run(workdir, /*sourceRef*/ "ref");
    console.assertThat().onceInLog(MessageType.INFO, "Foo");

    // TODO(danielromero): Make feedback workflow write into dummyEndpoint
  }

  private Feedback skylarFeedback(String name, String actions)
      throws IOException, ValidationException {
    String config =
        ""
            + "def dummy_action(ctx):\n"
            + "    ctx.console.info('Foo')\n"
            + "\n"
            + "core.feedback(\n"
            + "    name = '"
            + name
            + "',\n"
            + "    origin = testing.feedback_endpoint(),\n"
            + "    destination = testing.feedback_endpoint(),\n"
            + "    actions = "
            + actions
            + ",\n"
            + ")\n"
            + "\n";
    System.err.println(config);
    return (Feedback) loadConfig(config).getMigration(name);
  }

  private Config loadConfig(String content) throws IOException, ValidationException {
    return skylark.loadConfig(
        new MapConfigFile(
            ImmutableMap.of("copy.bara.sky", content.getBytes(UTF_8)), "copy.bara.sky"),
        options.build(),
        options.general.console());
  }
}
