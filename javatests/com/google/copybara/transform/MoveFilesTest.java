package com.google.copybara.transform;

import static com.google.copybara.testing.FileSubjects.assertThatPath;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.EnvironmentException;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.transform.MoveFiles.MoveElement;
import com.google.copybara.util.console.Console;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(JUnit4.class)
public class MoveFilesTest {

  private MoveFiles.Yaml yaml;
  private OptionsBuilder options;
  private Path workdir;
  private Console console;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() throws IOException {
    FileSystem fs = Jimfs.newFileSystem();
    workdir = fs.getPath("/test-workdir");
    Files.createDirectories(workdir);
    yaml = new MoveFiles.Yaml();
    options = new OptionsBuilder();
    console = options.general.console();
  }

  @Test
  public void testSimpleMove() throws ValidationException, EnvironmentException, IOException {
    yaml.setPaths(ImmutableList.of(
        createMove("one.before", "folder/one.after"),
        createMove("folder2/two.before", "two.after")));
    MoveFiles mover = yaml.withOptions(options.build());
    Files.write(workdir.resolve("one.before"), new byte[]{});
    Files.createDirectories(workdir.resolve("folder2"));
    Files.write(workdir.resolve("folder2/two.before"), new byte[]{});
    mover.transform(workdir, console);

    assertThatPath(workdir)
        .containsFiles("folder/one.after", "two.after")
        .containsNoMoreFiles();

    mover.reverse().transform(workdir, console);

    assertThatPath(workdir)
        .containsFiles("one.before", "folder2/two.before")
        .containsNoMoreFiles();
  }

  @Test
  public void testTransitiveMove() throws ValidationException, EnvironmentException, IOException {
    yaml.setPaths(ImmutableList.of(
        createMove("one", "two"),
        createMove("two", "three")));
    MoveFiles mover = yaml.withOptions(options.build());
    Files.write(workdir.resolve("one"), new byte[]{});
    mover.transform(workdir, console);

    assertThatPath(workdir).containsFiles("three").containsNoMoreFiles();

    mover.reverse().transform(workdir, console);

    assertThatPath(workdir).containsFiles("one").containsNoMoreFiles();
  }

  @Test
  public void testDoesntExist() throws ValidationException, EnvironmentException, IOException {
    yaml.setPaths(ImmutableList.of(
        createMove("blablabla", "other")));
    MoveFiles mover = yaml.withOptions(options.build());
    thrown.expect(ValidationException.class);
    thrown.expectMessage("Error moving 'blablabla'. It doesn't exist");
    mover.transform(workdir, console);
  }

  @Test
  public void testEmpty() throws ConfigValidationException, EnvironmentException, IOException {
    yaml.setPaths(ImmutableList.<MoveElement>of());
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("'paths' attribute is required");
    yaml.withOptions(options.build());
  }

  @Test
  public void testAbsolute() throws ConfigValidationException, EnvironmentException, IOException {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("'/blablabla' is not a relative path");
    yaml.setPaths(ImmutableList.of(createMove("/blablabla", "other")));
  }

  @Test
  public void testDotDot() throws ConfigValidationException, EnvironmentException, IOException {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("'../blablabla' is not a relative path");
    yaml.setPaths(ImmutableList.of(createMove("../blablabla", "other")));
  }

  @Test
  public void testDestinationExist() throws ValidationException, EnvironmentException, IOException {
    yaml.setPaths(ImmutableList.of(createMove("one", "two")));
    Files.write(workdir.resolve("one"), new byte[]{});
    Files.write(workdir.resolve("two"), new byte[]{});
    MoveFiles mover = yaml.withOptions(options.build());
    thrown.expect(ValidationException.class);
    thrown.expectMessage("Cannot move file to '/test-workdir/two' because it already exists");
    mover.transform(workdir, console);
  }

  @Test
  public void testDestinationExistDirectory() throws ValidationException, EnvironmentException,
      IOException {
    yaml.setPaths(ImmutableList.of(createMove("one", "folder/two")));
    Files.createDirectories(workdir.resolve("folder"));
    Files.write(workdir.resolve("one"), new byte[]{});
    MoveFiles mover = yaml.withOptions(options.build());
    mover.transform(workdir, console);

    assertThatPath(workdir)
        .containsFiles("folder/two")
        .containsNoMoreFiles();
  }

  @Test
  public void testMoveToWorkdirRoot() throws Exception {
    yaml.setPaths(ImmutableList.of(createMove("third_party/java", "")));
    Files.createDirectories(workdir.resolve("third_party/java/org"));
    Files.write(workdir.resolve("third_party/java/one.java"), new byte[]{});
    Files.write(workdir.resolve("third_party/java/org/two.java"), new byte[]{});
    MoveFiles mover = yaml.withOptions(options.build());
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
   * make MoveFiles non-reversible. As it would need to know if foo exist or not for computing the
   * reverse. This test ensures that MoveFiles doesn't work like that but instead puts the content
   * of java in foo, no matter that foo exist or not.
   */
  @Test
  public void testMoveDir() throws Exception {
    yaml.setPaths(ImmutableList.of(createMove("third_party/java", "foo")));
    Files.createDirectories(workdir.resolve("third_party/java/org"));
    Files.createDirectories(workdir.resolve("foo"));
    Files.write(workdir.resolve("third_party/java/one.java"), new byte[]{});
    Files.write(workdir.resolve("third_party/java/org/two.java"), new byte[]{});
    MoveFiles mover = yaml.withOptions(options.build());
    mover.transform(workdir, console);

    assertThatPath(workdir)
        .containsFiles("foo/one.java", "foo/org/two.java")
        .containsNoMoreFiles();
  }

  @Test
  public void testMoveFromWorkdirRootToSubdir() throws Exception {
    yaml.setPaths(ImmutableList.of(createMove("", "third_party/java")));
    Files.write(workdir.resolve("file.java"), new byte[]{});
    MoveFiles mover = yaml.withOptions(options.build());
    mover.transform(workdir, console);

    assertThatPath(workdir)
        .containsFiles("third_party/java/file.java")
        .containsNoMoreFiles();
  }

  @Test
  public void testCannotMoveFromRootToAlreadyExistingDir() throws Exception {
    yaml.setPaths(ImmutableList.of(createMove("", "third_party/java")));
    Files.createDirectories(workdir.resolve("third_party/java"));
    Files.write(workdir.resolve("third_party/java/bar.java"), new byte[]{});
    Files.write(workdir.resolve("third_party/java/foo.java"), new byte[]{});
    MoveFiles mover = yaml.withOptions(options.build());

    thrown.expect(ValidationException.class);
    thrown.expectMessage(
        "Files already exist in " + workdir + "/third_party/java: [bar.java, foo.java]");
    mover.transform(workdir, console);
  }

  private MoveElement createMove(String before, String after) throws ConfigValidationException {
    MoveElement result = new MoveElement();
    result.setBefore(before);
    result.setAfter(after);
    return result;
  }
}
