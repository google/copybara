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
import static org.junit.Assert.assertThrows;

import com.google.common.base.Joiner;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
import com.google.copybara.exception.NonReversibleValidationException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RenameTest {

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

  @Test
  public void testSimple() throws Exception {
    write("a/one.before", "1");
    write("a/FOOone.before", "1a");
    write("b/one.before", "2");
    write("d/e/one.before", "3");
    write("one.before", "4");

    Transformation t = run(
        "before = 'one.before'",
        "after = 'after'");

    assertThatPath(checkoutDir)
        .containsFile("a/after", "1")
        .containsFile("a/FOOone.before", "1a")
        .containsFile("b/after", "2")
        .containsFile("d/e/after", "3")
        .containsFile("after", "4")
        .containsNoMoreFiles();

    var unused = run(t.reverse());

    assertThatPath(checkoutDir)
        .containsFile("a/one.before", "1")
        .containsFile("a/FOOone.before", "1a")
        .containsFile("b/one.before", "2")
        .containsFile("d/e/one.before", "3")
        .containsFile("one.before", "4")
        .containsNoMoreFiles();
  }

  @Test
  public void testSuffix() throws Exception {
    write("one.before", "1");
    write("a/one.before", "2");
    write("a/FOOone.before", "3");

    Transformation t = run(
        "before = 'one.before'",
        "after = 'after'",
        "suffix = True");

    assertThatPath(checkoutDir)
        .containsFile("after", "1")
        .containsFile("a/after", "2")
        .containsFile("a/FOOafter", "3")
        .containsNoMoreFiles();

    var unused = run(t.reverse());

    assertThatPath(checkoutDir)
        .containsFile("one.before", "1")
        .containsFile("a/one.before", "2")
        .containsFile("a/FOOone.before", "3")
        .containsNoMoreFiles();
  }

  @Test
  public void testOverwrite_false() throws Exception {
    write("before", "1");
    write("after", "2");

    assertThrows(FileAlreadyExistsException.class,
        () -> run("before = 'before'", "after = 'after'"));
  }

  @Test
  public void testNoop() throws Exception {
    Transformation t = createTransform("before = 'before'", "after = 'after'");
    assertThat(run(t).isNoop()).isTrue();
  }

  @Test
  public void testOverwrite_true() throws Exception {
    write("before", "1");
    write("after", "2");

    Transformation t = run(
        "before = 'before'",
        "after = 'after'",
        "overwrite = True");

    assertThatPath(checkoutDir)
        .containsFile("after", "1")
        .containsNoMoreFiles();

    assertThrows(NonReversibleValidationException.class, t::reverse);
  }

  private Transformation run(String... fields) throws Exception {
    Transformation t = createTransform(fields);
    var unused = run(t);
    return t;
  }

  private Transformation createTransform(String... fields) throws ValidationException {
    return skylark.eval("m",
        "m = core.rename(" + Joiner.on(", ").join(fields) + ")");
  }

  private void write(String strPath, String content) throws IOException {
    Path path = checkoutDir.resolve(strPath);
    Files.createDirectories(path.getParent());
    Files.write(path, content.getBytes(StandardCharsets.UTF_8));
  }

  private TransformationStatus run(Transformation t) throws Exception {
    return t.transform(TransformWorks.of(checkoutDir, "testmsg", console));
  }

}
