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
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.config.Config;
import com.google.copybara.config.MapConfigFile;
import com.google.copybara.config.Migration;
import com.google.copybara.effect.DestinationEffect;
import com.google.copybara.effect.DestinationEffect.DestinationRef;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.monitor.EventMonitor.ChangeMigrationFinishedEvent;
import com.google.copybara.testing.DummyTrigger;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TestingEventMonitor;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ActionMigrationTest {

  private SkylarkTestExecutor skylark;
  private TestingConsole console;
  private TestingEventMonitor eventMonitor;
  private DummyTrigger dummyTrigger;
  private Path workdir;
  private OptionsBuilder options;

  @Before
  public void setup() throws Exception {
    workdir = Jimfs.newFileSystem().getPath("/");
    console = new TestingConsole(/*verbose=*/ false);
    eventMonitor = new TestingEventMonitor();
    options = new OptionsBuilder();
    options.setConsole(console);
    options.general.enableEventMonitor("justTesting", eventMonitor);
    dummyTrigger = new DummyTrigger();
    options.testingOptions.feedbackTrigger = dummyTrigger;
    skylark = new SkylarkTestExecutor(options);
  }

  @Test
  public void testParsing() throws Exception {
    ActionMigration actionMigration = loggingFeedback();
    assertThat(actionMigration.getName()).isEqualTo("default");
    assertThat(actionMigration.getModeString()).isEqualTo("feedback");
    assertThat(actionMigration.getMainConfigFile()).isNotNull();
    assertThat(actionMigration.getOriginDescription()).isEqualTo(dummyTrigger.describe());
    assertThat(actionMigration.getDestinationDescription()).isEqualTo(dummyTrigger.describe());
    assertThat(actionMigration.getActionsDescription())
        .containsEntry("test_action", ImmutableSetMultimap.of());
  }

  @Test
  public void testDescription() throws Exception {
    String config = ""
        + "def test_action(ctx):\n"
        + "    return ctx.success()\n"
        + "\n"
        + "core.feedback(\n"
        + "    name = 'default',\n"
        + "    description = 'Do foo with bar',\n"
        + "    origin = testing.dummy_trigger(),\n"
        + "    destination = testing.dummy_endpoint(),\n"
        + "    actions = [test_action],\n"
        + ")";
    ActionMigration actionMigration = (ActionMigration) loadConfig(config).getMigration("default");
    assertThat(actionMigration.getDescription()).isEqualTo("Do foo with bar");
  }


  @Test
  public void testDescribeActions() throws Exception {
    ActionMigration actionMigration = feedback(
        ""
            + "def _action1(ctx):\n"
            + "  return ctx.success()\n"
            + "\n"
            + "def _action2(ctx):\n"
            + "  return ctx.success()\n"
            + "\n"
            + "def action1(param1):\n"
            + "  return core.action(\n"
            + "      impl = _action1,\n"
            + "      params = {\n"
            + "          'param1': param1,\n"
            + "      },\n"
            + "  )"
            + "\n"
            + "def action2(param1, param2):\n"
            + "  return core.action(\n"
            + "      impl = _action2,\n"
            + "      params = {\n"
            + "          'param1': param1,\n"
            + "          'param2': param2,\n"
            + "      },\n"
            + "  )"
            + "\n",
        "action1(param1 = 'foo')", "action2(param1 = True, param2 = 'Bar')");
    assertThat(actionMigration.getActionsDescription())
        .isEqualTo(
            ImmutableSetMultimap.builder()
                .put("_action1", ImmutableSetMultimap.of("param1", "foo"))
                .put("_action2", ImmutableSetMultimap.of("param1", "true", "param2", "Bar"))
                .build());
  }

  @Test
  public void testAction() throws Exception {
    ActionMigration actionMigration = loggingFeedback();
    actionMigration.run(workdir, ImmutableList.of("12345"));
    console.assertThat().onceInLog(MessageType.INFO, "Ref: 12345");
    console.assertThat().onceInLog(MessageType.INFO, "Feedback name: default");
    console.assertThat().onceInLog(MessageType.INFO, "Action name: test_action");
  }

  @Test
  public void testSingleAction() throws Exception {
    String config =
        (""
            + "def test_action(ctx):\n"
            + "    for ref in ctx.refs:\n"
            + "      ctx.console.info('Ref: ' + ref)\n"
            + "    return ctx.success()\n"
            + "\n")
            + "\n"
            + "core.feedback(\n"
            + "    name = 'default',\n"
            + "    origin = testing.dummy_trigger(),\n"
            + "    destination = testing.dummy_endpoint(),\n"
            + "    action = test_action,\n"
            + ")\n"
            + "\n";
    System.err.println(config);
    ActionMigration actionMigration = (ActionMigration) loadConfig(config).getMigration("default");
    actionMigration.run(workdir, ImmutableList.of("12345"));
    console.assertThat().onceInLog(MessageType.INFO, "Ref: 12345");
  }

  @Test
  public void testNullSourceRef() throws Exception {
    ActionMigration actionMigration = loggingFeedback();
    actionMigration.run(workdir, ImmutableList.of());
    console.assertThat()
        .equalsNext(MessageType.INFO, "Action 'test_action' returned success")
        .containsNoMoreMessages();
  }

  @Test
  public void testMultipleSourceRefs() throws Exception {
    ActionMigration actionMigration = loggingFeedback();
    actionMigration.run(workdir, ImmutableList.of("12345", "67890"));
    console
        .assertThat()
        .matchesNext(MessageType.INFO, ".*Ref: 12345")
        .matchesNext(MessageType.INFO, ".*Feedback name: default")
        .matchesNext(MessageType.INFO, ".*Action name: test_action")
        .matchesNext(MessageType.INFO, ".*Ref: 67890")
        .matchesNext(MessageType.INFO, ".*Feedback name: default")
        .matchesNext(MessageType.INFO, ".*Action name: test_action")
        .matchesNext(MessageType.INFO, ".*Action 'test_action' returned success.*");
  }

  @Test
  public void testRefReturnsFirst() throws Exception {
    ActionMigration actionMigration =
        feedback(
            ""
                + "def test_action(ctx):\n"
                + "    ctx.console.info('Ref: ' + str(ctx.refs[0]))\n"
                + "    return ctx.success()\n"
                + "\n",
            "test_action");
    actionMigration.run(workdir, ImmutableList.of("12345", "67890"));
    console
        .assertThat()
        .matchesNext(MessageType.INFO, ".*Ref: 12345")
        .matchesNext(MessageType.INFO, ".*Action 'test_action' returned success.*")
        .containsNoMoreMessages();
  }

  @Test
  public void verifyCliLabels() throws Exception {
    options.general.setCliLabelsForTest(ImmutableMap.of("foo", "value"));
    ActionMigration actionMigration =
        feedback(
            ""
                + "def test_action(ctx):\n"
                + "    foo = ctx.cli_labels['foo']\n"
                + "    ctx.console.info('foo is: ' + foo)\n"
                + "    return ctx.success()\n"
                + "\n",
            "test_action");
    actionMigration.run(workdir, ImmutableList.of());
    console
        .assertThat()
        .matchesNext(MessageType.INFO, ".*foo is: value")
        .matchesNext(MessageType.INFO, ".*Action 'test_action' returned success.*")
        .containsNoMoreMessages();
  }

  @Test
  public void testRefReturnsNone() throws Exception {
    ActionMigration actionMigration =
        feedback(
            ""
                + "def test_action(ctx):\n"
                + "    ref = None\n"
                + "    if len(ctx.refs) > 0:\n"
                + "      ref = ctx.refs[0]\n"
                + "    ctx.console.info('Ref: '+ str(ref))\n"
                + "    return ctx.success()\n"
                + "\n",
            "test_action");
    actionMigration.run(workdir, ImmutableList.of());
    console
        .assertThat()
        .matchesNext(MessageType.INFO, ".*Ref: None")
        .matchesNext(MessageType.INFO, ".*Action 'test_action' returned success.*")
        .containsNoMoreMessages();
  }

  @Test
  public void testActionsMustReturnResult() throws Exception {
    ActionMigration migration = feedback(
        ""
            + "def test_action(ctx):\n"
            + "    ctx.console.info('Bad action')\n"
            + "\n",
        "test_action");
    ValidationException expected =
        assertThrows(ValidationException.class, () -> migration.run(workdir, ImmutableList.of()));
    assertThat(expected)
        .hasMessageThat()
        .contains(
            "Actions must return a result via built-in functions: success(), "
                + "error(), noop() return, but 'test_action' returned: None");
  }

  @Test
  public void testSuccessResult() throws Exception {
    ActionMigration actionMigration = feedback(
        ""
            + "def test_action(ctx):\n"
            + "    return ctx.success()\n",
        "test_action");
    actionMigration.run(workdir, ImmutableList.of());
    console.assertThat().equalsNext(MessageType.INFO, "Action 'test_action' returned success");
  }

  @Test
  public void testNoActionIsAUserError() throws Exception {
    assertThat(assertThrows(ValidationException.class, () -> feedback("")))
        .hasMessageThat()
        .contains(
            "'action' is a required field");
  }

  @Test
  public void testNoopResultThrowsEmptyChangeException() throws Exception {
    ActionMigration migration = feedback(
        ""
            + "def test_action_1(ctx):\n"
            + "    return ctx.noop('No effect 1')\n"
            + "\n"
            + "def test_action_2(ctx):\n"
            + "    return ctx.noop('No effect 2')\n"
            + "\n",
        "test_action_1", "test_action_2");
    EmptyChangeException expected =
        assertThrows(EmptyChangeException.class, () -> migration.run(workdir, ImmutableList.of()));
    assertThat(expected)
        .hasMessageThat()
        .contains(
            "Feedback migration 'default' was noop. "
                + "Detailed messages: [No effect 1, No effect 2]");
    console
        .assertThat()
        .equalsNext(MessageType.INFO, "Action 'test_action_1' returned noop: No effect 1")
        .equalsNext(MessageType.INFO, "Action 'test_action_2' returned noop: No effect 2");
  }

  @Test
  public void testErrorResultThrowsValidationException() throws Exception {
    ActionMigration migration = feedback(
        ""
            + "def test_action(ctx):\n"
            + "    return ctx.error('This is an error')\n",
        "test_action");
    ValidationException expected =
        assertThrows(ValidationException.class, () -> migration.run(workdir, ImmutableList.of()));
    assertThat(expected)
        .hasMessageThat()
        .contains(
            "Feedback migration 'default' action 'test_action' returned error: "
                + "This is an error. Aborting execution.");
    console
        .assertThat()
        .equalsNext(MessageType.ERROR, "Action 'test_action' returned error: This is an error");
  }

  @Test
  public void testErrorResultAbortsExecution() throws Exception {
    ActionMigration migration = feedback(
        ""
            + "def test_action_1(ctx):\n"
            + "    return ctx.error('This is an error')\n"
            + "\n"
            + "def test_action_2(ctx):\n"
            + "    return ctx.success()\n"
        , "test_action_1", "test_action_2");
    ValidationException expected =
        assertThrows(ValidationException.class, () -> migration.run(workdir, ImmutableList.of()));
    assertThat(expected)
        .hasMessageThat()
        .contains(
            "Feedback migration 'default' action 'test_action_1' returned error: "
                + "This is an error. Aborting execution.");
    console
        .assertThat()
        .equalsNext(MessageType.ERROR, "Action 'test_action_1' returned error: This is an error")
        .containsNoMoreMessages();
  }

  @Test
  public void testNoopSuccessReturnsSuccess() throws Exception {
    ActionMigration actionMigration = feedback(
        ""
            + "def test_action_1(ctx):\n"
            + "    return ctx.noop('No effect')\n"
            + "\n"
            + "def test_action_2(ctx):\n"
            + "    return ctx.success()\n"
            + "\n", "test_action_1", "test_action_2");
    actionMigration.run(workdir, ImmutableList.of());
    console
        .assertThat()
        .equalsNext(MessageType.INFO, "Action 'test_action_1' returned noop: No effect")
        .equalsNext(MessageType.INFO, "Action 'test_action_2' returned success")
        .containsNoMoreMessages();
  }

  @Test
  public void testErrorResultEmptyMsg() throws Exception {
    ActionMigration migration = feedback(
        ""
            + "def test_action(ctx):\n"
            + "    result = ctx.error()\n"
            + "\n",
        "test_action");
    ValidationException expected =
        assertThrows(ValidationException.class, () -> migration.run(workdir, ImmutableList.of()));
    assertThat(expected).hasMessageThat().contains("missing 1 required positional argument: msg");
  }

  @Test
  public void testEvalExceptionIncludesLocation() throws Exception {
    ActionMigration migration = feedback(
        ""
            + "def test_action(ctx):\n"
            + "    result = ctx.foo()\n"
            + "\n",
        "test_action");
    ValidationException ex =
        assertThrows(ValidationException.class, () -> migration.run(workdir, ImmutableList.of()));
    assertThat(ex)
        .hasMessageThat()
        .contains("Error while executing the skylark transformation test_action");
    assertThat(ex)
        .hasMessageThat()
        .contains("File \"copy.bara.sky\", line 2, column 17, in test_action");
    assertThat(ex)
        .hasMessageThat()
        .contains("'feedback.context' value has no field or method 'foo'");
  }

  @Test
  public void testDestinationEffects() throws Exception {
    runAndVerifyDestinationEffects(""
        + "def test_action(ctx):\n"
        + "    ctx.record_effect("
        + "      'Some effect',\n"
        + "      [ctx.origin.new_origin_ref('foo')],\n"
        + "      ctx.destination.new_destination_ref(ref = 'bar', type = 'some_type'))\n"
        + "    return ctx.success()\n"
        + "\n", ImmutableList.of());
  }

  @Test
  public void testAccessDestinationThruEndpoints() throws Exception {
    runAndVerifyDestinationEffects(""
        + "def test_action(ctx):\n"
        + "    ctx.record_effect("
        + "      'Some effect',\n"
        + "      [ctx.origin.new_origin_ref('foo')],\n"
        + "      ctx.endpoints.destination.new_destination_ref(ref = 'bar', type = 'some_type'))\n"
        + "    return ctx.success()\n"
        + "\n", ImmutableList.of());
  }

  @Test
  public void testDestinationEffectsWithErrors() throws Exception {
    runAndVerifyDestinationEffects(
        ""
            + "def test_action(ctx):\n"
            + "    ctx.record_effect("
            + "      'Some effect',\n"
            + "      [ctx.origin.new_origin_ref('foo')],\n"
            + "      ctx.destination.new_destination_ref(ref = 'bar', type = 'some_type'), "
            + "      ['error1', 'error2'])\n"
            + "    return ctx.success()\n"
            + "\n", ImmutableList.of("error1", "error2"));
  }

  @Test
  public void testDestinationEffectsWithUrl() throws Exception {
    runAndVerifyDestinationEffects(""
        + "def test_action(ctx):\n"
        + "    ctx.record_effect("
        + "      'Some other effect',\n"
        + "      [ctx.origin.new_origin_ref('origin_ref')],\n"
        + "      ctx.destination.new_destination_ref("
            + "    'dest_ref', 'custom_type', 'https://foo.bar'))\n"
        + "    return ctx.success()\n"
        + "\n", ImmutableList.of(),
        "Some other effect",
        "origin_ref",
        "dest_ref",
        "custom_type",
        "https://foo.bar");
  }

  private void runAndVerifyDestinationEffects(
      String actionsCode, ImmutableList<String> expectedErrors) throws Exception {
    runAndVerifyDestinationEffects(
        actionsCode,
        expectedErrors,
        "Some effect",
        "foo",
        "bar",
        "some_type",
        /*expectedDestUrl=*/ null);
  }

  private void runAndVerifyDestinationEffects(
      String actionsCode,
      ImmutableList<String> expectedErrors,
      String expectedSummary,
      String expectedOriginRef,
      String expectedDestRef,
      String expectedDestType,
      @Nullable String expectedDestUrl)
      throws IOException, ValidationException, RepoException {
    ActionMigration actionMigration = feedback(actionsCode, "test_action");
    actionMigration.run(workdir, ImmutableList.of());
    console.assertThat().equalsNext(MessageType.INFO, "Action 'test_action' returned success");

    assertThat(eventMonitor.changeMigrationStartedEventCount()).isEqualTo(1);
    assertThat(eventMonitor.changeMigrationFinishedEventCount()).isEqualTo(1);

    ChangeMigrationFinishedEvent event =
        Iterables.getOnlyElement(eventMonitor.changeMigrationFinishedEvents);
    DestinationEffect effect = Iterables.getOnlyElement(event.getDestinationEffects());
    assertThat(effect.getSummary()).isEqualTo(expectedSummary);
    assertThat(effect.getOriginRefs()).hasSize(1);
    assertThat(effect.getOriginRefs().get(0).getRef()).isEqualTo(expectedOriginRef);
    DestinationRef destinationRef = effect.getDestinationRef();
    assertThat(destinationRef.getId()).isEqualTo(expectedDestRef);
    assertThat(destinationRef.getType()).isEqualTo(expectedDestType);
    if (expectedDestUrl == null) {
      assertThat(destinationRef.getUrl()).isNull();
    } else {
      assertThat(destinationRef.getUrl()).isEqualTo(expectedDestUrl);
    }
    assertThat(effect.getErrors()).containsExactlyElementsIn(expectedErrors);
  }

  private ActionMigration loggingFeedback() throws IOException, ValidationException {
    return feedback(
        ""
            + "def test_action(ctx):\n"
            + "    for ref in ctx.refs:\n"
            + "      ctx.console.info('Ref: ' + ref)\n"
            + "      ctx.console.info('Feedback name: ' + ctx.feedback_name)\n"
            + "      ctx.console.info('Action name: ' + ctx.action_name)\n"
            + "    return ctx.success()\n"
            + "\n",
        "test_action");
  }

  private ActionMigration feedback(String actionsCode, String... actionNames)
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
    return (ActionMigration) loadConfig(config).getMigration("default");
  }

  private Config loadConfig(String content) throws IOException, ValidationException {
    return skylark.loadConfig(
        new MapConfigFile(
            ImmutableMap.of("copy.bara.sky", content.getBytes(UTF_8)), "copy.bara.sky"));
  }

  @Test
  public void testInvalidMigrationName() {
    skylark.evalFails(
        ""
            + "core.feedback(\n"
            + "    name = 'foo| bad;name',\n"
            + "    origin = testing.dummy_trigger(),\n"
            + "    destination = testing.dummy_endpoint(),\n"
            + "    action = lambda ctx: ctx.console.info('Hello'),\n"
            + ")\n",
        ".*Migration name 'foo[|] bad;name' doesn't conform to expected pattern.*");
  }

  @Test
  public void testActionMigration_WithFileSystem() throws Exception {
    String config = ""
            + "def test_action(ctx):\n"
            + "    p = ctx.fs.new_path('foo/bar')\n"
            + "    ctx.fs.write_path(p, 'hello')\n"
            + "    for f in ctx.fs.list(glob(['**'])):\n"
            + "        ctx.console.info('FOUND: ' + f.path)\n"
            + "    return ctx.success()\n"
            + "\n"
            + "core.action_migration(\n"
            + "    name = 'default',\n"
            + "    origin = testing.dummy_trigger(),\n"
            + "    endpoints = struct(\n"
            + "        destination = testing.dummy_endpoint()\n"
            + "    ),"
            + "    filesystem = True,\n"
            + "    action = test_action,\n"
            + ")\n"
            + "\n";
    Migration actionMigration = loadConfig(config).getMigration("default");
    actionMigration.run(workdir, ImmutableList.of("12345"));
    assertThatPath(workdir)
        .containsFile("foo/bar", "hello")
        .containsNoMoreFiles();
    console.assertThat().onceInLog(MessageType.INFO, "FOUND: foo/bar");
  }

  @Test
  public void testActionMigration_WithoutFileSystem() throws Exception {
    String config = ""
            + "def test_action(ctx):\n"
            + "    p = ctx.fs.new_path('foo/bar')\n"
            + "    return ctx.success()\n"
            + "\n"
            + "core.action_migration(\n"
            + "    name = 'default',\n"
            + "    origin = testing.dummy_trigger(),\n"
            + "    endpoints = struct(\n"
            + "        destination = testing.dummy_endpoint()\n"
            + "    ),"
            + "    action = test_action,\n"
            + ")\n"
            + "\n";
    Migration actionMigration = loadConfig(config).getMigration("default");
    assertThat(assertThrows(ValidationException.class, () ->
        actionMigration.run(workdir, ImmutableList.of("12345"))))
        .hasMessageThat().contains("Migration 'default' doesn't have access to the filesystem");
  }

  @Test
  public void testActionMigration_UseDestination() throws Exception {
    String config = ""
            + "def test_action(ctx):\n"
            + "    ctx.endpoints.destination.message('hello')\n"
            + "    ctx.console.info('FOUND: ' + ctx.endpoints.destination.get_messages[0])\n"
            + "    return ctx.success()\n"
            + "\n"
            + "core.action_migration(\n"
            + "    name = 'default',\n"
            + "    origin = testing.dummy_trigger(),\n"
            + "    endpoints = struct(\n"
            + "        destination = testing.dummy_endpoint()\n"
            + "    ),"
            + "    action = test_action,\n"
            + ")\n"
            + "\n";
    Migration actionMigration = loadConfig(config).getMigration("default");
    actionMigration.run(workdir, ImmutableList.of("12345"));
    console.assertThat().onceInLog(MessageType.INFO, "FOUND: hello");
  }

  // TODO(b/269526710): Remove test when we remove this limitation
  @Test
  public void testActionMigration_UseMoreThanOneDestination() throws Exception {
    String config = ""
            + "def test_action(ctx):\n"
            + "    return ctx.success()\n"
            + "\n"
            + "core.action_migration(\n"
            + "    name = 'default',\n"
            + "    origin = testing.dummy_trigger(),\n"
            + "    endpoints = struct(\n"
            + "        destination = testing.dummy_endpoint(),\n"
            + "        foo = testing.dummy_endpoint()\n"
            + "    ),"
            + "    action = test_action,\n"
            + ")\n"
            + "\n";
    assertThat(assertThrows(ValidationException.class, () ->
        loadConfig(config).getMigration("default")))
        .hasMessageThat().contains(
            "Temporarily core.action_migration only supports one endpoint called destination");
  }

  @Test
  public void testActionMigration_noopNullMsgDoesNotNPE() throws Exception {
    String config =
        ""
            + "def test_action(ctx):\n"
            + "    return ctx.noop()\n" // no argument for ctx.noop() will return a null messsage
            + "\n"
            + "core.action_migration(\n"
            + "    name = 'default',\n"
            + "    origin = testing.dummy_trigger(),\n"
            + "    endpoints = struct(\n"
            + "        destination = testing.dummy_endpoint(),\n"
            + "    ),"
            + "    action = test_action,\n"
            + ")\n"
            + "\n";
    Migration actionMigration = loadConfig(config).getMigration("default");

    // should be an empty change and not null pointer exception
    assertThrows(
        EmptyChangeException.class, () -> actionMigration.run(workdir, ImmutableList.of("12345")));
  }
}
