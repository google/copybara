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
import static org.junit.Assert.fail;

import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
import com.google.copybara.buildozer.testing.BuildozerTesting;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.transform.ExplicitReversal;
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
public final class BuildozerModifyTest {

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
    checkoutDir = Files.createTempDirectory("BuildozerModifyTest");
    skylark = new SkylarkTestExecutor(options);
  }

  private TransformationStatus transform(Transformation modify) throws Exception {
    return modify.transform(TransformWorks.of(checkoutDir, "test msg", console));
  }

  @Test
  public void errorForMissingTarget() {
    try {
      skylark.eval(
          "m",
          """
          m = buildozer.modify(commands = ['set config :foo'])
          """);
      fail();
    } catch (ValidationException expected) {}
    console
        .assertThat()
        .onceInLog(MessageType.ERROR, ".*missing 1 required positional argument: target.*");
  }

  @Test
  public void errorMissingCommands() {
    try {
      skylark.eval("m", "m = buildozer.modify(target = 'foo:bar')\n");
      fail();
    } catch (ValidationException expected) {}
    console
        .assertThat()
        .onceInLog(MessageType.ERROR, ".*missing 1 required positional argument: commands.*");
  }

  @Test
  public void errorForEmptyCommands() {
    try {
      skylark.eval("m",
          "m = buildozer.modify(target = 'foo:bar', commands = [])\n");
      fail();
    } catch (ValidationException expected) {}
    console.assertThat()
        .onceInLog(MessageType.ERROR,
            ".*at least one element required in 'commands' argument*");
  }

  @Test
  public void nonReversibleError() {
    try {
      skylark.eval(
          "m",
          """
          m = core.reverse([
              buildozer.modify(
                  target = 'describe/IncludesTarget:Name',
                  commands = [
                      buildozer.cmd('set foo bar', reverse = 'set foo baz'),
                      buildozer.cmd('set tags ["foo"]'),
                  ],
              ),
          ])
          """);
      fail();
    } catch (ValidationException expected) {}
    console.assertThat()
        .onceInLog(MessageType.ERROR,
            ".*a reverse was not provided: set tags \\[\"foo\"\\].*");
  }

  @Test
  public void replaceWithMoreThan3Args_isError() {
    try {
      skylark.eval(
          "m",
          """
          m = buildozer.modify(
              target = 'describe/IncludesTarget:Name',
              commands = [buildozer.cmd('replace x y z a b c')],
          )
          """);
      fail();
    } catch (ValidationException expected) {}
    console.assertThat()
        .onceInLog(MessageType.ERROR,
            "(.|\n)*Cannot reverse 'replace x y z a b c', expected three arguments, but found 6.*");
  }

  @Test
  public void replaceForwardWith2Args_isError() {
    try {
      skylark.eval(
          "m",
          """
          m = buildozer.modify(
              target = 'describe/IncludesTarget:Name',
              commands = [buildozer.cmd(
                forward = 'replace library :go_default_library')],
          )
          """);
      fail();
    } catch (ValidationException expected) {}
    console.assertThat()
        .onceInLog(MessageType.ERROR,
            "(.|\n)*replace library :go_default_library', expected three arguments, but found 2.*");
  }

  @Test
  public void runTwoCommands() throws Exception {
    BuildozerModify modify =
        skylark.eval(
            "m",
            """
            m = buildozer.modify(
                target = 'foo/bar:baz',
                commands = [
                    buildozer.cmd('set config ":foo"'),
                    buildozer.cmd('replace deps old_dep new_dep'),
                ],
            )\
            """);

    Files.createDirectories(checkoutDir.resolve("foo/bar"));
    String original =
        """
        # initial comment

        proto_library(name = 'baz', deps = ['old_dep'])
        """;
    Files.write(checkoutDir.resolve("foo/bar/BUILD"), original.getBytes(UTF_8));
    transform(modify);
    assertThatPath(checkoutDir)
        .containsFile(
            "foo/bar/BUILD",
            """
            # initial comment

            proto_library(
                name = "baz",
                config = ":foo",
                deps = ["new_dep"],
            )
            """);
  }

  @Test
  public void runTwoCommandsOnTwoTargets() throws Exception {
    BuildozerModify modify =
        skylark.eval(
            "m",
            """
            m = buildozer.modify(
                target = ['foo/bar:baz', 'foo/bar:foo'],
                commands = [
                    buildozer.cmd('set config ":foo"'),
                    buildozer.cmd('replace deps old_dep new_dep'),
                ],
            )\
            """);

    Files.createDirectories(checkoutDir.resolve("foo/bar"));
    String original =
        """
        # initial comment

        proto_library(name = 'foo', deps = ['old_dep'])
        proto_library(name = 'baz', deps = ['old_dep'])
        """;
    Files.write(checkoutDir.resolve("foo/bar/BUILD"), original.getBytes(UTF_8));
    transform(modify);
    assertThatPath(checkoutDir)
        .containsFile(
            "foo/bar/BUILD",
            """
            # initial comment

            proto_library(
                name = "foo",
                config = ":foo",
                deps = ["new_dep"],
            )

            proto_library(
                name = "baz",
                config = ":foo",
                deps = ["new_dep"],
            )
            """);
  }

  /**
   * Here we test that if we join buildozer transformations, we use buildozer -k, so that it
   * keeps going if one of them fails to find a target. Otherwise the effect of bundling many
   * buildozer transformations will be visible to our users when using --ignore-noop
   */
  @Test
  public void testKeepGoing() throws Exception {
    // Explicit just in case default changes
    options.workflowOptions.noTransformationJoin = false;
    options.workflowOptions.ignoreNoop = true;
    ExplicitReversal modify =
        skylark.eval(
            "m",
            """
            m = core.transform([
                 buildozer.modify(
                   target = ['foo/bar:idontexist'],
                   commands = [ buildozer.cmd('set config "test"')],
                 ),
                 buildozer.modify(
                   target = ['foo/bar:*'],
                   commands = [ buildozer.cmd('set other "test"')],
                 ),
              ], reversal = [])\
            """);

    Files.createDirectories(checkoutDir.resolve("foo/bar"));
    String original = "proto_library(name = 'baz')\n";
    Files.write(checkoutDir.resolve("foo/bar/BUILD"), original.getBytes(UTF_8));
    transform(modify);
    assertThatPath(checkoutDir)
        .containsFile(
            "foo/bar/BUILD",
            """
            proto_library(
                name = "baz",
                other = "test",
            )
            """);
  }

  @Test
  public void reverseWithMultipleCommands() throws Exception {
    BuildozerModify modify =
        skylark.eval(
            "m",
            """
            m = buildozer.modify(
                target = 'foo/bar:baz',
                commands = [
                    buildozer.cmd('replace deps old_old_dep old_dep'),
                    buildozer.cmd('replace deps old_dep new_dep'),
                ],
            )\
            """);

    Files.createDirectories(checkoutDir.resolve("foo/bar"));
    String original =
        """
        # initial comment

        proto_library(name = 'baz', deps = ['new_dep'])
        """;
    Files.write(checkoutDir.resolve("foo/bar/BUILD"), original.getBytes(UTF_8));
    transform(modify.reverse());

    assertThatPath(checkoutDir)
        .containsFile(
            "foo/bar/BUILD",
            """
            # initial comment

            proto_library(
                name = "baz",
                deps = ["old_old_dep"],
            )
            """);
  }

  @Test
  public void reverseWithMultipleCommandsAndMultipleTargets() throws Exception {
    BuildozerModify modify =
        skylark.eval(
            "m",
            """
            m = buildozer.modify(
                target = ['foo/bar:baz', 'foo/bar:fooz'],
                commands = [
                    buildozer.cmd('replace deps old_old_dep old_dep'),
                    buildozer.cmd('replace deps old_dep new_dep'),
                ],
            )\
            """);

    Files.createDirectories(checkoutDir.resolve("foo/bar"));
    String original =
        """
        # initial comment

        proto_library(name = 'baz', deps = ['new_dep'])
        proto_library(name = 'fooz', deps = ['new_dep'])
        """;
    Files.write(checkoutDir.resolve("foo/bar/BUILD"), original.getBytes(UTF_8));
    transform(modify.reverse());

    assertThatPath(checkoutDir)
        .containsFile(
            "foo/bar/BUILD",
            """
            # initial comment

            proto_library(
                name = "baz",
                deps = ["old_old_dep"],
            )

            proto_library(
                name = "fooz",
                deps = ["old_old_dep"],
            )
            """);
  }

  @Test
  public void testAddValue() throws Exception {
    BuildozerModify modify =
        skylark.eval(
            "m",
            """
            m = buildozer.modify(
                target = 'foo:%proto_library',
                commands = [buildozer.cmd('add deps new_one')],
            )\
            """);

    Files.createDirectories(checkoutDir.resolve("foo"));
    String original =
        """
        proto_library(name = 'bar', deps = ['old_one'])
        proto_library(name = 'baz')
        """;
    Files.write(checkoutDir.resolve("foo/BUILD"), original.getBytes(UTF_8));

    transform(modify);

    assertThatPath(checkoutDir)
        .containsFile(
            "foo/BUILD",
            """
            proto_library(
                name = "bar",
                deps = [
                    "new_one",
                    "old_one",
                ],
            )

            proto_library(
                name = "baz",
                deps = ["new_one"],
            )
            """);

    transform(modify.reverse());

    assertThatPath(checkoutDir)
        .containsFile(
            "foo/BUILD",
            """
            proto_library(
                name = "bar",
                deps = ["old_one"],
            )

            proto_library(name = "baz")
            """);
  }

  @Test
  public void testRemoveValue() throws Exception {
    BuildozerModify modify =
        skylark.eval(
            "m",
            """
            m = buildozer.modify(
                target = 'foo:%proto_library',
                commands = [buildozer.cmd('remove deps new_one')],
            )\
            """);

    Files.createDirectories(checkoutDir.resolve("foo"));
    String original =
        """
        proto_library(name = 'bar', deps = ['new_one','old_one'])
        proto_library(name = 'baz', deps = ['new_one'])
        """;
    Files.write(checkoutDir.resolve("foo/BUILD"), original.getBytes(UTF_8));

    transform(modify);

    assertThatPath(checkoutDir)
        .containsFile(
            "foo/BUILD",
            """
            proto_library(
                name = "bar",
                deps = ["old_one"],
            )

            proto_library(name = "baz")
            """);

    transform(modify.reverse());

    assertThatPath(checkoutDir)
        .containsFile(
            "foo/BUILD",
            """
            proto_library(
                name = "bar",
                deps = [
                    "new_one",
                    "old_one",
                ],
            )

            proto_library(
                name = "baz",
                deps = ["new_one"],
            )
            """);
  }

  @Test
  public void testRemoveAll() throws Exception {
    BuildozerModify modify =
        skylark.eval(
            "m",
            """
            m = buildozer.modify(
                target = 'foo:%proto_library',
                commands = [buildozer.cmd('remove deps')],
            )\
            """);

    Files.createDirectories(checkoutDir.resolve("foo"));
    String original = "proto_library(name = 'bar', deps = ['new_one','old_one'])\n";
    Files.write(checkoutDir.resolve("foo/BUILD"), original.getBytes(UTF_8));

    transform(modify);

    assertThatPath(checkoutDir)
        .containsFile(
            "foo/BUILD",
            """
            proto_library(name = "bar")
            """);
  }

  @Test
  public void testRemoveAllNotReversible() {
    try {
      skylark.eval(
          "m",
          """
          m = core.reverse([
              buildozer.modify(
                  target = 'foo:%proto_library',
                  commands = [buildozer.cmd('remove deps')],
              ),
          ])\
          """);
      fail();
    } catch (ValidationException expected) {}
    console.assertThat()
        .onceInLog(MessageType.ERROR,
            ".*a reverse was not provided: remove deps");
  }

  @Test
  public void testEmptyCMD() {
    try {
      skylark.eval(
          "m",
          """
          buildozer.modify(
              target = 'foo:%proto_library',
              commands = [buildozer.cmd(' ')],
          )\
          """);
      fail();
    } catch (ValidationException e) {
      assertThat(e).hasMessageThat().contains("Found empty command");
    }
  }

  @Test
  public void testNoopIsWarningNonExistentBuild() throws Exception {
    BuildozerModify modify =
        skylark.eval(
            "m",
            """
            m = buildozer.modify(
                target = 'doesnt_exist:%proto_library',
                commands = [buildozer.cmd('remove deps')],
            )\
            """);
    options.workflowOptions.ignoreNoop = true;
    try {
      transform(modify);
      fail();
    } catch (ValidationException e) {
      assertThat(e.getMessage())
          .contains(
              """
              Failed to execute buildozer with args:
                remove deps|doesnt_exist:%proto_library\
              """);
      assertThat(e.getMessage())
          .contains("doesnt_exist/BUILD: file not found or not readable");
    }
  }

  @Test
  public void testNoopIsWarningTarget() throws Exception {
    BuildozerModify modify =
        skylark.eval(
            "m",
            """
            m = buildozer.modify(
                target = 'foo:doesnt_exist',
                commands = [buildozer.cmd('remove deps')],
            )\
            """);
    options.workflowOptions.ignoreNoop = true;
    Files.createDirectories(checkoutDir.resolve("foo"));
    Files.write(checkoutDir.resolve("foo/BUILD"), "".getBytes(UTF_8));
    TransformationStatus status = transform(modify);
    assertThat(status.isNoop()).isTrue();
    assertThat(status.getMessage())
        .matches(
            ".*Buildozer could not find a target for foo:doesnt_exist:"
                + " rule 'doesnt_exist' not found.*");
  }

  @Test
  public void testNoopIsWarningTargetWildcard() throws Exception {
    BuildozerModify modify =
        skylark.eval(
            "m",
            """
            m = buildozer.modify(
                target = 'foo:%proto_library',
                commands = [buildozer.cmd('remove deps')],
            )\
            """);
    options.workflowOptions.ignoreNoop = true;
    Files.createDirectories(checkoutDir.resolve("foo"));
    Files.write(checkoutDir.resolve("foo/BUILD"), "".getBytes(UTF_8));
    TransformationStatus status = transform(modify);
    assertThat(status.isNoop()).isTrue();
    assertThat(status.getMessage())
        .matches(".*Buildozer could not find a target for:\n  remove deps\\|foo:%proto_library.*");
  }

  @Test
  public void testReplaceIsAutomaticallyReversible() throws Exception {
    BuildozerModify modify =
        skylark.eval(
            "m",
            """
            m = buildozer.modify(
                target = 'foo:%proto_library',
                commands = [buildozer.cmd('replace deps ekusu x')],
            )\
            """);
    Files.createDirectories(checkoutDir.resolve("foo"));
    String original = "proto_library(name = 'bar', deps = ['x', 'y'])";
    Files.write(checkoutDir.resolve("foo/BUILD"), original.getBytes(UTF_8));

    transform(modify.reverse());

    assertThatPath(checkoutDir)
        .containsFile(
            "foo/BUILD",
            """
            proto_library(
                name = "bar",
                deps = [
                    "ekusu",
                    "y",
                ],
            )
            """);
  }

  @Test
  public void reversalOfManualReversalDoesOriginalReversal() throws Exception {
    BuildozerModify modify =
        skylark.eval(
            "m",
            """
            m = buildozer.modify(
                target = 'foo/bar:baz',
                commands = [
                    buildozer.cmd('replace deps before after',
                                  reverse = 'replace deps wrong1 wrong2'),
                ],
            )\
            """);

    Files.createDirectories(checkoutDir.resolve("foo/bar"));
    String original = "proto_library(name = 'baz', deps = ['before'])\n";
    Files.write(checkoutDir.resolve("foo/bar/BUILD"), original.getBytes(UTF_8));
    transform(modify.reverse().reverse());

    assertThatPath(checkoutDir)
        .containsFile(
            "foo/bar/BUILD",
            """
            proto_library(
                name = "baz",
                deps = ["after"],
            )
            """);
  }

  @Test
  public void recursivePackageExpression() throws Exception {
    BuildozerModify modify =
        skylark.eval(
            "m",
            """
            m = buildozer.modify(
                target = 'foo/...:%java_library',
                commands = [buildozer.cmd('replace deps before after')],
            )\
            """);

    Files.createDirectories(checkoutDir.resolve("foo/bar/baz"));
    Files.createDirectories(checkoutDir.resolve("foo/abc"));

    String original =
        """
        java_library(name = 'foo', deps = ['before'])
        java_binary(name = 'unmatched', deps = ['before'])
        """;
    Files.write(checkoutDir.resolve("foo/bar/baz/BUILD"), original.getBytes(UTF_8));
    Files.write(checkoutDir.resolve("foo/abc/BUILD"), original.getBytes(UTF_8));

    transform(modify);

    String buildozed =
        """
        java_library(
            name = "foo",
            deps = ["after"],
        )

        java_binary(
            name = "unmatched",
            deps = ["before"],
        )
        """;
    assertThatPath(checkoutDir)
        .containsFile("foo/bar/baz/BUILD", buildozed)
        .containsFile("foo/abc/BUILD", buildozed);
  }

  @Test
  public void positionalArgumentsAndAutowrapCommandStrings() throws Exception {
    BuildozerModify modify = skylark.eval("m",
        "m = buildozer.modify('foo/bar:baz', ['replace deps old_dep new_dep'])");

    Files.createDirectories(checkoutDir.resolve("foo/bar"));
    String original =
        """
        # initial comment

        proto_library(name = 'baz', deps = ['old_dep'])
        """;
    Files.write(checkoutDir.resolve("foo/bar/BUILD"), original.getBytes(UTF_8));
    transform(modify);

    assertThatPath(checkoutDir)
        .containsFile(
            "foo/bar/BUILD",
            """
            # initial comment

            proto_library(
                name = "baz",
                deps = ["new_dep"],
            )
            """);
  }

  @Test
  public void autoreverseBareCommandString() throws Exception {
    BuildozerModify modify = skylark.eval("m",
        "m = buildozer.modify('foo/bar:baz', ['replace deps new_dep old_dep'])");

    Files.createDirectories(checkoutDir.resolve("foo/bar"));
    String original =
        """
        # initial comment

        proto_library(name = 'baz', deps = ['old_dep'])
        """;
    Files.write(checkoutDir.resolve("foo/bar/BUILD"), original.getBytes(UTF_8));
    transform(modify.reverse());

    assertThatPath(checkoutDir)
        .containsFile(
            "foo/bar/BUILD",
            """
            # initial comment

            proto_library(
                name = "baz",
                deps = ["new_dep"],
            )
            """);
  }

  @Test
  public void errorForInvalidCommandType() {
    try {
      skylark.eval(
          "m",
          """
          m = buildozer.modify(
              target = 'foo/bar:baz',
              commands = [42],
          )\
          """);
      fail();
    } catch (ValidationException expected) {}
    console.assertThat()
        .onceInLog(MessageType.ERROR,
            ".*Expected a string or buildozer[.]cmd, but got: 42.*");
  }

  @Test
  public void setMultipleValues() throws Exception {
    BuildozerModify modify =
        skylark.eval(
            "m",
            """
            m = buildozer.modify(
                target = 'foo/bar:baz',
                commands = ['set deps first second'],
            )\
            """);

    Files.createDirectories(checkoutDir.resolve("foo/bar"));
    String original = ""
        + "# initial comment\n\n"
        + "proto_library(name = 'baz', srcs = ['x'])\n";
    Files.write(checkoutDir.resolve("foo/bar/BUILD"), original.getBytes(UTF_8));
    transform(modify);

    assertThatPath(checkoutDir)
        .containsFile(
            "foo/bar/BUILD",
            """
            # initial comment

            proto_library(
                name = "baz",
                srcs = ["x"],
                deps = [
                    "first",
                    "second",
                ],
            )
            """);
  }

  @Test
  public void setEmptyList() throws Exception {
    BuildozerModify modify =
        skylark.eval(
            "m",
            """
            m = buildozer.modify(
                target = 'foo/bar:baz',
                commands = ['set deps'],
            )\
            """);

    Files.createDirectories(checkoutDir.resolve("foo/bar"));
    String original = ""
        + "# initial comment\n\n"
        + "proto_library(name = 'baz', srcs = ['x'])\n";
    Files.write(checkoutDir.resolve("foo/bar/BUILD"), original.getBytes(UTF_8));
    transform(modify);

    assertThatPath(checkoutDir)
        .containsFile(
            "foo/bar/BUILD",
            """
            # initial comment

            proto_library(
                name = "baz",
                srcs = ["x"],
                deps = [],
            )
            """);
  }

  /**
   * A specific test for the target '...:*'.
   */
  @Test
  public void modifyMultipleFiles() throws Exception {
    BuildozerModify modify =
        skylark.eval(
            "m",
            """
            m = buildozer.modify(
                target = '...:*',
                commands = ['set deps first'],
            )\
            """);

    String originalContents =
        """
        # initial comment

        proto_library(name = 'baz', srcs = ['x'])
        """;
    createBuildFile("", originalContents);
    createBuildFile("foo", originalContents);
    createBuildFile("foo/bar", originalContents);

    transform(modify);

    String expectedContents =
        """
        # initial comment

        proto_library(
            name = "baz",
            srcs = ["x"],
            deps = ["first"],
        )
        """;
    assertThatPath(checkoutDir)
        .containsFile("BUILD", expectedContents)
        .containsFile("foo/BUILD", expectedContents)
        .containsFile("foo/bar/BUILD", expectedContents);
  }

  /**
   * A specific test for the target '//foo/...:*'.
   */
  @Test
  public void modifyMultipleFilesPrefix() throws Exception {
    BuildozerModify modify =
        skylark.eval(
            "m",
            """
            m = buildozer.modify(
                target = 'foo/...:*',
                commands = ['set deps first'],
            )\
            """);

    String originalContents =
        """
        # initial comment

        proto_library(name = 'baz', srcs = ['x'])
        """;
    createBuildFile("", originalContents);
    createBuildFile("foo", originalContents);
    createBuildFile("foo/bar", originalContents);

    transform(modify);

    String expectedContents =
        """
        # initial comment

        proto_library(
            name = "baz",
            srcs = ["x"],
            deps = ["first"],
        )
        """;
    assertThatPath(checkoutDir)
        .containsFile("BUILD", originalContents)
        .containsFile("foo/BUILD", expectedContents)
        .containsFile("foo/bar/BUILD", expectedContents);
  }

  /**
   * A specific test for the target '...:__pkg__'.
   */
  @Test
  public void modifyMultipleFilesPackage() throws Exception {
    BuildozerModify modify =
        skylark.eval(
            "m",
            """
            m = buildozer.modify(
                target = '...:__pkg__',
                commands = ['set visibility //visibility:public'],
            )\
            """);

    String originalContents =
        """
        # initial comment

        proto_library(name = 'baz', srcs = ['x'])
        """;
    createBuildFile("", originalContents);
    createBuildFile("foo", originalContents);
    createBuildFile("foo/bar", originalContents);

    transform(modify);

    String expectedContents =
        """
        # initial comment

        package(visibility = ["//visibility:public"])

        proto_library(
            name = "baz",
            srcs = ["x"],
        )
        """;
    assertThatPath(checkoutDir)
        .containsFile("BUILD", expectedContents)
        .containsFile("foo/BUILD", expectedContents)
        .containsFile("foo/bar/BUILD", expectedContents);
  }

  /**
   * A specific test for the target '...:__pkg__'.
   */
  @Test
  public void modifyMultipleFilesPackagePrefix() throws Exception {
    BuildozerModify modify =
        skylark.eval(
            "m",
            """
            m = buildozer.modify(
                target = 'foo/...:__pkg__',
                commands = ['set visibility //visibility:public'],
            )\
            """);

    String originalContents =
        """
        # initial comment

        proto_library(name = 'baz', srcs = ['x'])
        """;
    createBuildFile("", originalContents);
    createBuildFile("foo", originalContents);
    createBuildFile("foo/bar", originalContents);

    transform(modify);

    String expectedContents =
        """
        # initial comment

        package(visibility = ["//visibility:public"])

        proto_library(
            name = "baz",
            srcs = ["x"],
        )
        """;
    assertThatPath(checkoutDir)
        .containsFile("BUILD", originalContents)
        .containsFile("foo/BUILD", expectedContents)
        .containsFile("foo/bar/BUILD", expectedContents);
  }

  private void createBuildFile(String path, String contents) throws IOException {
    Files.createDirectories(checkoutDir.resolve(path));
    Files.write(checkoutDir.resolve(path).resolve("BUILD"), contents.getBytes(UTF_8));
  }
}
