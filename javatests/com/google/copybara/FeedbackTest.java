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
import com.google.copybara.exception.ValidationException;
import com.google.copybara.feedback.Feedback;
import com.google.copybara.testing.DummyTrigger;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.TestingModule;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
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
  private DummyTrigger dummyTrigger;
  private Path workdir;

  @Before
  public void setup() throws Exception {
    workdir = Files.createTempDirectory("workdir");
    Files.createDirectories(workdir);
    console = new TestingConsole();
    options = new OptionsBuilder();
    options.setConsole(console);
    dummyTrigger = new DummyTrigger();
    options.testingOptions.feedbackTrigger = dummyTrigger;
    skylark = new SkylarkParser(ImmutableSet.of(Core.class, TestingModule.class));
  }

  @Test
  public void testParsing() throws Exception {
    Feedback feedback = loggingFeedback();
    assertThat(feedback.getName()).isEqualTo("default");
    assertThat(feedback.getModeString()).isEqualTo("feedback");
    assertThat(feedback.getMainConfigFile()).isNotNull();
    assertThat(feedback.getOriginDescription()).isEqualTo(dummyTrigger.describe());
    assertThat(feedback.getDestinationDescription()).isEqualTo(dummyTrigger.describe());
  }

  @Test
  public void testAction() throws Exception {
    Feedback feedback = loggingFeedback();
    feedback.run(workdir, /*sourceRef*/ "12345");
    console.assertThat().onceInLog(MessageType.INFO, "Action for ref 12345");
  }

  @Test
  public void testNullSourceRef() throws Exception {
    Feedback feedback = loggingFeedback();
    feedback.run(workdir, /*sourceRef*/ null);
    console.assertThat().onceInLog(MessageType.INFO, "Action for ref None");
  }

  private Feedback loggingFeedback() throws IOException, ValidationException {
    return feedback(
        ""
            + "def test_action(ctx):\n"
            + "    ref = 'None'\n"
            + "    if ctx.ref:\n"
            + "      ref = ctx.ref\n"
            + "    ctx.console.info('Action for ref ' + ref)\n"
            + "\n"
    );
  }

  private Feedback feedback(String actionFunction) throws IOException, ValidationException {
    String config =
        actionFunction
            + "\n"
            + "core.feedback(\n"
            + "    name = 'default',\n"
            + "    origin = testing.dummy_trigger(),\n"
            + "    destination = testing.dummy_endpoint(),\n"
            + "    actions = [test_action,],\n"
            + ")\n"
            + "\n";
    System.err.println(config);
    return (Feedback) loadConfig(config).getMigration("default");
  }

  private Config loadConfig(String content) throws IOException, ValidationException {
    return skylark.loadConfig(
        new MapConfigFile(
            ImmutableMap.of("copy.bara.sky", content.getBytes(UTF_8)), "copy.bara.sky"),
        options.build(),
        options.general.console());
  }
}
