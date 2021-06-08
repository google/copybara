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

package com.google.copybara.transform;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
import com.google.copybara.exception.NonReversibleValidationException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CopyOrMoveTest {

  private OptionsBuilder options;
  private Path checkoutDir;
  private TestingConsole console;
  private SkylarkTestExecutor skylark;

  @Before
  public void setup() throws IOException {
    FileSystem fs = Jimfs.newFileSystem();
    checkoutDir = fs.getPath("/test-checkoutDir");
    Files.createDirectories(checkoutDir);
    console = new TestingConsole();
    options = new OptionsBuilder()
        .setConsole(console);
    skylark = new SkylarkTestExecutor(options);
  }

  private TransformationStatus transform(Transformation t) throws Exception {
    return t.transform(TransformWorks.of(checkoutDir, "testmsg", console));
  }

  @Test
  public void testMoveAndItsReverse() throws Exception {
    CopyOrMove mover = skylark.eval("m", ""
        + "m = core.move(before = 'one.before', after = 'folder/one.after'\n"
        + ")");
    touch("one.before");
    transform(mover);

    assertThatPath(checkoutDir)
        .containsFiles("folder/one.after")
        .containsNoMoreFiles();

    transform(mover.reverse());

    assertThatPath(checkoutDir)
        .containsFiles("one.before")
        .containsNoMoreFiles();
  }

  @Test
  public void testCopyAndItsReverse() throws Exception {
    CopyOrMove copier = skylark.eval("m", ""
        + "m = core.copy(before = 'one.before', after = 'folder/one.after'\n"
        + ")");
    touch("one.before");
    transform(copier);

    assertThatPath(checkoutDir)
        .containsFiles("one.before")
        .containsFiles("folder/one.after")
        .containsNoMoreFiles();

    transform(copier.reverse());

    assertThatPath(checkoutDir)
        .containsFiles("one.before")
        .containsNoMoreFiles();
  }

  @Test
  public void testEmptyMoveOrCopy() {
    skylark.evalFails("core.move('foo', 'foo')",
        "Moving from the same folder to the same folder is a noop");
    skylark.evalFails("core.copy('foo', 'foo')",
        "Copying from the same folder to the same folder is a noop");
  }

  @Test
  public void testCopyAndItsReverse_folder() throws Exception {
    CopyOrMove copier = skylark.eval("m", ""
        + "m = core.copy(before = 'one', after = 'public'\n"
        + ")");
    touch("one/file1");
    touch("one/file2");
    transform(copier);

    assertThatPath(checkoutDir)
        .containsFiles("one/file1")
        .containsFiles("one/file2")
        .containsFiles("public/file1")
        .containsFiles("public/file2")
        .containsNoMoreFiles();

    transform(copier.reverse());

    assertThatPath(checkoutDir)
        .containsFiles("one/file1")
        .containsFiles("one/file2")
        .containsNoMoreFiles();
  }

  private void touch(String strPath) throws IOException {
    Path path = checkoutDir.resolve(strPath);
    Files.createDirectories(path.getParent());
    Files.write(path, new byte[]{});
  }

  @Test
  public void testMoveAndItsReverseWithPaths() throws Exception {
    CopyOrMove mover = skylark.eval("m", "m = "
        + "core.move("
        + "    before = 'foo',"
        + "    after = 'folder/bar',"
        + "    paths = glob(['**.java'])"
        + ")");

    touch("foo/a.java");
    touch("foo/other/b.java");
    touch("foo/c.txt");
    transform(mover);

    assertThatPath(checkoutDir)
        .containsFiles("foo/c.txt")
        .containsFiles("folder/bar/a.java")
        .containsFiles("folder/bar/other/b.java")
        .containsNoMoreFiles();

    transform(mover.reverse());

    assertThatPath(checkoutDir)
        .containsFiles("foo/a.java")
        .containsFiles("foo/other/b.java")
        .containsFiles("foo/c.txt")
        .containsNoMoreFiles();
  }

  @Test
  public void testCopyAndItsReverseWithPaths() throws Exception {
    CopyOrMove copier = skylark.eval("m", "m = "
        + "core.copy("
        + "    before = 'foo',"
        + "    after = 'folder/bar',"
        + "    paths = glob(['**.java'])"
        + ")");
    touch("foo/a.java");
    touch("foo/other/b.java");
    touch("foo/c.txt");
    transform(copier);

    assertThatPath(checkoutDir)
        .containsFiles("foo/c.txt")
        .containsFiles("foo/a.java")
        .containsFiles("foo/other/b.java")
        .containsFiles("folder/bar/a.java")
        .containsFiles("folder/bar/other/b.java")
        .containsNoMoreFiles();
    assertThrows(NonReversibleValidationException.class, () -> transform(copier.reverse()));
  }

  @Test
  public void testMoveOverwrite() throws Exception {
    CopyOrMove mover = skylark.eval("m", "m = "
        + "core.move("
        + "    before = 'foo',"
        + "    after = 'bar',"
        + "    overwrite = True,"
        + ")");
    Files.write(checkoutDir.resolve("foo"), "foo".getBytes(UTF_8));
    Files.write(checkoutDir.resolve("bar"), "bar".getBytes(UTF_8));
    transform(mover);

    assertThatPath(checkoutDir)
        .containsFile("bar", "foo")
        .containsNoMoreFiles();
    assertThrows(NonReversibleValidationException.class, () -> mover.reverse());
  }

  @Test
  public void testCopyOverwrite() throws Exception {
    CopyOrMove copier = skylark.eval("m", "m = "
        + "core.copy("
        + "    before = 'foo',"
        + "    after = 'bar',"
        + "    overwrite = True,"
        + ")");
    Files.write(checkoutDir.resolve("foo"), "foo".getBytes(UTF_8));
    Files.write(checkoutDir.resolve("bar"), "bar".getBytes(UTF_8));
    transform(copier);

    assertThatPath(checkoutDir)
        .containsFile("foo", "foo")
        .containsFile("bar", "foo")
        .containsNoMoreFiles();

    assertThrows(NonReversibleValidationException.class, () -> copier.reverse());
  }

  @Test
  public void testMoveNoOverwrite() throws Exception {
    CopyOrMove mover = skylark.eval("m", "m = core.move('foo', 'bar')");

    Files.write(checkoutDir.resolve("foo"), "foo".getBytes(UTF_8));
    Files.write(checkoutDir.resolve("bar"), "bar".getBytes(UTF_8));

    ValidationException thrown = assertThrows(ValidationException.class, () -> transform(mover));
    assertThat(thrown).hasMessageThat().contains("because it already exists");
  }

  @Test
  public void testSomeMove() throws Exception {
    CopyOrMove mover = skylark.eval("m", "m = core.move('foo', 'bar')");

    Files.createDirectories(checkoutDir.resolve("foo"));
    Files.createDirectories(checkoutDir.resolve("bar"));
    Files.write(checkoutDir.resolve("foo/foo.txt"), "foo".getBytes(UTF_8));
    Files.write(checkoutDir.resolve("bar/bar.txt"), "bar".getBytes(UTF_8));

    transform(mover);

    assertThatPath(checkoutDir)
        .containsFiles("bar/bar.txt")
        .containsFiles("bar/foo.txt")
        .containsNoMoreFiles();
  }

  @Test
  public void testCopyNoOverwrite() throws Exception {
    CopyOrMove copier = skylark.eval("m", "m = core.copy('foo', 'bar')");

    Files.write(checkoutDir.resolve("foo"), "foo".getBytes(UTF_8));
    Files.write(checkoutDir.resolve("bar"), "bar".getBytes(UTF_8));

    ValidationException thrown = assertThrows(ValidationException.class, () -> transform(copier));
    assertThat(thrown).hasMessageThat().contains("because it already exists");
  }

  @Test
  public void testDoesntExistMover() throws Exception {
    CopyOrMove mover = skylark.eval("m", ""
        + "m = core.move(before = 'blablabla', after = 'other')\n");
    TransformationStatus status = transform(mover);
    assertThat(status.isNoop()).isTrue();
    assertThat(status.getMessage()).contains("Error moving 'blablabla'. It doesn't exist");
  }

  @Test
  public void testDoesntExistCopier() throws Exception {
    CopyOrMove copier = skylark.eval("m", ""
        + "m = core.copy(before = 'blablabla', after = 'other')\n");
    TransformationStatus status = transform(copier);
    assertThat(status.isNoop()).isTrue();
    assertThat(status.getMessage()).contains("Error moving 'blablabla'. It doesn't exist");
  }

  @Test
  public void testDoesntExistAsWarning() throws Exception {
    options.workflowOptions.ignoreNoop = true;

    CopyOrMove mover = skylark.eval("m", ""
        + "m = core.move(before = 'blablabla', after = 'other')\n");

    TransformationStatus status = transform(mover);
    assertThat(status.isNoop()).isTrue();
    assertThat(status.getMessage()).matches(".*blablabla.*doesn't exist.*");
  }

  @Test
  public void testAbsoluteBefore() {
    for (String t : ImmutableList.of("move", "copy")) {
      options.setConsole(new TestingConsole());
      skylark.evalFails(
          "core." + t + "(before = '/blablabla', after = 'other')\n",
          "path must be relative.*/blablabla");
    }
  }

  @Test
  public void testAbsoluteAfter() {
    for (String t : ImmutableList.of("move", "copy")) {
      options.setConsole(new TestingConsole());
      skylark.evalFails(
          "core." + t + "(after = '/blablabla', before = 'other')\n",
          "path must be relative.*/blablabla");
    }
  }

  @Test
  public void testDotDot() {
    for (String t : ImmutableList.of("move", "copy")) {
      options.setConsole(new TestingConsole());
      skylark.evalFails(
          "core." + t + "(after = '../blablabla', before = 'other')\n",
          "path has unexpected [.] or [.][.] components.*[.][.]/blablabla");
    }

  }

  @Test
  public void testDestinationExist() throws Exception {
    touch("one");
    touch("two");
    CopyOrMove mover = skylark.eval("m", "m = core.move(before = 'one', after = 'two')\n");
    ValidationException thrown = assertThrows(ValidationException.class, () -> transform(mover));
    assertThat(thrown)
        .hasMessageThat()
        .contains("Cannot move file to '/test-checkoutDir/two' because it already exists");
  }

  @Test
  public void testDestinationExistDirectory() throws Exception {
    touch("one");
    CopyOrMove mover = skylark.eval("m", "m = core.move(before = 'one', after = 'folder/two')\n");
    transform(mover);

    assertThatPath(checkoutDir)
        .containsFiles("folder/two")
        .containsNoMoreFiles();
  }

  @Test
  public void testMoveToCheckoutDirRoot() throws Exception {
    CopyOrMove mover = skylark.eval("m",
        "m = core.move(before = 'third_party/java', after = '')\n");

    touch("third_party/java/one.java");
    touch("third_party/java/org/two.java");

    transform(mover);

    assertThatPath(checkoutDir)
        .containsFiles("one.java", "org/two.java")
        .containsNoDirs("third_party/java") // java folder is really moved. Not only the files.
        .containsDirs("third_party") // We requested to move java. We don't touch third_party
        .containsNoMoreFiles();
  }

  @Test
  public void testMoveFoldersWithExistingFiles() throws Exception {
    CopyOrMove mover = skylark.eval("m",
        "m = core.move(before = 'foo', after = 'bar')\n");

    touch("foo/dir/subdir/file1.txt");
    touch("bar/dir/subdir/file2.txt");

    transform(mover);

    assertThatPath(checkoutDir)
        .containsFiles("bar/dir/subdir/file1.txt", "bar/dir/subdir/file2.txt")
        .containsNoDirs("foo") // foo was moved
        .containsNoMoreFiles();
  }

  @Test
  public void testMoveFoldersDeleteRecursively() throws Exception {
    CopyOrMove mover = skylark.eval("m",
        "m = core.move(before = 'foo', after = 'bar', paths = glob(['**.txt']))\n");

    touch("foo/dir/subdir/file1.txt");
    touch("bar/dir/subdir/file2.txt");

    transform(mover);

    assertThatPath(checkoutDir)
        .containsFiles("bar/dir/subdir/file1.txt", "bar/dir/subdir/file2.txt")
        .containsNoDirs("foo") // foo was moved
        .containsNoMoreFiles();
    }

  @Test
  public void testMoveFoldersDeleteRecursively_moveFolders() throws Exception {
    CopyOrMove mover = skylark.eval("m",
        "m = core.move(before = 'foo', after = 'bar')\n");

    touch("foo/dir/subdir/file1.txt");
    touch("bar/dir/subdir/file2.txt");

    transform(mover);

    assertThatPath(checkoutDir)
        .containsFiles("bar/dir/subdir/file1.txt", "bar/dir/subdir/file2.txt")
        .containsNoDirs("foo") // foo was moved
        .containsNoMoreFiles();

    // We deleted temporary directories.

    try(Stream<Path> files = Files.list(checkoutDir)) {
      assertThat(files
          .anyMatch(e -> !e.getFileName().toString().contains("bar"))).isFalse();
    }
  }

  @Test
  public void testMoveToCheckoutDirRoot_samelevel() throws Exception {
    CopyOrMove mover = skylark.eval("m", "m = core.move(before = 'foo', after = 'bar')\n");

    touch("foo/one.java");
    touch("foo/two.cc");

    transform(mover);

    assertThatPath(checkoutDir)
        .containsFiles("bar/one.java")
        .containsFiles("bar/two.cc")
        .containsNoDirs("foo")
        .containsNoMoreFiles();
  }

  @Test
  public void testMoveToCheckoutDirRoot_samelevel_glob() throws Exception {
    CopyOrMove mover = skylark.eval("m",
        "m = core.move(before = 'foo', after = 'bar', paths = glob(['**.java']))\n");

    touch("foo/one.java");
    touch("foo/two.cc");

    transform(mover);

    assertThatPath(checkoutDir)
        .containsFiles("foo/two.cc") // Not deleted
        .containsFiles("bar/one.java")
        .containsNoMoreFiles();
  }

  @Test
  public void testMoveToCheckoutDirRoot_subdir() throws Exception {
    CopyOrMove mover = skylark.eval("m", "m = core.move(before = 'foo', after = 'foo/bar')\n");

    touch("foo/old/test.java");
    touch("foo/two.cc");

    transform(mover);

    assertThatPath(checkoutDir)
        .containsFiles("foo/bar/old/test.java", "foo/bar/two.cc")
        .containsNoDirs("foo.old")
        .containsNoMoreFiles();
  }

  /**
   * Tricky case: a folder called 'foo' that contains a file called 'foo'. If we move directory
   * contents to the root weneed to make sure we do it right so that we delete the folder before
   * copying the file.
   */
  @Test
  public void testMoveToCheckoutDirRoot_sameName() throws Exception {
    Transformation mover = skylark.eval("m", "m = core.move(before = 'foo', after = '')");

    touch("foo/foo");
    touch("foo/bar");

    transform(mover);

    assertThatPath(checkoutDir)
        .containsFiles("foo")
        .containsFiles("bar")
        .containsNoMoreFiles();
  }

  @Test
  public void testMoveToCheckoutDirRoot_subfolder() throws Exception {
    CopyOrMove mover = skylark.eval("m",
        "m = core.move(before = 'third_party/java', after = '')\n");

    touch("third_party/java/one.java");
    touch("third_party/java/org/two.java");

    transform(mover);

    assertThatPath(checkoutDir)
        .containsFiles("one.java", "org/two.java")
        .containsNoMoreFiles();
  }

  @Test
  public void testFailMoveToCheckoutDirRoot() throws Exception {
    CopyOrMove mover = skylark.eval("m",
        "m = core.move(before = 'third_party/java/one.java', after = '')\n");
    touch("third_party/java/one.java");
    ValidationException e = assertThrows(ValidationException.class, () -> transform(mover));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "Can only move a path to the root when the path is a "
                + "folder. But 'third_party/java/one.java' is a file. Use instead "
                + "core.move('third_party/java/one.java', 'one.java')");
      assertThatPath(checkoutDir).containsFiles("third_party/java/one.java")
          .containsNoMoreFiles();
  }

  @Test
  public void testFailMoveRootFileToCheckoutDirRoot() throws Exception {
    CopyOrMove mover = skylark.eval("m",
        "m = core.move(before = 'one.java', after = '')\n");
    touch("one.java");
    ValidationException e = assertThrows(ValidationException.class, () -> transform(mover));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "Can only move a path to the root when the path is a "
                + "folder. But 'one.java' is a file. Use instead "
                + "core.move('one.java', 'one.java')");
      assertThatPath(checkoutDir).containsFiles("one.java")
          .containsNoMoreFiles();
  }

  @Test
  public void testCopyToCheckoutDirRoot() throws Exception {
    CopyOrMove copier = skylark.eval("m",
        "m = core.copy(before = 'third_party/java', after = '')\n");
    touch("third_party/java/one.java");
    touch("third_party/java/org/two.java");

    transform(copier);

    assertThatPath(checkoutDir).containsFiles(
        "third_party/java/one.java",
        "third_party/java/org/two.java",
        "one.java",
        "org/two.java")
        .containsNoMoreFiles();
    assertThrows(NonReversibleValidationException.class, () -> transform(copier.reverse()));
  }

  /**
   * In bash if foo/ exist, the following command:
   *
   * <pre>
   *   mv third_party/java foo/
   * </pre>
   *
   * would create foo/java instead of moving the content of java to foo. Sadly, this behavior would
   * make Move non-reversible. As it would need to know if foo exist or not for computing the
   * reverse. This test ensures that Move doesn't work like that but instead puts the content
   * of java in foo, no matter that foo exist or not.
   */
  @Test
  public void testMoveDir() throws Exception {
    CopyOrMove mover = skylark.eval("m",
        "m = core.move(before = 'third_party/java', after = 'foo')\n");
    touch("third_party/java/one.java");
    touch("third_party/java/org/two.java");

    transform(mover);

    assertThatPath(checkoutDir)
        .containsFiles("foo/one.java", "foo/org/two.java")
        .containsNoMoreFiles();
  }

  @Test
  public void testMoveFromCheckoutDirRootToSubdir() throws Exception {
    CopyOrMove mover = skylark.eval("m",
        "m = core.move(before = '', after = 'third_party/java')\n");
    touch("file.java");
    transform(mover);

    assertThatPath(checkoutDir)
        .containsFiles("third_party/java/file.java")
        .containsNoMoreFiles();
  }

  @Test
  public void testCannotMoveFromRootToAlreadyExistingDir() throws Exception {
    CopyOrMove mover = skylark.eval("m",
        "m = core.move(before = '', after = 'third_party/java')\n");
    touch("third_party/java/bar.java");
    touch("third_party/java/foo.java");

    ValidationException e = assertThrows(ValidationException.class, () -> transform(mover));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "Files already exist in " + checkoutDir + "/third_party/java: [bar.java, foo.java]");
  }

  @Test
  public void testMoveWithGlob() throws Exception {
    ExplicitReversal t = skylark.eval("m", "m = "
        + "core.transform([\n"
        + "  core.move('foo','' , paths = glob(['**.txt'])),\n"
        + "  core.move('foo/bar', 'bar'),\n"
        + "])\n");
    touch("foo/baz");
    touch("foo/foo.txt");
    touch("foo/bar");
    transform(t);

    assertThatPath(checkoutDir)
        .containsFiles("foo.txt", "bar", "foo/baz")
        .containsNoMoreFiles();

    transform(t.reverse());

    assertThatPath(checkoutDir)
        .containsFiles("foo/foo.txt", "foo/bar", "foo/baz")
        .containsNoMoreFiles();
  }

  @Test
  public void testCanMoveWithPathGlobsSpecificFile() throws Exception {
    ExplicitReversal t = skylark.eval("m", "m = "
        + "core.transform([\n"
        + "  core.move('foo','' , paths = glob(['foo.txt'])),\n"
        + "  core.move('foo/bar', 'bar'),\n"
        + "])\n");
    touch("foo/baz");
    touch("foo/foo.txt");
    touch("foo/bar");
    transform(t);

    assertThatPath(checkoutDir)
        .containsFiles("foo.txt", "bar", "foo/baz")
        .containsNoMoreFiles();

    transform(t.reverse());

    assertThatPath(checkoutDir)
        .containsFiles("foo/foo.txt", "foo/bar", "foo/baz")
        .containsNoMoreFiles();
  }

  @Test
  public void testCannotMoveWithPathGlobsSpecificFile() throws Exception {
    CopyOrMove t = skylark.eval("m", "m = core.move('foo', '' , paths = glob(['foo.txt']))");
    touch("foo/foo.txt");
    touch("foo.txt");
    ValidationException e = assertThrows(ValidationException.class, () -> transform(t));
    assertThat(e)
        .hasMessageThat()
        .contains("Cannot move file to '" + checkoutDir + "/foo.txt' because it already exists");
  }

  @Test
  public void errorForMissingBefore() {
    assertThrows(
        ValidationException.class,
        () -> skylark.<CopyOrMove>eval("m", "m = core.move(after = 'third_party/java')\n"));
    console
        .assertThat()
        .onceInLog(MessageType.ERROR, ".*missing 1 required positional argument: before.*");
  }

  @Test
  public void errorForMissingAfter() {
    assertThrows(
        ValidationException.class,
        () -> skylark.<CopyOrMove>eval("m", "m = core.move(before = 'third_party/java')\n"));
    console
        .assertThat()
        .onceInLog(MessageType.ERROR, ".*missing 1 required positional argument: after.*");
  }

  @Test
  public void copyWithRegex() throws Exception {
    CopyOrMove copier =
        skylark.eval(
            "m",
            "m = core.copy(before = '${x}.before', after = 'folder/${x}.after', regex_groups = {"
                + " 'x': '...'})");
    touch("one.before");
    touch("foo.before");
    touch("not_matched.before");
    transform(copier);

    assertThatPath(checkoutDir)
        .containsFiles("one.before")
        .containsFiles("foo.before")
        .containsFiles("not_matched.before")
        .containsFiles("folder/one.after")
        .containsFiles("folder/foo.after")
        .containsNoMoreFiles();
  }

  @Test
  public void copyWithRegex_toSubdirectory() throws Exception {
    CopyOrMove copier =
        skylark.eval(
            "m",
            "m = core.copy(before = '${x}', after = 'folder/${x}', regex_groups = {"
                + " 'x': '.*'})");
    touch("file1");
    touch("folder/file2");
    transform(copier);

    assertThatPath(checkoutDir)
        .containsFiles("file1")
        .containsFiles("folder/file1")
        .containsFiles("folder/file2")
        .containsFiles("folder/folder/file2")
        .containsNoMoreFiles();
  }

  @Test
  public void copyWithRegex_fileNameCollision() throws Exception {
    CopyOrMove copier =
        skylark.eval(
            "m",
            "m = core.copy(before = '${x}/${y}/file', after = '${x}/file', regex_groups = {"
                + " 'x': '.*', 'y': '.*'})");
    touch("folder/foo/file");
    touch("folder/bar/file");
    ValidationException e = assertThrows(ValidationException.class, () -> transform(copier));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "Cannot move file to '" + checkoutDir + "/folder/file' because it already exists");
  }

  @Test
  public void copyWithRegex_noop() throws Exception {
    CopyOrMove copier =
        skylark.eval(
            "m",
            "m = core.copy(before = '${x}.java', after = '${x}.java.copy', regex_groups = {"
                + " 'x': '.*'})");
    touch("folder/foo/file.py");

    TransformationStatus status = transform(copier);
    assertThatPath(checkoutDir).containsFiles("folder/foo/file.py").containsNoMoreFiles();
    assertThat(status.isNoop()).isTrue();
    assertThat(status.getMessage()).contains("was a no-op because it didn't match any file");
  }

  @Test
  public void copyWithRegex_regexIsPortionOfPathComponent() throws Exception {
    CopyOrMove copier =
        skylark.eval(
            "m",
            "m = core.copy(before = 'folder_${x}/${y}', after = 'folder/${x}/${y}', regex_groups ="
                + " { 'x': 'foo|bar', 'y': '.*'})");
    touch("folder_foo/file");
    touch("folder_other/file");

    transform(copier);
    assertThatPath(checkoutDir)
        .containsFiles("folder/foo/file")
        .containsFiles("folder_foo/file")
        .containsFiles("folder_other/file")
        .containsNoMoreFiles();
  }

  @Test
  public void copyWithRegex_regexIncludesPathSeparator() throws Exception {
    CopyOrMove copier =
        skylark.eval(
            "m",
            "m = core.copy(before = 'folder/${x}', after = 'folder_other/${x}', regex_groups ="
                + " { 'x': 'foo/.*/bar'})");
    touch("folder/foo/file/bar");
    touch("folder/xxx/yyy/zzz");

    transform(copier);
    assertThatPath(checkoutDir)
        .containsFiles("folder/foo/file/bar")
        .containsFiles("folder/xxx/yyy/zzz")
        .containsFiles("folder_other/foo/file/bar")
        .containsNoMoreFiles();
  }

  @Test
  public void copyWithRegex_notReversible() throws Exception {
    CopyOrMove copier =
        skylark.eval(
            "m",
            "m = core.copy(before = 'folder/${x}', after = 'folder_other/${x}', regex_groups ="
                + " { 'x': '.*'})");
    assertThrows(NonReversibleValidationException.class, copier::reverse);
  }

  @Test
  public void moveWithRegex_deletesEmptiedFolders() throws Exception {
    CopyOrMove copier =
        skylark.eval(
            "m",
            "m = core.move(before = 'folder/${x}', after = 'other_folder/${x}', regex_groups = {"
                + " 'x': '.*'})");
    touch("folder/file");
    transform(copier);

    assertThatPath(checkoutDir)
        .containsFiles("other_folder/file")
        .containsNoDirs("folder")
        .containsNoMoreFiles();
  }
}
