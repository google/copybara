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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.authoring.Author;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.RecordsProcessCallDestination;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TestingModule;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.transform.ExplicitReversal;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.TimeZone;
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
    options.setForce(true); // We don't care about force for this test
    skylark = new SkylarkTestExecutor(options, TestingModule.class);
    workdir = Files.createTempDirectory("workdir");
  }

  @Test
  public void testAddLabel() throws Exception {
    checkAddLabel("foo", "foo\n\nTEST=VALUE\n");
  }

  @Test
  public void testAddLabelToGroup() throws Exception {
    checkAddLabel("foo\n\nA=B\n\n", "foo\n\nA=B\nTEST=VALUE\n");
  }

  @Test
  public void testAddLabelNoEmptyLineBeforeGroup() throws Exception {
    checkAddLabel("foo\nA=B\n\n", "foo\nA=B\n\nTEST=VALUE\n");
  }

  @Test
  public void testAddLabelNoGroupNoEndLine() throws Exception {
    checkAddLabel("foo\nA=B", "foo\nA=B\n\nTEST=VALUE\n");
  }

  @Test
  public void testAddOrReplaceExistingLabel() throws Exception {
    checkLabelWithSkylark("Foo\n\nSOME=TEST\nother=other\n",
        "ctx.add_or_replace_label('SOME', 'REPLACED')",
        "Foo\n\nSOME=REPLACED\nother=other\n");
  }

  @Test
  public void testAddTextBeforeLabels() throws Exception {
    checkLabelWithSkylark("Foo\n\nSOME=TEST\n",
        "ctx.add_text_before_labels('\\nFixes #1234')",
        "Foo\n\nFixes #1234\n\nSOME=TEST\n");
  }

  @Test
  public void testAddLabelWhitespaceInMsg() throws Exception {
    checkAddLabel("    foo", "    foo\n\nTEST=VALUE\n");
  }

  @Test
  public void testAddLabelLastParagraphList() throws Exception {
    checkLabelWithSkylark(""
            + "Foo\n"
            + "\n"
            + "  - list\n"
            + "  - other\n",
        "ctx.add_label('TEST', 'VALUE')",
        ""
            + "Foo\n"
            + "\n"
            + "  - list\n"
            + "  - other\n"
            + "\n"
            + "TEST=VALUE\n");
  }

  @Test
  public void testAddLabelLastParagraphContainsLabel() throws Exception {
    checkLabelWithSkylark(""
            + "Foo\n"
            + "\n"
            + "  - list\n"
            + "I_AM_A: Label\n"
            + "  - other\n",
        "ctx.add_label('TEST', 'VALUE')",
        ""
            + "Foo\n"
            + "\n"
            + "  - list\n"
            + "I_AM_A: Label\n"
            + "  - other\n"
            + "TEST=VALUE\n");
  }

  @Test
  public void testAddTextBeforeLabelsNoGroup() throws Exception {
    checkLabelWithSkylark("Foo\n",
        "ctx.add_text_before_labels('\\nFixes #1234')",
        "Foo\n\nFixes #1234\n");
  }

  @Test
  public void testReplaceLabel() throws Exception {
    checkLabelWithSkylark("Foo\n\nSOME=TEST\n",
        "ctx.replace_label('SOME', 'REPLACED')",
        "Foo\n\nSOME=REPLACED\n");
  }

  @Test
  public void testReplaceNonExistentLabel() throws Exception {
    checkLabelWithSkylark("Foo\n\nFOO=TEST\n",
        "ctx.replace_label('SOME', 'REPLACED')",
        "Foo\n\nFOO=TEST\n");
  }

  @Test
  public void testReplaceNonExistentLabelNoGroup() throws Exception {
    checkLabelWithSkylark("Foo\n",
        "ctx.replace_label('SOME', 'REPLACED')",
        "Foo\n");
  }

  @Test
  public void testsDeleteNotFound() throws Exception {
    checkLabelWithSkylark("Foo\n",
        "ctx.remove_label('SOME', False)",
        "Foo\n");
  }

  @Test
  public void testsDeleteNotFound_whole() throws Exception {
    checkLabelWithSkylark("Foo\n",
        "ctx.remove_label('SOME', True)",
        "Foo\n");
  }

  @Test
  public void testsDeleteLabel() throws Exception {
    checkLabelWithSkylark("Foo\n\nSOME=TEST\n",
        "ctx.remove_label('SOME', False)",
        "Foo\n");
  }

  @Test
  public void testsDeleteLabel_whole() throws Exception {
    checkLabelWithSkylark("Foo\n\nSOME=TEST\n",
        "ctx.remove_label('SOME', True)",
        "Foo\n");
  }

  @Test
  public void testsDeleteOnlyOneLabel() throws Exception {
    checkLabelWithSkylark("Foo\n\nSOME=TEST\nOTHER=aaa\n",
        "ctx.remove_label('SOME', False)",
        "Foo\n\nOTHER=aaa\n");
  }

  @Test
  public void testsDeleteOnlyOneLabel_whole() throws Exception {
    checkLabelWithSkylark("Foo\n\nSOME=TEST\nOTHER=aaa\n",
        "ctx.remove_label('SOME', True)",
        "Foo\n\nOTHER=aaa\n");
  }

  @Test
  public void testsDeleteNonExistentLabel() throws Exception {
    checkLabelWithSkylark("Foo\n\nSOME=TEST\n",
        "ctx.remove_label('FOO', False)",
        "Foo\n\nSOME=TEST\n");
  }

  @Test
  public void testsDeleteNonExistentLabel_whole() throws Exception {
    checkLabelWithSkylark("Foo\n\nSOME=TEST\n",
        "ctx.remove_label('FOO', True)",
        "Foo\n\nSOME=TEST\n");
  }

  private Change<DummyRevision> toChange(DummyRevision dummyRevision) {
    return TransformWorks.toChange(dummyRevision, ORIGINAL_AUTHOR);
  }

  @Test
  public void testDateFormat() throws Exception {
    checkLabelWithSkylark("", "ctx.set_message(ctx.now_as_string())",
        DateTimeFormatter.ofPattern("yyyy-MM-dd").format(ZonedDateTime.now(ZoneOffset.UTC)));

    boolean isDst = TimeZone.getTimeZone("Europe/Madrid").inDaylightTime(new Date());
    checkLabelWithSkylark("",
        "ctx.set_message(ctx.now_as_string('yyyy MM dd XXX', 'Europe/Madrid'))",
        DateTimeFormatter.ofPattern("yyyy MM dd").format(
            ZonedDateTime.now(ZoneId.of("Europe/Madrid"))) + (isDst ? " +02:00" : " +01:00"));

    checkLabelWithSkylark("",
        "ctx.set_message(ctx.now_as_string('yyyy MM dd VV', 'Europe/Madrid'))",
        DateTimeFormatter.ofPattern("yyyy MM dd").format(
            ZonedDateTime.now(ZoneId.of("Europe/Madrid"))) + " Europe/Madrid");
  }

  @Test
  public void testGetLabel() {
    TransformWork work = create("Foo\n\nSOME=TEST\n").withChanges(
        new Changes(ImmutableList.of(
            toChange(
                new DummyRevision("1")
                    .withLabels(ImmutableMap.of("ONE", "one", "SOME", "SHOULD_NOT_HAPPEN"))),
            toChange(
                new DummyRevision("2").withLabels(ImmutableMap.of("TWO", "two"))),
            toChange(
                new DummyRevision("3").withLabels(ImmutableMap.of("THREE", "three")))
        ), ImmutableList.of())).withResolvedReference(new DummyRevision("resolved").withLabels(
        ImmutableMap.of("RESOLVED", "resolved",
            "ONE", "SHOULD_NOT_HAPPEN",
            "SOME", "SHOULD_NOT_HAPPEN")));

    assertThat(work.getLabel("SOME")).isEqualTo("TEST");
    assertThat(work.getLabel("ONE")).isEqualTo("one");
    assertThat(work.getLabel("TWO")).isEqualTo("two");
    assertThat(work.getLabel("THREE")).isEqualTo("three");
    assertThat(work.getLabel("RESOLVED")).isEqualTo("resolved");
    assertThat(work.getLabel("FOO")).isEqualTo(null);
  }

  @Test
  public void testReversable() {
    TransformWork work = create("Foo\n\nSOME=TEST\nOTHER=FOO\n");
    work.addOrReplaceLabel("EXAMPLE", "VALUE", "=");
    work.replaceLabel("EXAMPLE", "OTHER VALUE", "=", true);
    assertThat(work.getMessage()).isEqualTo("Foo\n\nSOME=TEST\nOTHER=FOO\nEXAMPLE=OTHER VALUE\n");
    work.removeLabel("EXAMPLE", /*wholeMessage=*/true);
    assertThat(work.getMessage()).isEqualTo("Foo\n\nSOME=TEST\nOTHER=FOO\n");
  }

  @Test
  public void testConsole() throws IOException, ValidationException, RepoException {
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path base = fileSystem.getPath("foo");
    touchFile(base.resolve("not_important.txt"), "");
    Files.createDirectories(workdir.resolve("folder"));
    origin.addChange(0, base, "message", /*matchesGlob=*/true);

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
    origin.addChange(0, base, "message", /*matchesGlob=*/true);

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
  public void testWithCurrentRev() {
    assertThat(TransformWorks.of(workdir, "test", console).withCurrentRev(new DummyRevision("a"))
        .isInsideExplicitTransform()).isFalse();
  }

  @Test
  public void testWithConsole() {
    assertThat(TransformWorks.of(workdir, "test", console)
        .insideExplicitTransform()
        .withConsole(console)
        .isInsideExplicitTransform()).isTrue();
  }

  @Test
  public void testRunGlob() throws IOException, ValidationException, RepoException {
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path base = fileSystem.getPath("testRunGlob");
    touchFile(base, "folder/file.txt");
    touchFile(base, "folder/subfolder/file.txt");
    touchFile(base, "folder/subfolder/file.java");

    Files.createDirectories(workdir.resolve("folder"));
    origin.addChange(0, base, "message", /*matchesGlob=*/true);

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
    origin.addChange(0, base, "message", /*matchesGlob=*/true);

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
  public void testReadAndWrite() throws IOException, ValidationException, RepoException {
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path base = fileSystem.getPath("testRunDynamicTransforms");
    writeFile(base, "folder/file.txt", "foo");

    Files.createDirectories(workdir.resolve("folder"));
    origin.addChange(0, base, "message", /*matchesGlob=*/true);

    String now = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        .format(ZonedDateTime.now(ZoneOffset.UTC));

    runWorkflow("test", ""
        + "def test(ctx):\n"
        + "    path = ctx.new_path('folder/file.txt')\n"
        + "    ctx.read_path(path)\n"
        + "    ctx.write_path(path, ctx.read_path(path) + ctx.now_as_string())");

    assertThat(destination.processed.get(0).getWorkdir())
        .containsEntry("folder/file.txt", "foo" + now);
  }

  @Test
  public void testTreeStateRestored() throws IOException, ValidationException, RepoException {
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path base = fileSystem.getPath("testTreeStateRestored");
    writeFile(base, "folder/file1.txt", "aaa");
    writeFile(base, "folder/file2.txt", "aaa");
    writeFile(base, "folder/file3.txt", "aaa");

    Files.createDirectories(workdir.resolve("folder"));
    origin.addChange(0, base, "message", /*matchesGlob=*/true);

    runWorkflow("test", ""
        + "def test(ctx):\n"
        + "    message = ''\n"
        + "    for f in ctx.run(glob(['**.txt'])):\n"
        + "        ctx.run(core.move(f.path, 'prefix_' + f.name))\n"
        + "        ctx.run(core.replace("
        + "before ='aaa', after = 'bbb', paths = glob(['prefix_' + f.name])))");

    assertThat(destination.processed.get(0).getWorkdir())
        .containsExactlyEntriesIn(ImmutableMap.of(
            "prefix_file1.txt", "bbb",
            "prefix_file2.txt", "bbb",
            "prefix_file3.txt", "bbb"));
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
    origin.addChange(0, base, "message", /*matchesGlob=*/true);

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

  @Test
  public void testAttr() throws RepoException, IOException, ValidationException {
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path base = fileSystem.getPath("foo");
    Files.createDirectories(base);
    Files.write(base.resolve("file.txt"), "1234567890".getBytes(UTF_8));
    Files.createDirectories(workdir.resolve("folder"));
    origin.addChange(0, base, "message", /*matchesGlob=*/true);

    runWorkflow("test", "def test(ctx):\n"
        + "    size = ctx.run(glob(['**.txt']))[0].attr.size\n"
        + "    ctx.console.info('File size: ' + str(size))");

    console.assertThat().onceInLog(MessageType.INFO, "File size: 10");
  }

  private Path touchFile(Path base, String path) throws IOException {
    return writeFile(base, path, "");
  }

  private Path writeFile(Path base, String path, String content) throws IOException {
    Files.createDirectories(base.resolve(path).getParent());
    return Files.write(base.resolve(path), content.getBytes(UTF_8));
  }

  private void runWorkflow(String functionName, String function)
      throws RepoException, IOException, ValidationException {
    skylark.loadConfig(""
        + function + "\n"
        + "core.workflow(\n"
        + "    name = 'default',\n"
        + "    origin = testing.origin(),\n"
        + "    destination = testing.destination(),\n"
        + "    transformations = [" + functionName + "],\n"
        + "    authoring = authoring.pass_thru('foo <foo@foo.com>'),\n"
        + ")\n").getMigration("default").run(workdir, /*sourceRef=*/null);
  }

  private void checkAddLabel(String originalMsg, String expected) throws Exception {
    checkLabelWithSkylark(originalMsg,
        "ctx.add_label('TEST','VALUE')",
        expected);
    checkLabelWithSkylark(originalMsg,
        "ctx.add_or_replace_label('TEST','VALUE')",
        expected);
  }

  private TransformWork create(String msg) {
    return TransformWorks.of(FileSystems.getDefault().getPath("/"), msg, console);
  }

  private void checkLabelWithSkylark(String originalMsg, final String transform,
      String expectedOutputMsg)
      throws Exception {
    TransformWork work = create(originalMsg);
    ExplicitReversal t = skylark.eval("t", ""
        + "def user_transform(ctx):\n"
        + "    " + transform + "\n"
        + "t = core.transform([user_transform])");
    t.transform(work);
    assertThat(work.getMessage()).isEqualTo(expectedOutputMsg);
  }
}
