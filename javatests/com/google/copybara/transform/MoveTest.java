package com.google.copybara.transform;

import static com.google.copybara.testing.FileSubjects.assertThatPath;

import com.google.common.jimfs.Jimfs;
import com.google.copybara.Core;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
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
  private Path workdir;
  private TestingConsole console;
  private SkylarkTestExecutor skylark;

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() throws IOException {
    FileSystem fs = Jimfs.newFileSystem();
    workdir = fs.getPath("/test-workdir");
    Files.createDirectories(workdir);
    console = new TestingConsole();
    options = new OptionsBuilder()
        .setConsole(console);
    skylark = new SkylarkTestExecutor(options, Core.class, Move.class);
  }

  @Test
  public void testMoveAndItsReverse() throws Exception {
    Move mover = skylark.eval("m", ""
        + "m = core.move(before = 'one.before', after = 'folder/one.after'\n"
        + ")");
    Files.write(workdir.resolve("one.before"), new byte[]{});
    mover.transform(workdir, console);

    assertThatPath(workdir)
        .containsFiles("folder/one.after")
        .containsNoMoreFiles();

    mover.reverse().transform(workdir, console);

    assertThatPath(workdir)
        .containsFiles("one.before")
        .containsNoMoreFiles();
  }

  @Test
  public void testDoesntExist() throws Exception {
    Move mover = skylark.eval("m", ""
        + "m = core.move(before = 'blablabla', after = 'other')\n");
    thrown.expect(ValidationException.class);
    thrown.expectMessage("Error moving 'blablabla'. It doesn't exist");
    mover.transform(workdir, console);
  }

  @Test
  public void testDoesntExistAsWarning() throws Exception {
    options.workflowOptions.ignoreNoop = true;

    Move mover = skylark.eval("m", ""
        + "m = core.move(before = 'blablabla', after = 'other')\n");

    mover.transform(workdir, console);

    console.assertThat()
        .onceInLog(MessageType.WARNING, ".*blablabla.*doesn't exist.*");
  }

  @Test
  public void testAbsoluteBefore() throws Exception {
    try {
      skylark.eval("m", ""
          + "m = core.move(before = '/blablabla', after = 'other')\n");
      Assert.fail();
    } catch (ConfigValidationException expected) {}

    console.assertThat()
        .onceInLog(MessageType.ERROR, ".*'/blablabla'.*is not a relative path.*");
  }

  @Test
  public void testAbsoluteAfter() throws Exception {
    try {
      skylark.eval("m", ""
          + "m = core.move(after = '/blablabla', before = 'other')\n");
      Assert.fail();
    } catch (ConfigValidationException expected) {}

    console.assertThat()
        .onceInLog(MessageType.ERROR, ".*'/blablabla'.*is not a relative path.*");
  }

  @Test
  public void testDotDot() throws Exception {
    try {
      skylark.eval("m", ""
          + "m = core.move(after = '../blablabla', before = 'other')\n");
      Assert.fail();
    } catch (ConfigValidationException expected) {}

    console.assertThat()
        .onceInLog(MessageType.ERROR, ".*'[.][.]/blablabla'.*contains unexpected [.][.].*");
  }

  @Test
  public void testDestinationExist() throws Exception {
    Files.write(workdir.resolve("one"), new byte[]{});
    Files.write(workdir.resolve("two"), new byte[]{});
    Move mover = skylark.eval("m", "m = core.move(before = 'one', after = 'two')\n");
    thrown.expect(ValidationException.class);
    thrown.expectMessage("Cannot move file to '/test-workdir/two' because it already exists");
    mover.transform(workdir, console);
  }

  @Test
  public void testDestinationExistDirectory() throws Exception {
    Files.createDirectories(workdir.resolve("folder"));
    Files.write(workdir.resolve("one"), new byte[]{});
    Move mover = skylark.eval("m", "m = core.move(before = 'one', after = 'folder/two')\n");
    mover.transform(workdir, console);

    assertThatPath(workdir)
        .containsFiles("folder/two")
        .containsNoMoreFiles();
  }

  @Test
  public void testMoveToWorkdirRoot() throws Exception {
    Move mover = skylark.eval("m",
        "m = core.move(before = 'third_party/java', after = '')\n");
    Files.createDirectories(workdir.resolve("third_party/java/org"));
    Files.write(workdir.resolve("third_party/java/one.java"), new byte[]{});
    Files.write(workdir.resolve("third_party/java/org/two.java"), new byte[]{});

    mover.transform(workdir, console);

    assertThatPath(workdir)
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
    Files.createDirectories(workdir.resolve("third_party/java/org"));
    Files.createDirectories(workdir.resolve("foo"));
    Files.write(workdir.resolve("third_party/java/one.java"), new byte[]{});
    Files.write(workdir.resolve("third_party/java/org/two.java"), new byte[]{});

    mover.transform(workdir, console);

    assertThatPath(workdir)
        .containsFiles("foo/one.java", "foo/org/two.java")
        .containsNoMoreFiles();
  }

  @Test
  public void testMoveFromWorkdirRootToSubdir() throws Exception {
    Move mover = skylark.eval("m",
        "m = core.move(before = '', after = 'third_party/java')\n");
    Files.write(workdir.resolve("file.java"), new byte[]{});
    mover.transform(workdir, console);

    assertThatPath(workdir)
        .containsFiles("third_party/java/file.java")
        .containsNoMoreFiles();
  }

  @Test
  public void testCannotMoveFromRootToAlreadyExistingDir() throws Exception {
    Move mover = skylark.eval("m",
        "m = core.move(before = '', after = 'third_party/java')\n");
    Files.createDirectories(workdir.resolve("third_party/java"));
    Files.write(workdir.resolve("third_party/java/bar.java"), new byte[]{});
    Files.write(workdir.resolve("third_party/java/foo.java"), new byte[]{});

    thrown.expect(ValidationException.class);
    thrown.expectMessage(
        "Files already exist in " + workdir + "/third_party/java: [bar.java, foo.java]");
    mover.transform(workdir, console);
  }

  @Test
  public void errorForMissingBefore() throws Exception {
    try {
      skylark.<Move>eval("m", "m = core.move(after = 'third_party/java')\n");
      Assert.fail();
    } catch (ConfigValidationException expected) {}

    console.assertThat()
        .onceInLog(MessageType.ERROR, ".*missing mandatory .* 'before'.*");
  }

  @Test
  public void errorForMissingAfter() throws Exception {
    try {
      skylark.<Move>eval("m", "m = core.move(before = 'third_party/java')\n");
      Assert.fail();
    } catch (ConfigValidationException expected) {}

    console.assertThat()
        .onceInLog(MessageType.ERROR, ".*missing mandatory .* 'after'.*");
  }
}
