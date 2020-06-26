/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.copybara.buildozer;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.copybara.Transformation;
import com.google.copybara.buildozer.testing.BuildozerTesting;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.util.console.Message;
import com.google.copybara.util.console.testing.TestingConsole;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class BuildozerDeleteTest {

  private OptionsBuilder options;
  private Path checkoutDir;
  private TestingConsole console;
  private SkylarkTestExecutor skylark;

  @Before
  public void setup() throws Exception {
    console = new TestingConsole();
    options = new OptionsBuilder();
    options.setConsole(console);
    BuildozerTesting.enable(options);
    checkoutDir = Files.createTempDirectory("BuildozerDeleteTest");
    skylark = new SkylarkTestExecutor(options);
  }

  private void transform(Transformation transformation) throws Exception {
    transformation.transform(TransformWorks.of(checkoutDir, "test msg", console));
  }

  @Test
  public void describeReversiblyDelete() throws Exception {
    BuildozerDelete delete = skylark.eval("d", "d = "
        + "buildozer.delete("
        + "    target = 'foo/bar:baz',"
        + "    rule_type = 'java_library',"
        + ")");
    assertThat(delete.describe()).contains("buildozer.delete");
  }

  @Test
  public void deleteWithPositionalTargetArgument() throws Exception {
    BuildozerDelete delete = skylark.eval("d",
        "d = buildozer.delete('foo/bar:delete_me')");
    Files.createDirectories(checkoutDir.resolve("foo/bar"));
    String original = ""
        + "java_binary(name = 'keep')\n"
        + "py_library(name = 'delete_me')\n";
    Files.write(checkoutDir.resolve("foo/bar/BUILD"), original.getBytes(UTF_8));
    transform(delete);
    assertThatPath(checkoutDir)
        .containsFile("foo/bar/BUILD", "java_binary(name = \"keep\")\n");
  }

  @Test
  public void errorForCommandsWithoutRuleType() {
    skylark.evalFails(""
        + "buildozer.delete("
        + "    target = 'foo/bar:delete_me',"
        + "    recreate_commands = ['set foo bar'],"
        + ")",
        "'recreate_commands' is only used for reversible buildozer[.]delete");
  }

  @Test
  public void errorForBeforeWithoutRuleType() {
    skylark.evalFails(""
        + "buildozer.delete("
        + "    target = 'foo/bar:delete_me',"
        + "    before = 'bar',"
        + ")",
        "'before' is only used for reversible buildozer[.]delete");
  }

  @Test
  public void errorForAfterWithoutRuleType() {
    skylark.evalFails(""
        + "buildozer.delete("
        + "    target = 'foo/bar:delete_me',"
        + "    after = 'bar',"
        + ")",
        "'after' is only used for reversible buildozer[.]delete");
  }

  @Test
  public void notReversibleWhenRuleTypeMissing() {
    skylark.evalFails("core.reverse([buildozer.delete('foo/bar:delete_me')])",
        "This buildozer[.]delete is not reversible");
  }

  @Test
  public void targetRequired() {
    skylark.evalFails(
        "buildozer.delete(rule_type = 'java_library')\n",
        "missing 1 required positional argument: target");
  }

  @Test
  public void errorForSpecifyingBeforeAndAfterInRecreateArgs() {
    skylark.evalFails(""
        + "buildozer.delete("
        + "    target = 'foo/bar:baz',"
        + "    rule_type = 'py_binary',"
        + "    before = 'foo',"
        + "    after = 'bar',"
        + ")",
        "cannot specify both 'before' and 'after' in the target create arguments");
  }

  @Test
  public void createByReversing() throws Exception {
    BuildozerDelete delete = skylark.eval("d", "d = "
        + "buildozer.delete("
        + "    target = 'foo/bar:baz',"
        + "    rule_type = 'proto_library',"
        + ")");

    Files.createDirectories(checkoutDir.resolve("foo/bar"));
    Files.write(checkoutDir.resolve("foo/bar/BUILD"),
        "java_binary(name = \"blah\")\n".getBytes(UTF_8));

    transform(delete.reverse());
    assertThatPath(checkoutDir)
        .containsFile("foo/bar/BUILD", ""
            + "java_binary(name = \"blah\")\n"
            + "\n"
            + "proto_library(name = \"baz\")\n");
  }

  private void checkCanDeleteTarget(String extraArgs) throws Exception {
    BuildozerDelete delete = skylark.eval("d", "d = "
        + "buildozer.delete(target = 'foo/bar:baz'," + extraArgs + ")");
    Files.createDirectories(checkoutDir.resolve("foo/bar"));
    String original = ""
        + "proto_library(name = 'baz', foo_attr = 42)\n"
        + "java_binary(name = 'blah')\n";
    Files.write(checkoutDir.resolve("foo/bar/BUILD"), original.getBytes(UTF_8));

    transform(delete);
    assertThatPath(checkoutDir)
        .containsFile("foo/bar/BUILD", "java_binary(name = \"blah\")\n");
  }

  @Test
  public void deleteTarget() throws Exception {
    checkCanDeleteTarget("rule_type = 'proto_library',");
  }

  @Test
  public void deleteTargetWithNonReversibleTransform() throws Exception {
    checkCanDeleteTarget(/*extraArgs*/ "");
  }

  @Test
  public void deleteRootPkgTarget() throws Exception {
    BuildozerDelete delete = skylark.eval("d", "d = "
        + "buildozer.delete(target = ':baz'," + "rule_type = 'proto_library'," + ")");
    String original = ""
        + "proto_library(name = 'baz', foo_attr = 42)\n"
        + "java_binary(name = 'blah')\n";
    Files.write(checkoutDir.resolve("BUILD"), original.getBytes(UTF_8));

    transform(delete);
    assertThatPath(checkoutDir).containsFile("BUILD", "java_binary(name = \"blah\")\n");
  }

  @Test
  public void reverseToPopulateAttributesWithCommands() throws Exception {
    BuildozerCreate create = skylark.eval("c", "c = "
        + "core.reverse([buildozer.delete("
        + "    target = 'foo/bar:baz',"
        + "    rule_type = 'proto_library',"
        + "    recreate_commands = ["
        + "        'set config \":android_proto_config\"',"
        + "        buildozer.cmd('set header_outsNOT_AS_LIST tf_android_core_proto_headers()'),"
        + "        'move header_outsNOT_AS_LIST header_outs *',"
        + "        buildozer.cmd('set prefix_dir \"protos\"'),"
        + "    ],"
        + ")])[0]");

    Files.createDirectories(checkoutDir.resolve("foo/bar"));
    String original = "java_binary(name = 'blah')\n";
    Files.write(checkoutDir.resolve("foo/bar/BUILD"), original.getBytes(UTF_8));

    transform(create);
    assertThatPath(checkoutDir)
        .containsFile("foo/bar/BUILD", ""
            + "java_binary(name = \"blah\")\n"
            + "\n"
            + "proto_library(\n"
            + "    name = \"baz\",\n"
            + "    config = \":android_proto_config\",\n"
            + "    header_outs = tf_android_core_proto_headers(),\n"
            + "    prefix_dir = \"protos\",\n"
            + ")\n");
  }

  @Test
  public void canReverseToCreateBeforeSomeTarget() throws Exception {
    BuildozerCreate create = skylark.eval("c", "c = "
        + "core.reverse([buildozer.delete("
        + "    target = 'foo/bar:baz',"
        + "    rule_type = 'proto_library',"
        + "    before = 'target_knee',"
        + ")])[0]");

    Files.createDirectories(checkoutDir.resolve("foo/bar"));
    String original = ""
        + "java_binary(name = 'target_itchy')\n"
        + "java_binary(name = 'target_knee')\n"
        + "java_binary(name = 'target_sun')\n";
    Files.write(checkoutDir.resolve("foo/bar/BUILD"), original.getBytes(UTF_8));

    transform(create);

    assertThatPath(checkoutDir)
        .containsFile("foo/bar/BUILD", ""
            + "java_binary(name = \"target_itchy\")\n"
            + "\n"
            + "proto_library(name = \"baz\")\n"
            + "\n"
            + "java_binary(name = \"target_knee\")\n"
            + "\n"
            + "java_binary(name = \"target_sun\")\n");
  }

  @Test
  // This is a test for the transformation of death in b/62372670
  public void errorForInvalidCommand() throws Exception {
    // Because of http://b/69386431 we don't get correct errors on keep going
    options.workflowOptions.ignoreNoop = false;
    BuildozerTesting.enable(options);

    BuildozerDelete del = skylark.eval("c", "c = "
        + "buildozer.delete("
        + "    target = 'foo/bar:baz',"
        + "    rule_type = 'proto_library',"
        + "    recreate_commands = ['set config :baz',"
        + "        'add header_outs //google/protobuf:any.proto',"
        + "        'set header_outsNOT_AS_LIST tf_android_core_proto_headers(CORE_PROTO_SRCS)',"
        + "        'move header_outsNOT_AS_LIST header_outs *'],"
        + ")");
    Files.createDirectories(checkoutDir.resolve("foo/bar"));
    Files.write(checkoutDir.resolve("foo/bar/BUILD"),
        "proto_library(name='baz')".getBytes(UTF_8));

    transform(del);
    assertThrows(ValidationException.class, () -> transform(del.reverse()));
    console.assertThat()
        .onceInLog(Message.MessageType.ERROR,
            ".*header_outsNOT_AS_LIST is not a simple list.*");
  }

  @Test
  public void canReverseToCreateAfterSomeTarget() throws Exception {
    BuildozerCreate create = skylark.eval("c", "c = "
        + "core.reverse([buildozer.delete("
        + "    target = 'foo/bar:baz',"
        + "    rule_type = 'proto_library',"
        + "    after = 'target2',"
        + ")])[0]");

    Files.createDirectories(checkoutDir.resolve("foo/bar"));
    String original = ""
        + "java_binary(name = 'target1')\n"
        + "java_binary(name = 'target2')\n"
        + "java_binary(name = 'target3')\n";
    Files.write(checkoutDir.resolve("foo/bar/BUILD"), original.getBytes(UTF_8));

    transform(create);

    assertThatPath(checkoutDir)
        .containsFile("foo/bar/BUILD", ""
            + "java_binary(name = \"target1\")\n"
            + "\n"
            + "java_binary(name = \"target2\")\n"
            + "\n"
            + "proto_library(name = \"baz\")\n"
            + "\n"
            + "java_binary(name = \"target3\")\n");
  }
}
