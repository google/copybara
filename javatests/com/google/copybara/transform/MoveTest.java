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

import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.jimfs.Jimfs;
import com.google.copybara.Core;
import com.google.copybara.NonReversibleValidationException;
import com.google.copybara.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.copybara.util.console.testing.TestingConsole.MessageType;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MoveTest {

  private OptionsBuilder options;
  private Path checkoutDir;
  private TestingConsole console;
  private SkylarkTestExecutor skylark;

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() throws IOException {
    FileSystem fs = Jimfs.newFileSystem();
    checkoutDir = fs.getPath("/test-checkoutDir");
    Files.createDirectories(checkoutDir);
    console = new TestingConsole();
    options = new OptionsBuilder()
        .setConsole(console);
    skylark = new SkylarkTestExecutor(options, Core.class, Move.class);
  }

  private void transform(Move mover) throws IOException, ValidationException {
    mover.transform(TransformWorks.of(checkoutDir, "testmsg", console));
  }

  @Test
  public void testMoveAndItsReverse() throws Exception {
    Move mover = skylark.eval("m", ""
        + "m = core.move(before = 'one.before', after = 'folder/one.after'\n"
        + ")");
    Files.write(checkoutDir.resolve("one.before"), new byte[]{});
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
  public void testMoveAndItsReverseWithPaths() throws Exception {
    Move mover = skylark.eval("m", "m = "
        + "core.move("
        + "    before = 'foo',"
        + "    after = 'folder/bar',"
        + "    paths = glob(['**.java'])"
        + ")");
    Files.createDirectories(checkoutDir.resolve("foo/other"));
    Files.write(checkoutDir.resolve("foo/a.java"), new byte[]{});
    Files.write(checkoutDir.resolve("foo/other/b.java"), new byte[]{});
    Files.write(checkoutDir.resolve("foo/c.txt"), new byte[]{});
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
  public void testMoveOverwrite() throws Exception {
    Move mover = skylark.eval("m", "m = "
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

    thrown.expect(NonReversibleValidationException.class);
    mover.reverse();
  }

  @Test
  public void testMoveNoOverwrite() throws Exception {
    Move mover = skylark.eval("m", "m = core.move('foo', 'bar')");

    Files.write(checkoutDir.resolve("foo"), "foo".getBytes(UTF_8));
    Files.write(checkoutDir.resolve("bar"), "bar".getBytes(UTF_8));

    thrown.expect(ValidationException.class);
    thrown.expectMessage("because it already exists");
    transform(mover);
  }

  @Test
  public void testDoesntExist() throws Exception {
    Move mover = skylark.eval("m", ""
        + "m = core.move(before = 'blablabla', after = 'other')\n");
    thrown.expect(ValidationException.class);
    thrown.expectMessage("Error moving 'blablabla'. It doesn't exist");
    transform(mover);
  }

  @Test
  public void testDoesntExistAsWarning() throws Exception {
    options.workflowOptions.ignoreNoop = true;

    Move mover = skylark.eval("m", ""
        + "m = core.move(before = 'blablabla', after = 'other')\n");

    transform(mover);

    console.assertThat()
        .onceInLog(MessageType.WARNING, ".*blablabla.*doesn't exist.*");
  }

  @Test
  public void testAbsoluteBefore() throws Exception {
    skylark.evalFails(
        "core.move(before = '/blablabla', after = 'other')\n",
        "path must be relative.*/blablabla");
  }

  @Test
  public void testAbsoluteAfter() throws Exception {
    skylark.evalFails(
        "core.move(after = '/blablabla', before = 'other')\n",
        "path must be relative.*/blablabla");
  }

  @Test
  public void testDotDot() throws Exception {
    skylark.evalFails(
        "core.move(after = '../blablabla', before = 'other')\n",
        "path has unexpected [.] or [.][.] components.*[.][.]/blablabla");
  }

  @Test
  public void testDestinationExist() throws Exception {
    Files.write(checkoutDir.resolve("one"), new byte[]{});
    Files.write(checkoutDir.resolve("two"), new byte[]{});
    Move mover = skylark.eval("m", "m = core.move(before = 'one', after = 'two')\n");
    thrown.expect(ValidationException.class);
    thrown.expectMessage("Cannot move file to '/test-checkoutDir/two' because it already exists");
    transform(mover);
  }

  @Test
  public void testDestinationExistDirectory() throws Exception {
    Files.createDirectories(checkoutDir.resolve("folder"));
    Files.write(checkoutDir.resolve("one"), new byte[]{});
    Move mover = skylark.eval("m", "m = core.move(before = 'one', after = 'folder/two')\n");
    transform(mover);

    assertThatPath(checkoutDir)
        .containsFiles("folder/two")
        .containsNoMoreFiles();
  }

  @Test
  public void testMoveToCheckoutDirRoot() throws Exception {
    Move mover = skylark.eval("m",
        "m = core.move(before = 'third_party/java', after = '')\n");
    Files.createDirectories(checkoutDir.resolve("third_party/java/org"));
    Files.write(checkoutDir.resolve("third_party/java/one.java"), new byte[]{});
    Files.write(checkoutDir.resolve("third_party/java/org/two.java"), new byte[]{});

    transform(mover);

    assertThatPath(checkoutDir)
        .containsFiles("one.java", "org/two.java")
        .containsNoMoreFiles();
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
    Move mover = skylark.eval("m",
        "m = core.move(before = 'third_party/java', after = 'foo')\n");
    Files.createDirectories(checkoutDir.resolve("third_party/java/org"));
    Files.createDirectories(checkoutDir.resolve("foo"));
    Files.write(checkoutDir.resolve("third_party/java/one.java"), new byte[]{});
    Files.write(checkoutDir.resolve("third_party/java/org/two.java"), new byte[]{});

    transform(mover);

    assertThatPath(checkoutDir)
        .containsFiles("foo/one.java", "foo/org/two.java")
        .containsNoMoreFiles();
  }

  @Test
  public void testMoveFromCheckoutDirRootToSubdir() throws Exception {
    Move mover = skylark.eval("m",
        "m = core.move(before = '', after = 'third_party/java')\n");
    Files.write(checkoutDir.resolve("file.java"), new byte[]{});
    transform(mover);

    assertThatPath(checkoutDir)
        .containsFiles("third_party/java/file.java")
        .containsNoMoreFiles();
  }

  @Test
  public void testCannotMoveFromRootToAlreadyExistingDir() throws Exception {
    Move mover = skylark.eval("m",
        "m = core.move(before = '', after = 'third_party/java')\n");
    Files.createDirectories(checkoutDir.resolve("third_party/java"));
    Files.write(checkoutDir.resolve("third_party/java/bar.java"), new byte[]{});
    Files.write(checkoutDir.resolve("third_party/java/foo.java"), new byte[]{});

    thrown.expect(ValidationException.class);
    thrown.expectMessage(
        "Files already exist in " + checkoutDir + "/third_party/java: [bar.java, foo.java]");
    transform(mover);
  }

  @Test
  public void errorForMissingBefore() throws Exception {
    try {
      skylark.<Move>eval("m", "m = core.move(after = 'third_party/java')\n");
      Assert.fail();
    } catch (ValidationException expected) {}

    console.assertThat()
        .onceInLog(MessageType.ERROR, ".*missing mandatory .* 'before'.*");
  }

  @Test
  public void errorForMissingAfter() throws Exception {
    try {
      skylark.<Move>eval("m", "m = core.move(before = 'third_party/java')\n");
      Assert.fail();
    } catch (ValidationException expected) {}

    console.assertThat()
        .onceInLog(MessageType.ERROR, ".*missing mandatory .* 'after'.*");
  }
}
