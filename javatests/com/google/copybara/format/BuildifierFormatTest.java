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

package com.google.copybara.format;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.copybara.TransformationStatus;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BuildifierFormatTest {

  private static final String NOT_FORMATTED =
      "java_library(" + "name=\"tests\",srcs = [\"Foo.java\", \"Bar.java\"])\n";
  private static final String FORMATTED = ""
      + "java_library(\n"
      + "    name = \"tests\",\n"
      + "    srcs = [\n"
      + "        \"Bar.java\",\n"
      + "        \"Foo.java\",\n"
      + "    ],\n"
      + ")\n";

  private OptionsBuilder options;
  private Path checkoutDir;
  private TestingConsole console;
  private SkylarkTestExecutor skylark;

  @Before
  public void setup() throws IOException {
    console = new TestingConsole();
    options = new OptionsBuilder();
    options.setConsole(console);
    File buildifier = Paths.get(System.getenv("TEST_SRCDIR"))
        .resolve(System.getenv("TEST_WORKSPACE"))
        .resolve("javatests/com/google/copybara/format")
        .resolve("buildifier")
        .toFile();
    options.buildifier.buildifierBin = buildifier.getAbsolutePath();
    checkoutDir = Files.createTempDirectory("BuildifierFormatTest");
    skylark = new SkylarkTestExecutor(options);
  }

  @Test
  public void nonDefaultUsage() throws ValidationException, IOException {
    BuildifierFormat b = skylark.eval("c",
        "c = format.buildifier(glob(['**a_build_file']), type = 'build')\n");

    Files.write(checkoutDir.resolve("a_build_file"), NOT_FORMATTED.getBytes(UTF_8));
    Files.createDirectories(checkoutDir.resolve("foo"));
    Files.write(checkoutDir.resolve("foo/a_build_file"), NOT_FORMATTED.getBytes(UTF_8));

    b.transform(TransformWorks.of(checkoutDir, "foo", console));

    assertThatPath(checkoutDir)
        .containsFile("a_build_file", FORMATTED)
        .containsFile("foo/a_build_file", FORMATTED)
        .containsNoMoreFiles();
  }

  @Test
  public void defaultUsage() throws ValidationException, IOException {
    BuildifierFormat b = skylark.eval("c", "c = format.buildifier()\n");

    Files.write(checkoutDir.resolve("BUILD"), NOT_FORMATTED.getBytes(UTF_8));
    Files.createDirectories(checkoutDir.resolve("foo"));
    Files.write(checkoutDir.resolve("foo/BUILD"), NOT_FORMATTED.getBytes(UTF_8));

    b.transform(TransformWorks.of(checkoutDir, "foo", console));

    assertThatPath(checkoutDir)
        .containsFile("BUILD", FORMATTED)
        .containsFile("foo/BUILD", FORMATTED)
        .containsNoMoreFiles();
  }

  @Test
  public void useLint() throws ValidationException, IOException {
    BuildifierFormat b = skylark.eval("c",
        "c = format.buildifier(lint = 'FIX', lint_warnings = ['all'])\n");

    Files.write(
        checkoutDir.resolve("BUILD"),
        (""
                + "load(\"//something/some:file.bzl\", \"some_func\")\n"
                + "load(\"//third_party/bazel_rules/rules_java/java:defs.bzl\", \"java_library\")\n"
                + "\n"
                + "java_library("
                + "name=\"tests\",srcs = [\"Foo.java\", \"Bar.java\"])\n")
            .getBytes(UTF_8));

    b.transform(TransformWorks.of(checkoutDir, "foo", console));

    // Removes unused load statement.
    assertThatPath(checkoutDir)
        .containsFile(
            "BUILD",
            ""
                + "load(\"//third_party/bazel_rules/rules_java/java:defs.bzl\", \"java_library\")\n"
                + "\n"
                + "java_library(\n"
                + "    name = \"tests\",\n"
                + "    srcs = [\n"
                + "        \"Bar.java\",\n"
                + "        \"Foo.java\",\n"
                + "    ],\n"
                + ")\n")
        .containsNoMoreFiles();
  }

  @Test
  public void withGlob() throws ValidationException, IOException {
    BuildifierFormat b = skylark.eval("c", "c = format.buildifier(glob([\"BUILD\"]))\n");

    Files.write(checkoutDir.resolve("BUILD"), NOT_FORMATTED.getBytes(UTF_8));
    Files.createDirectories(checkoutDir.resolve("foo"));
    Files.write(checkoutDir.resolve("foo/BUILD"), NOT_FORMATTED.getBytes(UTF_8));

    b.transform(TransformWorks.of(checkoutDir, "foo", console));

    assertThatPath(checkoutDir)
        .containsFile("BUILD", FORMATTED)
        .containsFile("foo/BUILD", NOT_FORMATTED)
        .containsNoMoreFiles();
  }

  @Test
  public void humongousNumberOfFiles() throws ValidationException, IOException {
    options.buildifier.batchSize = 7;
    BuildifierFormat b = skylark.eval("c", "c = format.buildifier(glob(['**/BUILD']))\n");

    int count = 30;
    for (int i = 0; i < count; i++) {
      Path base = Files.createDirectories(checkoutDir.resolve("aaaaaaa_" + i));
      Files.write(base.resolve("BUILD"), NOT_FORMATTED.getBytes(UTF_8));
    }

    b.transform(TransformWorks.of(checkoutDir, "foo", console));

    for (int i = 0; i < count; i++) {
      Path base = Files.createDirectories(checkoutDir.resolve("aaaaaaa_" + i));
      assertThatPath(base).containsFile("BUILD", FORMATTED);
    }
  }

  @Test
  public void noop() throws ValidationException, IOException {
    BuildifierFormat b = skylark.eval("c", "c = format.buildifier(glob([\"BUILD\"]))\n");

    Files.createDirectories(checkoutDir.resolve("foo"));
    Files.write(checkoutDir.resolve("foo/BUILD"), NOT_FORMATTED.getBytes(UTF_8));
    TransformationStatus status = b.transform(TransformWorks.of(checkoutDir, "foo", console));
    assertThat(status.isNoop()).isTrue();
  }

  @Test
  public void syntaxError() throws ValidationException, IOException {
    BuildifierFormat b = skylark.eval("c", "c = format.buildifier()\n");

    // Random text in the BUILD file
    Files.write(checkoutDir.resolve("BUILD"), "lalala $ foooo / & * lalala 42".getBytes(UTF_8));

    ValidationException thrown =
        assertThrows(
            ValidationException.class,
            () -> b.transform(TransformWorks.of(checkoutDir, "foo", console)));
    assertThat(thrown)
        .hasMessageThat()
        .contains("Build file(s) couldn't be formatted because there was a syntax error");
  }
}
