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
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.config.Config;
import com.google.copybara.config.MapConfigFile;
import com.google.copybara.config.SkylarkParser;
import com.google.copybara.exception.RepoException;
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
    console.assertThat().onceInLog(MessageType.INFO, "Ref: 12345");
    console.assertThat().onceInLog(MessageType.INFO, "Feedback name: default");
    console.assertThat().onceInLog(MessageType.INFO, "Action name: test_action");
  }

  @Test
  public void testNullSourceRef() throws Exception {
    Feedback feedback = loggingFeedback();
    feedback.run(workdir, /*sourceRef*/ null);
    console.assertThat().onceInLog(MessageType.INFO, "Ref: None");
  }

  @Test
  public void testActionsMustReturnResult() throws IOException, ValidationException, RepoException {
    Feedback feedback = feedback(
        ""
            + "def test_action(ctx):\n"
            + "    ctx.console.info('Bad action')\n"
            + "\n"
    );
    try {
      feedback.run(workdir, /*sourceRef*/ null);
      fail();
    } catch (ValidationException expected) {
      assertThat(expected.getMessage())
          .contains("Feedback actions must return a result via built-in functions: success(), "
              + "error(), noop() return, but 'test_action' returned: None");
    }

  }

  @Test
  public void testSuccessResult() throws ValidationException, IOException, RepoException {
    Feedback feedback = feedback(
        ""
            + "def test_action(ctx):\n"
            + "    return ctx.success()\n"
            + "\n"
    );
    feedback.run(workdir, /*sourceRef*/ null);
    console.assertThat().equalsNext(MessageType.INFO, "Action 'test_action' returned success");
  }

  @Test
  public void testNoopResult() throws ValidationException, IOException, RepoException {
    Feedback feedback = feedback(
        ""
            + "def test_action(ctx):\n"
            + "    return ctx.noop('No effect')\n"
            + "\n"
    );
    feedback.run(workdir, /*sourceRef*/ null);
    console
        .assertThat()
        .equalsNext(MessageType.INFO, "Action 'test_action' returned noop: No effect");
  }

  @Test
  public void testErrorResult() throws ValidationException, IOException, RepoException {
    Feedback feedback = feedback(
        ""
            + "def test_action(ctx):\n"
            + "    return ctx.error('This is an error')\n"
            + "\n"
    );
    try {
      feedback.run(workdir, /*sourceRef*/ null);
      fail();
    } catch (ValidationException expected) {
      console.assertThat()
          .equalsNext(MessageType.ERROR, "Action 'test_action' returned error: This is an error");
    }
  }

  @Test
  public void testErrorResultEmptyMsg() throws Exception {
    Feedback feedback = feedback(
        ""
            + "def test_action(ctx):\n"
            + "    result = ctx.error()\n"
            + "\n"
    );
    try {
      feedback.run(workdir, /*sourceRef*/ null);
      fail();
    } catch (ValidationException expected) {
      assertThat(expected.getMessage())
          .matches(".*parameter 'msg' has no default value, in method.*error\\(\\).*");
    }
  }

  private Feedback loggingFeedback() throws IOException, ValidationException {
    return feedback(
        ""
            + "def test_action(ctx):\n"
            + "    ref = 'None'\n"
            + "    if ctx.ref:\n"
            + "      ref = ctx.ref\n"
            + "    ctx.console.info('Ref: ' + ref)\n"
            + "    ctx.console.info('Feedback name: ' + ctx.feedback_name)\n"
            + "    ctx.console.info('Action name: ' + ctx.action_name)\n"
            + "    return ctx.success()\n"
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
