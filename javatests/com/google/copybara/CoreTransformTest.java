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

package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.jimfs.Jimfs;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.exception.VoidOperationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.transform.ExplicitReversal;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CoreTransformTest {

  private SkylarkTestExecutor skylark;
  private TestingConsole console;
  private Path checkoutDir;
  private OptionsBuilder options;

  @Before
  public void setup() throws IOException {
    FileSystem fs = Jimfs.newFileSystem();
    checkoutDir = fs.getPath("/test-checkoutDir");
    Files.createDirectories(checkoutDir);
    options = new OptionsBuilder();
    skylark = new SkylarkTestExecutor(options);
    console = new TestingConsole();
    options.setConsole(console);
  }

  private void transform(Transformation transform) throws Exception {
    transform.transform(TransformWorks.of(checkoutDir, "testmsg", console));
  }

  @Test
  public void errorForMissingReversalNonReversibleArgument() {
    skylark.evalFails(""
            + "core.transform([\n"
            + "    core.replace(\n"
            + "        before = 'foo${x}',\n"
            + "        after = 'bar',\n"
            + "        regex_groups = {\n"
            + "            'x' : 'x+',\n"
            + "        },\n"
            + "  )\n"
            + "])",
        "transformations are not automatically reversible");
  }

  @Test
  public void autoReversibleCheck() throws Exception {
    ExplicitReversal t = skylark.eval("x", "x="
        + "core.transform([\n"
        + "    core.replace(\n"
        + "        before = 'foo',\n"
        + "        after = 'bar',\n"
        + "    ),\n"
        + "    core.replace(\n"
        + "        before = 'bar',\n"
        + "        after = 'baz',\n"
        + "    ),\n"
        + "])");

    Files.write(checkoutDir.resolve("file.txt"), "foo".getBytes(UTF_8));

    t.transform(TransformWorks.of(checkoutDir, "msg", console));

    assertThatPath(checkoutDir)
        .containsFile("file.txt", "baz")
        .containsNoMoreFiles();

    t.reverse().transform(TransformWorks.of(checkoutDir, "msg", console));

    assertThatPath(checkoutDir)
        .containsFile("file.txt", "foo")
        .containsNoMoreFiles();
  }

  @Test
  public void testOneLayerTransformWithNoop() throws Exception {
    ExplicitReversal t = skylark.eval("x", "x="
                + "core.transform([\n"
                + "    core.replace(\n"
                + "        before = 'not found',\n"
                + "        after = 'not important',\n"
                + "    ),\n"
                + "    core.replace(\n"
                + "        before = 'foo',\n"
                + "        after = 'bar',\n"
                + "    ),\n"
                + "], ignore_noop = True)");

    Files.write(checkoutDir.resolve("file.txt"), "foo".getBytes(UTF_8));
    t.transform(TransformWorks.of(checkoutDir, "msg", console));
    console.assertThat().onceInLog(MessageType.WARNING, ".*NOOP.*");
    assertThatPath(checkoutDir)
        .containsFile("file.txt", "bar")
        .containsNoMoreFiles();
  }

  @Test
  public void testOneLayerTransformWithNoNoop() throws ValidationException, IOException {
    ExplicitReversal t = skylark.eval("x", "x="
                + "core.transform([\n"
                + "    core.replace(\n"
                + "        before = 'not found',\n"
                + "        after = 'not important',\n"
                + "    ),\n"
                + "    core.replace(\n"
                + "        before = 'foo',\n"
                + "        after = 'bar',\n"
                + "    ),\n"
                + "], ignore_noop = False)");

    Files.write(checkoutDir.resolve("file.txt"), "foo".getBytes(UTF_8));

    VoidOperationException e =
        assertThrows(
            VoidOperationException.class,
            () -> t.transform(TransformWorks.of(checkoutDir, "msg", console)));
    assertThat(e)
        .hasMessageThat()
        .containsMatch(".*was a no-op because it didn't " + "change any of the matching files.*");
    assertThatPath(checkoutDir).containsFile("file.txt", "foo").containsNoMoreFiles();
  }

  @Test
  public void testSecondLayerWithInnerNoop() throws Exception {
    String secondLayerTransform =
                "core.transform([\n"
                + "    core.replace(\n"
                + "        before = 'not found',\n"
                + "        after = 'not important',\n"
                + "    ),\n"
                + "],"
                + "ignore_noop=True),";

    ExplicitReversal t = skylark.eval("x", "x="
                + "core.transform([\n"
                + "    core.replace(\n"
                + "        before = 'foo',\n"
                + "        after = 'bar',\n"
                + "    ),\n"
                + secondLayerTransform
                + "    core.replace(\n"
                + "        before = 'bar',\n"
                + "        after = 'baz',\n"
                + "    )\n"
                + "], ignore_noop=False)");

    Files.write(checkoutDir.resolve("file.txt"), "foo".getBytes(UTF_8));
    t.transform(TransformWorks.of(checkoutDir, "msg", console));
    assertThatPath(checkoutDir)
        .containsFile("file.txt", "baz")
        .containsNoMoreFiles();
    console.assertThat().onceInLog(MessageType.WARNING, ".*NOOP.*");
  }

  @Test
  public void testIgnoreNoopWithVerboseFalse() throws Exception {
    ExplicitReversal t = skylark.eval("x", "x="
        + "core.transform([\n"
            + "    core.replace(\n"
            + "        before = 'not found',\n"
            + "        after = 'not important',\n"
            + "    ),\n"
            + "],"
            + "ignore_noop=True)");
    console = new TestingConsole(false);
    options.setConsole(console);
    Files.write(checkoutDir.resolve("file.txt"), "foo".getBytes(UTF_8));
    t.transform(TransformWorks.of(checkoutDir, "msg", console));
    console.assertThat().matchesNext(MessageType.PROGRESS, ".*Replace not found.*")
        .containsNoMoreMessages();
  }

  @Test
  public void testSecondLayerTransformWithOuterNoop() throws Exception {
    String secondLayerTransform =
        "core.transform([\n"
          + "    core.replace(\n"
          + "        before = 'not found',\n"
          + "        after = 'not important',\n"
          + "    ),\n"
          + "]),\n";

    ExplicitReversal t =
        skylark.eval("x", "x="
                + "core.transform([\n"
                + "    core.replace(\n"
                + "        before = 'foo',\n"
                + "        after = 'bar',\n"
                + "     ),"
                + secondLayerTransform
                + "    core.replace(\n"
                + "        before = 'bar',\n"
                + "        after = 'baz',\n"
                + "    )\n"
                + "], ignore_noop=True)");

    Files.write(checkoutDir.resolve("file.txt"), "foo".getBytes(UTF_8));
    t.transform(TransformWorks.of(checkoutDir, "msg", console));
    assertThatPath(checkoutDir)
        .containsFile("file.txt", "baz")
        .containsNoMoreFiles();
    console.assertThat().onceInLog(MessageType.WARNING, ".*NOOP.*");
  }

  @Test
  public void testSecondLayerTransformWithInnerAndOuterNoop()
      throws ValidationException, IOException {
    String secondLayerTransform =
        "core.transform([\n"
            + "    core.replace(\n"
            + "        before = 'not found',\n"
            + "        after = 'not important',\n"
            + "    ),\n"
            + "], ignore_noop=False),\n";

    ExplicitReversal t =
        skylark.eval("x", "x="
            + "core.transform([\n"
            + "    core.replace(\n"
            + "        before = 'foo',\n"
            + "        after = 'bar',\n"
            + "     ),"
            + secondLayerTransform
            + "    core.replace(\n"
            + "        before = 'bar',\n"
            + "        after = 'baz',\n"
            + "    )\n"
            + "], ignore_noop=True)");

    Files.write(checkoutDir.resolve("file.txt"), "foo".getBytes(UTF_8));

    VoidOperationException e =
        assertThrows(
            VoidOperationException.class,
            () -> t.transform(TransformWorks.of(checkoutDir, "msg", console)));
    assertThat(e)
        .hasMessageThat()
        .containsMatch(".*was a no-op because it didn't " + "change any of the matching files.*");
  }

  @Test
  public void testOneLayerTransformWithNoop_usingNoopBehavior_ignoreNoop() throws Exception {
    ExplicitReversal t =
        skylark.eval(
            "x",
            "x="
                + "core.transform([\n"
                + "    core.replace(\n"
                + "        before = 'not found',\n"
                + "        after = 'not important',\n"
                + "    ),\n"
                + "    core.replace(\n"
                + "        before = 'foo',\n"
                + "        after = 'bar',\n"
                + "    ),\n"
                + "], noop_behavior = 'IGNORE_NOOP')");
    Files.write(checkoutDir.resolve("file.txt"), "foo".getBytes(UTF_8));

    t.transform(TransformWorks.of(checkoutDir, "msg", console));

    console.assertThat().onceInLog(MessageType.WARNING, ".*NOOP.*");
    assertThatPath(checkoutDir).containsFile("file.txt", "bar").containsNoMoreFiles();
  }

  @Test
  public void testOneLayerTransformWithNoop_usingNoopBehavior_noopIfAllNoop() throws Exception {
    ExplicitReversal t =
        skylark.eval(
            "x",
            "x="
                + "core.transform([\n"
                + "    core.replace(\n"
                + "        before = 'not found',\n"
                + "        after = 'not important',\n"
                + "    ),\n"
                + "    core.replace(\n"
                + "        before = 'foo',\n"
                + "        after = 'bar',\n"
                + "    ),\n"
                + "], noop_behavior = 'NOOP_IF_ALL_NOOP')");
    Files.write(checkoutDir.resolve("file.txt"), "foo".getBytes(UTF_8));

    t.transform(TransformWorks.of(checkoutDir, "msg", console));

    console.assertThat().onceInLog(MessageType.WARNING, ".*NOOP.*");
    assertThatPath(checkoutDir).containsFile("file.txt", "bar").containsNoMoreFiles();
  }

  @Test
  public void testOneLayerTransformWithAllNoops_usingNoopBehavior_noopIfAllNoop() throws Exception {
    ExplicitReversal t =
        skylark.eval(
            "x",
            "x="
                + "core.transform([\n"
                + "    core.replace(\n"
                + "        before = 'not found',\n"
                + "        after = 'not important',\n"
                + "    ),\n"
                + "    core.replace(\n"
                + "        before = 'also not found',\n"
                + "        after = 'also not important',\n"
                + "    ),\n"
                + "], noop_behavior = 'NOOP_IF_ALL_NOOP')");

    Files.write(checkoutDir.resolve("file.txt"), "foo".getBytes(UTF_8));

    TransformationStatus status = t.transform(TransformWorks.of(checkoutDir, "msg", console));

    assertThat(status.isNoop()).isTrue();
    console.assertThat().timesInLog(2, MessageType.WARNING, ".*NOOP.*");
    assertThatPath(checkoutDir).containsFile("file.txt", "foo").containsNoMoreFiles();
  }

  @Test
  public void errorForMissingForwardArgument() {
    skylark.evalFails(
        "core.transform(reversal = [core.move('foo', 'bar')])",
        "missing 1 required positional argument: transformations");
  }

  @Test
  public void runForward() throws Exception {
    Files.write(checkoutDir.resolve("file1"), new byte[0]);
    Files.write(checkoutDir.resolve("file2"), new byte[0]);
    Transformation transform = skylark.eval("t", "t = "
        + "core.transform("
        + "    [core.move('file1', 'file1.a'), core.move('file2', 'file2.a')],"
        + "    reversal = [core.move('foo', 'bar')],"
        + ")");

    transform(transform);

    assertThatPath(checkoutDir)
        .containsFiles("file1.a", "file2.a");
  }

  @Test
  public void progressMessages() throws Exception {
    Files.write(checkoutDir.resolve("file1"), new byte[0]);
    Files.write(checkoutDir.resolve("file2"), new byte[0]);
    Transformation transform = skylark.eval("t", "t = "
        + "core.transform("
        + "    [core.move('file1', 'file1.a'), core.move('file2', 'file2.a')],"
        + "    reversal = [core.move('foo', 'bar')],"
        + ")");

    transform(transform);

    console.assertThat()
        .onceInLog(MessageType.PROGRESS, "\\[ *1/ *2\\] Transform Moving file1")
        .onceInLog(MessageType.PROGRESS, "\\[ *2/ *2\\] Transform Moving file2");
  }

  @Test
  public void runReversal() throws Exception {
    Files.write(checkoutDir.resolve("file1"), new byte[0]);
    Files.write(checkoutDir.resolve("file2"), new byte[0]);
    Transformation transform = skylark.eval("t", "t = "
        + "core.transform("
        + "    [core.move('foo', 'bar')],"
        + "    reversal = [core.move('file1', 'file1.a'), core.move('file2', 'file2.a')],"
        + ")");

    transform(transform.reverse());

    assertThatPath(checkoutDir)
        .containsFiles("file1.a", "file2.a");
  }

  @Test
  public void errorForNonTransformationElementInList() {
    skylark.evalFails(
        "core.transform([42], reversal = [core.move('foo', 'bar')])",
        "for 'transformations' element, got int, want function or transformation");
    skylark.evalFails(
        "core.transform([core.move('foo', 'bar')], reversal = [42])",
        "for 'reversal' element, got int, want function or transformation");
  }

}
