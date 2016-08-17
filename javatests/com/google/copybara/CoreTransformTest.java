// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import static com.google.copybara.testing.FileSubjects.assertThatPath;

import com.google.common.jimfs.Jimfs;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
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

  @Before
  public void setup() throws IOException {
    FileSystem fs = Jimfs.newFileSystem();
    checkoutDir = fs.getPath("/test-checkoutDir");
    Files.createDirectories(checkoutDir);
    OptionsBuilder options = new OptionsBuilder();
    skylark = new SkylarkTestExecutor(options, Core.class);
    console = new TestingConsole();
    options.setConsole(console);
  }

  private void transform(Transformation transform) throws IOException, ValidationException {
    transform.transform(new TransformWork(checkoutDir, "testmsg"), console);
  }

  @Test
  public void errorForMissingReversalArgument() {
    skylark.evalFails("core.transform([core.move('foo', 'bar')])",
        "missing mandatory keyword arguments in call to transform");
  }

  @Test
  public void errorForMissingForwardArgument() {
    skylark.evalFails("core.transform(reversal = [core.move('foo', 'bar')])",
        "missing mandatory positional argument 'transformations' while calling transform");
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
        .onceInLog(TestingConsole.MessageType.PROGRESS, "\\[ *1/ *2\\] Transform Moving file1")
        .onceInLog(TestingConsole.MessageType.PROGRESS, "\\[ *2/ *2\\] Transform Moving file2");
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
  public void errorForNonTransformationElementInList() throws Exception {
    skylark.evalFails("core.transform([42], reversal = [core.move('foo', 'bar')])",
        "expected type transformation for 'transformations' .* type int instead");
    skylark.evalFails("core.transform([core.move('foo', 'bar')], reversal = [42])",
        "expected type transformation for 'reversal' .* type int instead");
  }
}
