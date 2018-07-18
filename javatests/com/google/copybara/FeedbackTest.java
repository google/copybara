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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.DestinationEffect.DestinationRef;
import com.google.copybara.config.Config;
import com.google.copybara.config.MapConfigFile;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.feedback.Feedback;
import com.google.copybara.monitor.EventMonitor.ChangeMigrationFinishedEvent;
import com.google.copybara.testing.DummyTrigger;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TestingEventMonitor;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FeedbackTest {

  private SkylarkTestExecutor skylark;
  private TestingConsole console;
  private TestingEventMonitor eventMonitor;
  private DummyTrigger dummyTrigger;
  private Path workdir;

  @Before
  public void setup() throws Exception {
    workdir = Jimfs.newFileSystem().getPath("/");
    console = new TestingConsole();
    eventMonitor = new TestingEventMonitor();
    OptionsBuilder options = new OptionsBuilder();
    options.setConsole(console);
    options.general.withEventMonitor(eventMonitor);
    dummyTrigger = new DummyTrigger();
    options.testingOptions.feedbackTrigger = dummyTrigger;
    skylark = new SkylarkTestExecutor(options);
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
    feedback.run(workdir, ImmutableList.of("12345"));
    console.assertThat().onceInLog(MessageType.INFO, "Ref: 12345");
    console.assertThat().onceInLog(MessageType.INFO, "Feedback name: default");
    console.assertThat().onceInLog(MessageType.INFO, "Action name: test_action");
  }

  @Test
  public void testNullSourceRef() throws Exception {
    Feedback feedback = loggingFeedback();
    feedback.run(workdir, ImmutableList.of());
    console.assertThat().onceInLog(MessageType.INFO, "Ref: None");
  }

  @Test
  public void testMultipleSourceRefs() throws Exception {
    Feedback feedback = loggingFeedback();
    feedback.run(workdir, ImmutableList.of("12345", "67890"));
    console
        .assertThat()
        .matchesNext(MessageType.INFO, ".*Ref: 12345")
        .matchesNext(MessageType.INFO, ".*Feedback name: default")
        .matchesNext(MessageType.INFO, ".*Action name: test_action")
        .matchesNext(MessageType.INFO, ".*Action 'test_action' returned success.*")
        .matchesNext(MessageType.INFO, ".*Ref: 67890")
        .matchesNext(MessageType.INFO, ".*Feedback name: default")
        .matchesNext(MessageType.INFO, ".*Action name: test_action")
        .matchesNext(MessageType.INFO, ".*Action 'test_action' returned success.*");
  }

  @Test
  public void testActionsMustReturnResult() throws Exception {
    Feedback feedback = feedback(
        ""
            + "def test_action(ctx):\n"
            + "    ctx.console.info('Bad action')\n"
            + "\n",
        "test_action");
    try {
      feedback.run(workdir, ImmutableList.of());
      fail();
    } catch (ValidationException expected) {
      assertThat(expected.getMessage())
          .contains("Feedback actions must return a result via built-in functions: success(), "
              + "error(), noop() return, but 'test_action' returned: None");
    }
  }

  @Test
  public void testSuccessResult() throws Exception {
    Feedback feedback = feedback(
        ""
            + "def test_action(ctx):\n"
            + "    return ctx.success()\n"
            + "\n",
        "test_action");
    feedback.run(workdir, ImmutableList.of());
    console.assertThat().equalsNext(MessageType.INFO, "Action 'test_action' returned success");
  }

  @Test
  public void testNoActionsThrowsEmptyChangeException() throws Exception {
    Feedback feedback = feedback("");
    try {
      feedback.run(workdir, ImmutableList.of());
      fail();
    } catch (EmptyChangeException expected) {
      assertThat(expected).hasMessageThat()
          .contains(
              "Feedback migration 'default' was noop. Detailed messages: actions field is empty");
    }
  }

  @Test
  public void testNoopResultThrowsEmptyChangeException() throws Exception {
    Feedback feedback = feedback(
        ""
            + "def test_action_1(ctx):\n"
            + "    return ctx.noop('No effect 1')\n"
            + "\n"
            + "def test_action_2(ctx):\n"
            + "    return ctx.noop('No effect 2')\n"
            + "\n",
        "test_action_1", "test_action_2");
    try {
      feedback.run(workdir, ImmutableList.of());
      fail();
    } catch (EmptyChangeException expected) {
      assertThat(expected).hasMessageThat()
          .contains(
              "Feedback migration 'default' was noop. "
                  + "Detailed messages: [No effect 1, No effect 2]");
    }
    console
        .assertThat()
        .equalsNext(MessageType.INFO, "Action 'test_action_1' returned noop: No effect 1")
        .equalsNext(MessageType.INFO, "Action 'test_action_2' returned noop: No effect 2");
  }

  @Test
  public void testErrorResultThrowsValidationException() throws Exception {
    Feedback feedback = feedback(
        ""
            + "def test_action(ctx):\n"
            + "    return ctx.error('This is an error')\n"
            + "\n",
        "test_action");
    try {
      feedback.run(workdir, ImmutableList.of());
      fail();
    } catch (ValidationException expected) {
      assertThat(expected).hasMessageThat()
          .contains(
              "Feedback migration 'default' action 'test_action' returned error: "
                  + "This is an error. Aborting execution.");
    }
    console.assertThat()
        .equalsNext(MessageType.ERROR, "Action 'test_action' returned error: This is an error");
  }

  @Test
  public void testErrorResultAbortsExecution() throws Exception {
    Feedback feedback = feedback(
        ""
            + "def test_action_1(ctx):\n"
            + "    return ctx.error('This is an error')\n"
            + "\n"
            + "def test_action_2(ctx):\n"
            + "    return ctx.success()\n"
            + "\n", "test_action_1", "test_action_2");
    try {
      feedback.run(workdir, ImmutableList.of());
      fail();
    } catch (ValidationException expected) {
      assertThat(expected).hasMessageThat()
          .contains(
              "Feedback migration 'default' action 'test_action_1' returned error: "
                  + "This is an error. Aborting execution.");
    }
    console.assertThat()
        .equalsNext(MessageType.ERROR, "Action 'test_action_1' returned error: This is an error")
        .containsNoMoreMessages();
  }

  @Test
  public void testNoopSuccessReturnsSuccess() throws Exception {
    Feedback feedback = feedback(
        ""
            + "def test_action_1(ctx):\n"
            + "    return ctx.noop('No effect')\n"
            + "\n"
            + "def test_action_2(ctx):\n"
            + "    return ctx.success()\n"
            + "\n", "test_action_1", "test_action_2");
    feedback.run(workdir, ImmutableList.of());
    console
        .assertThat()
        .equalsNext(MessageType.INFO, "Action 'test_action_1' returned noop: No effect")
        .equalsNext(MessageType.INFO, "Action 'test_action_2' returned success")
        .containsNoMoreMessages();
  }

  @Test
  public void testErrorResultEmptyMsg() throws Exception {
    Feedback feedback = feedback(
        ""
            + "def test_action(ctx):\n"
            + "    result = ctx.error()\n"
            + "\n",
        "test_action");
    try {
      feedback.run(workdir, ImmutableList.of());
      fail();
    } catch (ValidationException expected) {
      assertThat(expected.getMessage())
          .matches(".*parameter 'msg' has no default value, in method.*error\\(\\).*");
    }
  }

  @Test
  public void testEvalExceptionIncludesLocation() throws Exception {
    Feedback feedback = feedback(
        ""
            + "def test_action(ctx):\n"
            + "    result = ctx.foo()\n"
            + "\n",
        "test_action");
    try {
      feedback.run(workdir, ImmutableList.of());
      fail();
    } catch (ValidationException expected) {
      assertThat(expected.getMessage())
          .contains(
              "Error while executing the skylark transformation test_action: type "
                  + "'feedback.context' has no method foo(). Location: copy.bara.sky:2:14");
    }
  }

  @Test
  public void testDestinationEffects() throws Exception {
    Feedback feedback = feedback(
          ""
              + "def test_action(ctx):\n"
              + "    ctx.record_effect("
              + "      'Some effect',\n"
              + "      [ctx.origin.new_origin_ref('foo')],\n"
              + "      ctx.destination.new_destination_ref('bar'))\n"
              + "    return ctx.success()\n"
              + "\n",
        "test_action");
    feedback.run(workdir, ImmutableList.of());
    console.assertThat().equalsNext(MessageType.INFO, "Action 'test_action' returned success");

    assertThat(eventMonitor.changeMigrationStartedEventCount()).isEqualTo(1);
    assertThat(eventMonitor.changeMigrationFinishedEventCount()).isEqualTo(1);

    ChangeMigrationFinishedEvent event =
        Iterables.getOnlyElement(eventMonitor.changeMigrationFinishedEvents);
    DestinationEffect effect = Iterables.getOnlyElement(event.getDestinationEffects());
    assertThat(effect.getSummary()).isEqualTo("Some effect");
    assertThat(effect.getOriginRefs()).hasSize(1);
    assertThat(effect.getOriginRefs().get(0).getRef()).isEqualTo("foo");
    DestinationRef destinationRef = effect.getDestinationRef();
    assertThat(destinationRef.getId()).isEqualTo("bar");
    assertThat(destinationRef.getType()).isEqualTo("dummy_endpoint");
    assertThat(destinationRef.getUrl()).isNull();
    assertThat(effect.getErrors()).isEmpty();
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
            + "\n",
        "test_action");
  }

  private Feedback feedback(String actionsCode, String... actionNames)
      throws IOException, ValidationException {
    String config =
        actionsCode
            + "\n"
            + "core.feedback(\n"
            + "    name = 'default',\n"
            + "    origin = testing.dummy_trigger(),\n"
            + "    destination = testing.dummy_endpoint(),\n"
            + "    actions = [" + Joiner.on(',').join(actionNames) + "],\n"
            + ")\n"
            + "\n";
    System.err.println(config);
    return (Feedback) loadConfig(config).getMigration("default");
  }

  private Config loadConfig(String content) throws IOException, ValidationException {
    return skylark.loadConfig(
        new MapConfigFile(
            ImmutableMap.of("copy.bara.sky", content.getBytes(UTF_8)), "copy.bara.sky"));
  }
}
