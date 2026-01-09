/*
 * Copyright (C) 2023 Google LLC
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

package com.google.copybara.buildozer;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.copybara.Transformation;
import com.google.copybara.buildozer.testing.BuildozerTesting;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BuildozerPrintExecutorTest {

  public static final String BUILD_FILE =
      """
      cc_library(
          name = "bar_library",
          srcs = [
              "foo.cc",
          ],
          deps = [
              "//base",
          ],
      )\
      """;
  private OptionsBuilder options;
  private Path checkoutDir;
  private TestingConsole console;
  private SkylarkTestExecutor skylark;
  @Rule public TestName name = new TestName();

  @Before
  public void setup() throws IOException {
    console = new TestingConsole();
    options = new OptionsBuilder();
    options.setConsole(console);
    BuildozerTesting.enable(options);
    checkoutDir = Files.createTempDirectory("BuildozerPrintTest-" + name.getMethodName());
    skylark = new SkylarkTestExecutor(options);
    Files.createDirectories(checkoutDir.resolve("foo/bar"));
    Files.write(checkoutDir.resolve("foo/bar/BUILD"), BUILD_FILE.getBytes(UTF_8));
  }

  @Test
  public void testSimplePrint() throws Exception {
    Transformation t =
        skylark.eval(
            "t",
            "def _print(ctx):\n"
                + "  outfile = ctx.new_path(\"outfile\")\n"
                + "  ctx.write_path(outfile, buildozer.print(ctx, \"srcs\","
                + " \"foo/bar:bar_library\"))\n"
                + "\n"
                + "\n"
                + "t = core.dynamic_transform(impl = _print)");
    transform(t);
    assertThat(Files.readString(checkoutDir.resolve("outfile")).trim()).isEqualTo("[foo.cc]");
  }

  @Test
  public void testEntireRulePrint() throws Exception {
    Transformation t =
        skylark.eval(
            "t",
            "def _print(ctx):\n"
                + "  outfile = ctx.new_path(\"outfile\")\n"
                + "  ctx.write_path(outfile, buildozer.print(ctx, \"rule\","
                + " \"foo/bar:bar_library\"))\n"
                + "\n"
                + "\n"
                + "t = core.dynamic_transform(impl = _print)");
    transform(t);
    assertThat(Files.readString(checkoutDir.resolve("outfile")).trim())
        .isEqualTo(
            """
            cc_library(
                name = "bar_library",
                srcs = [
                    "foo.cc",
                ],
                deps = [
                    "//base",
                ],
            )\
            """);
  }

  @Test
  public void testBuildozerPrintFail() throws Exception {
    Transformation t =
        skylark.eval(
            "t",
            "def _print(ctx):\n"
                + "  outfile = ctx.new_path(\"outfile\")\n"
                + "  ctx.write_path(outfile, buildozer.print(ctx, \"rule\","
                + " \"does/not/exist:bar_library\"))\n"
                + "\n"
                + "\n"
                + "t = core.dynamic_transform(impl = _print)");
    ValidationException e = assertThrows(ValidationException.class, () -> transform(t));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "Error in print: Failed to execute buildozer with args:\n"
                + "  print rule|does/not/exist:bar_library");
  }

  private void transform(Transformation transformation) throws Exception {
    transformation.transform(TransformWorks.of(checkoutDir, "test msg", console));
  }
}
