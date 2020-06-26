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
import static org.junit.Assert.fail;

import com.google.copybara.Transformation;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.copybara.buildozer.testing.BuildozerTesting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class BuildozerCreateTest {

  private OptionsBuilder options;
  private Path checkoutDir;
  private TestingConsole console;
  private SkylarkTestExecutor skylark;

  @Before
  public void setup() throws IOException {
    console = new TestingConsole();
    options = new OptionsBuilder();
    options.setConsole(console);
    BuildozerTesting.enable(options);
    checkoutDir = Files.createTempDirectory("BuildozerCreateTest");
    skylark = new SkylarkTestExecutor(options);
  }

  private void transform(Transformation transformation) throws Exception {
    transformation.transform(TransformWorks.of(checkoutDir, "test msg", console));
  }

  @Test
  public void requiresTarget() {
    try {
      skylark.eval("c", ""
          + "c = buildozer.create(rule_type = 'java_library', commands = ['set config :foo'])\n");
      fail();
    } catch (ValidationException expected) {}
    console
        .assertThat()
        .onceInLog(MessageType.ERROR, ".*missing 1 required positional argument: target.*");
  }

  @Test
  public void requiresColonInTarget() {
    try {
      skylark.eval("c", ""
          + "c = buildozer.create(target = 'foo/bar', rule_type = 'py_binary')\n");
      fail();
    } catch (ValidationException expected) {}
    console.error("asdf");
    console.assertThat()
        .onceInLog(MessageType.ERROR,
            ".*target must be in the form of [{]PKG[}]:[{]TARGET_NAME[}].*");
  }

  @Test
  public void errorForMultipleColonsInTarget() {
    try {
      skylark.eval("c", ""
          + "c = buildozer.create(target = 'foo:bar:baz', rule_type = 'py_binary')\n");
      fail();
    } catch (ValidationException expected) {}
    console.error("asdf");
    console.assertThat()
        .onceInLog(MessageType.ERROR,
            ".*target must be in the form of [{]PKG[}]:[{]TARGET_NAME[}].*");
  }

  @Test
  public void requiresRuleType() {
    try {
      skylark.eval("c", "c = buildozer.create(target = 'foo/bar:baz')");
      fail();
    } catch (ValidationException expected) {}
    console
        .assertThat()
        .onceInLog(MessageType.ERROR, ".*missing 1 required positional argument: rule_type.*");
  }

  @Test
  public void packageMustBeRelative() {
    try {
      skylark.eval("c",
          "c = buildozer.create(target = '//foo/bar', rule_type = 'py_binary')");
      fail();
    } catch (ValidationException expected) {}
    console.assertThat()
        .onceInLog(MessageType.ERROR,
            ".*target must be relative and not start with '/' or '//'.*");
  }

  @Test
  public void describeIncludesCreate() throws ValidationException {
    BuildozerCreate transform = skylark.eval("c",
        "c = buildozer.create(target = 'foo/bar:baz', rule_type = 'py_binary')");
    assertThat(transform.describe()).contains("buildozer.create");
    assertThat(transform.reverse().describe()).contains("buildozer.delete");
  }

  @Test
  public void describeIncludesTarget() throws ValidationException {
    BuildozerCreate transform = skylark.eval("c",
        "c = buildozer.create(target = 'foo/bar:baz', rule_type = 'py_binary')");
    assertThat(transform.describe()).contains("foo/bar:baz");
    assertThat(transform.reverse().describe()).contains("foo/bar:baz");
  }

  @Test
  public void errorForSpecifyingBeforeAndAfter() {
    try {
      skylark.eval("c", "c = "
          + "buildozer.create("
          + "    target = 'foo/bar:baz',"
          + "    rule_type = 'py_binary',"
          + "    before = 'foo',"
          + "    after = 'bar',"
          + ")");
      fail();
    } catch (ValidationException expected) {}
    console.assertThat()
        .onceInLog(MessageType.ERROR,
            ".*cannot specify both 'before' and 'after' in the target create arguments.*");
  }

  @Test
  public void errorForSpecifyingPackageInBeforeTarget() {
    try {
      skylark.eval("c", "c = "
          + "buildozer.create("
          + "    target = 'foo/bar:baz',"
          + "    rule_type = 'py_binary',"
          + "    before = 'bar:foo',"
          + ")");
      fail();
    } catch (ValidationException expected) {}
    console.assertThat()
        .onceInLog(MessageType.ERROR,
            ".*unexpected : in target name [(]did you include the package by mistake[?][)]"
            + " - 'bar:foo'.*");
  }

  @Test
  public void errorForSpecifyingPackageInAfterTarget() {
    try {
      skylark.eval("c", "c = "
          + "buildozer.create("
          + "    target = 'foo/bar:baz',"
          + "    rule_type = 'py_binary',"
          + "    after = 'foo:bar',"
          + ")");
      fail();
    } catch (ValidationException expected) {}
    console.assertThat()
        .onceInLog(MessageType.ERROR,
            ".*unexpected : in target name [(]did you include the package by mistake[?][)]"
            + " - 'foo:bar'.*");
  }

  @Test
  public void createNewWithoutAttributes() throws Exception {
    BuildozerCreate create = skylark.eval("c", "c = "
        + "buildozer.create("
        + "    target = 'foo/bar:baz',"
        + "    rule_type = 'proto_library',"
        + ")");

    Files.createDirectories(checkoutDir.resolve("foo/bar"));
    Files.write(checkoutDir.resolve("foo/bar/BUILD"), "# initial comment".getBytes(UTF_8));
    transform(create);
    assertThatPath(checkoutDir)
        .containsFile("foo/bar/BUILD", ""
            + "# initial comment\n\n"
            + "proto_library(name = \"baz\")\n");
  }

  @Test
  public void deleteByReversing() throws Exception {
    BuildozerCreate create = skylark.eval("c", "c = "
        + "buildozer.create("
        + "    target = 'foo/bar:baz',"
        + "    rule_type = 'proto_library',"
        + ")");

    Files.createDirectories(checkoutDir.resolve("foo/bar"));
    String original = ""
        + "proto_library(name = 'baz')\n"
        + "java_binary(name = 'blah')\n";
    Files.write(checkoutDir.resolve("foo/bar/BUILD"), original.getBytes(UTF_8));

    transform(create.reverse());
    assertThatPath(checkoutDir)
        .containsFile("foo/bar/BUILD", "java_binary(name = \"blah\")\n");
  }

  @Test
  public void populateAttributesWithCommands() throws Exception {
    BuildozerCreate create = skylark.eval("c", "c = "
        + "buildozer.create("
        + "    target = 'foo/bar:baz',"
        + "    rule_type = 'proto_library',"
        + "    commands = ["
        + "        'set config \":android_proto_config\"',"
        + "        buildozer.cmd('set header_outsNOT_AS_LIST tf_android_core_proto_headers()'),"
        + "        'move header_outsNOT_AS_LIST header_outs *',"
        + "        buildozer.cmd('set prefix_dir \"protos\"'),"
        + "    ],"
        + ")");

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
  public void canCreateBeforeSomeTarget() throws Exception {
    BuildozerCreate create = skylark.eval("c", "c = "
        + "buildozer.create("
        + "    target = 'foo/bar:baz',"
        + "    rule_type = 'proto_library',"
        + "    before = 'target_knee',"
        + ")");

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
  public void canCreateAfterSomeTarget() throws Exception {
    BuildozerCreate create = skylark.eval("c", "c = "
        + "buildozer.create("
        + "    target = 'foo/bar:baz',"
        + "    rule_type = 'proto_library',"
        + "    after = 'target2',"
        + ")");

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

  @Test
  public void errorForInvalidCommandType() throws Exception {
    try {
      skylark.eval("c", "c = "
          + "buildozer.create("
          + "    target = 'foo/bar:baz',"
          + "    rule_type = 'py_binary',"
          + "    commands = [42],"
          + ")");
      fail();
    } catch (ValidationException expected) {}
    console.assertThat()
        .onceInLog(MessageType.ERROR,
            ".*Expected a string or buildozer[.]cmd, but got: 42.*");
  }

  @Test
  public void toStringHasRelativeTo() throws Exception {
    BuildozerCreate create = skylark.eval("c", "c = "
        + "buildozer.create("
        + "    target = 'foo/bar:baz',"
        + "    rule_type = 'py_binary',"
        + "    commands = ['set foo bar'],"
        + "    before = 'xyz',"
        + ")");
    assertThat(create.toString()).contains("before xyz");

    create = skylark.eval("c", "c = "
        + "buildozer.create("
        + "    target = 'foo/bar:baz',"
        + "    rule_type = 'py_binary',"
        + "    commands = ['set foo bar'],"
        + "    after = 'abc',"
        + ")");
    assertThat(create.toString()).contains("after abc");
  }

  @Test
  public void automaticallyCreatesBuildFileForNewPackage() throws Exception {
    // Make sure the package doesn't already exist, since that would invalidate the test.
    assertThatPath(checkoutDir).containsNoMoreFiles();

    BuildozerCreate create = skylark.eval("c", "c = "
        + "buildozer.create("
        + "    target = 'jfjf/ieie:target_itchy',"
        + "    rule_type = 'java_library',"
        + "    commands = ['set config \"foo\"'],"
        + ")");
    transform(create);
    assertThatPath(checkoutDir)
        .containsFile("jfjf/ieie/BUILD", ""
            + "java_library(\n"
            + "    name = \"target_itchy\",\n"
            + "    config = \"foo\",\n"
            + ")\n")
        .containsNoMoreFiles();
  }

  @Test
  public void testCreateWithMissingCommand() throws Exception {
    assertThrows(ValidationException.class,
        () -> skylark.eval("c", "c = "
          + "buildozer.create("
          + "    target = 'foo/bar:baz',"
          + "    rule_type = 'proto_library',"
          + "    commands = ['bar/foo:foobar'],"
          + ")"));
    console.assertThat()
        .onceInLog(MessageType.ERROR, ".*Expected an operation, but got 'bar/foo:foobar'..*");
  }

  @Test
  public void testPackageCanBeEmpty() throws Exception {
    BuildozerCreate create = skylark.eval("c", "c = "
        + "buildozer.create("
        + "    target = ':see_no_pkg',"
        + "    rule_type = 'java_library',"
        + "    commands = ['set config \"foo\"'],"
        + ")");
    transform(create);
    assertThatPath(checkoutDir)
        .containsFile("BUILD", ""
            + "java_library(\n"
            + "    name = \"see_no_pkg\",\n"
            + "    config = \"foo\",\n"
            + ")\n")
        .containsNoMoreFiles();
    transform(create.reverse());
    assertThatPath(checkoutDir).containsFile("BUILD", "");
  }

  @Test
  public void emptyCmdReversalIsError() throws Exception {
    skylark.evalFails("buildozer.cmd(forward='set config \"foo\"', reverse='')",
        ".*Found empty reversal command.*");
  }

  @Test
  public void whitespaceCmdReversalIsError() throws Exception {
    skylark.evalFails("buildozer.cmd(forward='set config \"foo\"', reverse=' ')",
        ".*Found empty reversal command.*");
  }

  @Test
  public void nullCmdReversalIsFine() throws Exception {
    skylark.eval("c", "c=buildozer.cmd(forward='set config \"foo\"')");
    skylark.eval("c", "c=buildozer.cmd(forward='set config \"foo\"', reverse=None)");
  }

  @Test
  public void explicitCmdReversalIsFine() throws Exception {
    skylark.eval("c", "c=buildozer.cmd(forward='set config \"foo\"')");
    skylark.eval("c",
        "c=buildozer.cmd(forward='set config \"foo\"', reverse='set config \"bar\"')");
  }
}
