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

import com.google.common.jimfs.Jimfs;
import com.google.copybara.Transformation;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformWorks;
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
public class RemoveTest {

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
  public void testBasicRemoval() throws Exception {
    ExplicitReversal t = skylark.eval("m", ""
        + "m = core.transform("
        + "    [core.copy('foo', 'bar'), core.copy('foo_dir', 'bar_dir')],"
        + "    reversal = [core.remove(glob(['bar', 'bar_dir/**']))],"
        + ")");
    touch("foo");
    touch("foo_dir/foo");
    transform(t);

    assertThatPath(checkoutDir)
        .containsFiles("foo")
        .containsFiles("foo_dir/foo")
        .containsFiles("bar")
        .containsFiles("bar_dir/foo")
        .containsNoMoreFiles();

    transform(t.reverse());

    assertThatPath(checkoutDir)
        .containsFiles("foo")
        .containsFiles("foo_dir/foo")
        .containsNoMoreFiles();
  }

  @Test
  public void testRemoveWithGlob() throws Exception {
    ExplicitReversal t = skylark.eval("m", ""
        + "m = core.transform("
        + "    [core.copy('foo', 'bar', paths = glob(['**.java']))],"
        + "    reversal = [core.remove(glob(['bar/**.java']))],"
        + ")");
    touch("foo/foo.c");
    touch("foo/foo.java");
    touch("bar/bar.c");
    transform(t);

    assertThatPath(checkoutDir)
        .containsFiles("foo/foo.c")
        .containsFiles("foo/foo.java")
        .containsFiles("bar/foo.java")
        .containsFiles("bar/bar.c")
        .containsNoMoreFiles();

    transform(t.reverse());

    assertThatPath(checkoutDir)
        .containsFiles("foo/foo.c")
        .containsFiles("foo/foo.java")
        .containsFiles("bar/bar.c")
        .containsNoMoreFiles();
  }

  @Test
  public void testBareRemoveNotAllowed() throws Exception {
    Remove t = skylark.eval("m", "m = core.remove(glob(['bar']))");
    ValidationException thrown = assertThrows(ValidationException.class, () -> transform(t));
    assertThat(thrown)
        .hasMessageThat()
        .contains("core.remove() is only mean to be used inside core.transform");
  }

  private void transform(Transformation t) throws Exception {
    t.transform(TransformWorks.of(checkoutDir, "testmsg", console));
  }

  private void touch(String strPath) throws IOException {
    Path path = checkoutDir.resolve(strPath);
    Files.createDirectories(path.getParent());
    Files.write(path, new byte[]{});
  }
}
