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
import static org.junit.Assert.fail;

import com.google.common.jimfs.Jimfs;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.RecordsProcessCallDestination;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TestingModule;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.copybara.util.console.testing.TestingConsole.MessageType;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TransformWorkTest {

  private static final Author ORIGINAL_AUTHOR = new Author("Foo Bar", "foo@bar.com");

  private SkylarkTestExecutor skylark;
  private DummyOrigin origin;
  private RecordsProcessCallDestination destination;
  private Path workdir;
  private TestingConsole console;

  @Before
  public void setup() throws IOException {
    origin = new DummyOrigin().setAuthor(ORIGINAL_AUTHOR);
    destination = new RecordsProcessCallDestination();
    OptionsBuilder options = new OptionsBuilder();
    console = new TestingConsole();
    options.setConsole(console);
    options.testingOptions.origin = origin;
    options.testingOptions.destination = destination;
    skylark = new SkylarkTestExecutor(options, TestingModule.class);
    workdir = Files.createTempDirectory("workdir");
  }

  @Test
  public void testAddLabel() {
    checkAddLabel("foo", "foo\n\nTEST=VALUE\n");
  }

  @Test
  public void testAddLabelToGroup() {
    checkAddLabel("foo\n\nA=B\n\n", "foo\n\nA=B\nTEST=VALUE\n\n");
  }

  @Test
  public void testAddLabelNoEmptyLineBeforeGroup() {
    checkAddLabel("foo\nA=B\n\n", "foo\nA=B\n\nTEST=VALUE\n");
  }

  @Test
  public void testAddLabelNoGroupNoEndLine() {
    checkAddLabel("foo\nA=B", "foo\nA=B\n\nTEST=VALUE\n");
  }

  @Test
  public void testReplaceLabel() {
    TransformWork work = create("Foo\n\nSOME=TEST\n");
    work.replaceLabel("SOME", "REPLACED");
    assertThat(work.getMessage()).isEqualTo("Foo\n\nSOME=REPLACED\n");
  }

  @Test
  public void testReplaceNonExistentLabel() {
    TransformWork work = create("Foo\n\nFOO=TEST\n");
    work.replaceLabel("SOME", "REPLACED");
    assertThat(work.getMessage()).isEqualTo("Foo\n\nFOO=TEST\n");
  }

  @Test
  public void testsDeleteLabel() {
    TransformWork work = create("Foo\n\nSOME=TEST\n");
    work.removeLabel("SOME");
    assertThat(work.getMessage()).isEqualTo("Foo\n\n");
  }

  @Test
  public void testsDeleteNonExistentLabel() {
    TransformWork work = create("Foo\n\nSOME=TEST\n");
    work.removeLabel("FOO");
    assertThat(work.getMessage()).isEqualTo("Foo\n\nSOME=TEST\n");
  }

  @Test
  public void testGetLabel() {
    TransformWork work = create("Foo\n\nSOME=TEST\n");
    assertThat(work.getLabel("SOME")).isEqualTo("TEST");
    assertThat(work.getLabel("FOO")).isEqualTo(null);
  }

  @Test
  public void testReversable() {
    TransformWork work = create("Foo\n\nSOME=TEST\nOTHER=FOO\n");
    work.addLabel("EXAMPLE", "VALUE");
    work.replaceLabel("EXAMPLE", "OTHER VALUE");
    assertThat(work.getMessage()).isEqualTo("Foo\n\nSOME=TEST\nOTHER=FOO\nEXAMPLE=OTHER VALUE\n");
    work.removeLabel("EXAMPLE");
    assertThat(work.getMessage()).isEqualTo("Foo\n\nSOME=TEST\nOTHER=FOO\n");
  }

  @Test
  public void testConsole() throws IOException, ValidationException, RepoException {
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path base = fileSystem.getPath("foo");
    touchFile(base.resolve("not_important.txt"), "");
    Files.createDirectories(workdir.resolve("folder"));
    origin.addChange(0, base, "message");

    runWorkflow("test", ""
        + "def test(ctx):\n"
        + "   ctx.console.progress('Progress message')\n"
        + "   ctx.console.info('Informational message')\n"
        + "   ctx.console.warn('Warning message')\n");

    console.assertThat().onceInLog(MessageType.PROGRESS, "Progress message");
    console.assertThat().onceInLog(MessageType.INFO, "Informational message");
    console.assertThat().onceInLog(MessageType.WARNING, "Warning message");
  }

  @Test
  public void testConsoleError() throws IOException, ValidationException, RepoException {
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path base = fileSystem.getPath("foo");
    touchFile(base.resolve("not_important.txt"), "");
    Files.createDirectories(workdir.resolve("folder"));
    origin.addChange(0, base, "message");

    try {
      runWorkflow("test", ""
          + "def test(ctx):\n"
          + "   ctx.console.error('Error message')\n"
          + "   ctx.console.error('Another error message')\n");
      fail();
    } catch (ValidationException e) {
      assertThat(e).hasMessage("2 error(s) while executing test");
      console.assertThat()
          .onceInLog(MessageType.ERROR, "Error message")
          .onceInLog(MessageType.ERROR, "Another error message");
    }
  }

  @Test
  public void testRunGlob() throws IOException, ValidationException, RepoException {
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path base = fileSystem.getPath("testRunGlob");
    touchFile(base, "folder/file.txt");
    touchFile(base, "folder/subfolder/file.txt");
    touchFile(base, "folder/subfolder/file.java");

    Files.createDirectories(workdir.resolve("folder"));
    origin.addChange(0, base, "message");

    runWorkflow("test", ""
        + "def test(ctx):\n"
        + "    message = ''\n"
        + "    for f in sorted(ctx.run(glob(['**']))):\n"
        + "        message += f.path +'\\n'\n"
        + "    ctx.set_message(message)");

    assertThat(destination.processed.get(0).getChangesSummary()).isEqualTo(""
        + "folder/file.txt\n"
        + "folder/subfolder/file.java\n"
        + "folder/subfolder/file.txt\n"
    );
  }

  @Test
  public void testRunDynamicTransforms() throws IOException, ValidationException, RepoException {
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path base = fileSystem.getPath("testRunDynamicTransforms");
    touchFile(base, "folder/file1.txt");
    touchFile(base, "folder/file2.txt");
    touchFile(base, "folder/file3.txt");

    Files.createDirectories(workdir.resolve("folder"));
    origin.addChange(0, base, "message");

    runWorkflow("test", ""
        + "def test(ctx):\n"
        + "    message = ''\n"
        + "    for f in ctx.run(glob(['**.txt'])):\n"
        + "        ctx.run(core.move(f.path, 'other/folder/prefix_' + f.name))");

    assertThat(destination.processed.get(0).getWorkdir().keySet()).containsExactly(
        "other/folder/prefix_file1.txt",
        "other/folder/prefix_file2.txt",
        "other/folder/prefix_file3.txt");
  }

  @Test
  public void testRunFileOps() throws IOException, ValidationException, RepoException {
    checkPathOperations("folder/file.txt", ""
        + "path: folder/file.txt\n"
        + "name: file.txt\n"
        + "sibling path: folder/baz.txt\n"
        + "parent path: folder\n"
        + "parent parent path: \n"
        + "parent parent parent: None\n");
  }

  @Test
  public void testRunFileOpsSubSubFolder() throws IOException, ValidationException, RepoException {
    checkPathOperations("folder/other/file.txt", ""
        + "path: folder/other/file.txt\n"
        + "name: file.txt\n"
        + "sibling path: folder/other/baz.txt\n"
        + "parent path: folder/other\n"
        + "parent parent path: folder\n"
        + "parent parent parent: \n");
  }

  private void checkPathOperations(final String filePath, String output)
      throws IOException, RepoException, ValidationException {
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path base = fileSystem.getPath("foo");
    touchFile(base.resolve("not_important.txt"), "");
    Files.createDirectories(workdir.resolve("folder"));
    origin.addChange(0, base, "message");

    runWorkflow("test", ""
        + "def test(ctx):\n"
        + "    f = ctx.new_path('" + filePath + "')\n"
        + "    message = 'path: ' + f.path +'\\n'\n"
        + "    message += 'name: ' + f.name +'\\n'\n"
        + "    message += 'sibling path: ' + f.resolve_sibling('baz.txt').path + '\\n'\n"
        + "    message += 'parent path: ' + f.parent.path + '\\n'\n"
        + "    message += 'parent parent path: ' + f.parent.parent.path + '\\n'\n"
        + "    message += 'parent parent parent: ' + str(f.parent.parent.parent) + '\\n'\n"
        + "    ctx.set_message(message)");

    assertThat(destination.processed.get(0).getChangesSummary()).isEqualTo(
        output);
  }

  private Path touchFile(Path base, String path) throws IOException {
    Files.createDirectories(base.resolve(path).getParent());
    return Files.write(base.resolve(path), new byte[]{});
  }

  private void runWorkflow(String functionName, String function)
      throws RepoException, IOException, ValidationException {
    skylark.loadConfig(""
        + "core.project('foo')\n"
        + function + "\n"
        + "core.workflow(\n"
        + "    name = 'default',\n"
        + "    origin = testing.origin(),\n"
        + "    destination = testing.destination(),\n"
        + "    transformations = [" + functionName + "],\n"
        + "    authoring = authoring.pass_thru('foo <foo@foo.com>'),\n"
        + ")\n").getActiveMigration().run(workdir,/*sourceRef=*/null);
  }

  private void checkAddLabel(String originalMsg, String expected) {
    TransformWork work = create(originalMsg);
    work.addLabel("TEST", "VALUE");
    assertThat(work.getMessage()).isEqualTo(expected);
  }

  private TransformWork create(String msg) {
    return TransformWorks.of(FileSystems.getDefault().getPath("/"), msg, console);
  }
}
