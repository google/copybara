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

import com.google.common.jimfs.Jimfs;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
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
public class SkylarkTransformationTest {

  private OptionsBuilder options;
  private Path checkoutDir;
  private TestingConsole console;
  private SkylarkTestExecutor skylark;
  private TransformWork transformWork;

  @Before
  public void setup() throws IOException {
    FileSystem fs = Jimfs.newFileSystem();
    checkoutDir = fs.getPath("/test-checkoutDir");
    Files.createDirectories(checkoutDir);
    console = new TestingConsole();
    options = new OptionsBuilder().setConsole(console);
    skylark = new SkylarkTestExecutor(options);
    transformWork = TransformWorks.of(checkoutDir, "testmsg", console);
  }

  @Test
  public void testStarlarkTransform_returnsSuccess() throws Exception {
    Transformation t =
        skylark.eval(
            "t",
            ""
                + "def foo(ctx):\n"
                + "  return ctx.success()\n"
                + "\n"
                + "t = core.dynamic_transform(foo)");

    TransformationStatus status = t.transform(transformWork);

    assertThat(status.isSuccess()).isTrue();
  }

  @Test
  public void testStarlarkTransform_returnsNoop() throws Exception {
    Transformation t =
        skylark.eval(
            "t",
            ""
                + "def foo(ctx):\n"
                + "  return ctx.noop('Reason for noop.')\n"
                + "\n"
                + "t = core.dynamic_transform(foo)");

    TransformationStatus status = t.transform(transformWork);

    assertThat(status.isNoop()).isTrue();
    assertThat(status.getMessage()).isEqualTo("Reason for noop.");
  }

  @Test
  public void testStarlarkTransform_noReturnValue_isTreatedAsSuccess() throws Exception {
    Transformation t =
        skylark.eval(
            "t",
            ""
                + "def foo(ctx):\n"
                + "  # Do nothing \n"
                + "  pass\n"
                + "\n"
                + "t = core.dynamic_transform(foo)");

    TransformationStatus status = t.transform(transformWork);

    assertThat(status.isSuccess()).isTrue();
  }

  @Test
  public void testStarlarkTransform_returnValueCanBeCasedOn() throws Exception {
    Transformation t =
        skylark.eval(
            "t",
            ""
                + "def foo(ctx):\n"
                + "  return ctx.success()\n"
                + "\n"
                + "s = core.dynamic_transform(foo)"
                + "\n"
                + "def bar(ctx):\n"
                + "  status = ctx.run(s)\n"
                + "  if not status.is_success:\n"
                + "    core.fail_with_noop()\n"
                + "  if status.is_noop:\n"
                + "    core.fail_with_noop()\n"
                + "  return status"
                + "\n"
                + "t = core.dynamic_transform(foo)");

    TransformationStatus status = t.transform(transformWork);

    assertThat(status.isSuccess()).isTrue();
  }

  @Test
  public void testStarlarkTransform_convertToString() throws Exception {
    Transformation t =
        skylark.eval(
            "t",
            ""
                + "def _foo_impl(ctx):\n"
                + "  pass\n"
                + "\n"
                + "t = core.dynamic_transform(_foo_impl, {'a': 1})");

    assertThat(t.describe()).isEqualTo("_foo_impl");
    assertThat(t.toString()).isEqualTo("Foo{a=1}");
  }
}
