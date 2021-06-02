/*
 * Copyright (C) 2018 Google Inc.
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
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.copybara.buildozer.testing.BuildozerTesting;
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
public class BuildozerBatchTest {

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
    checkoutDir = Files.createTempDirectory("BuildozerBatchTest-" + name.getMethodName());
    skylark = new SkylarkTestExecutor(options);
  }

  private TransformationStatus transform(Transformation transformation) throws Exception {
    return transformation.transform(TransformWorks.of(checkoutDir, "test msg", console));
  }

  @Test
  public void testCanBatch() throws Exception {
    BuildozerCreate create1 = skylark.eval("c", "c = "
        + "buildozer.create("
        + "    target = 'copy/bar:baz',"
        + "    rule_type = 'proto_library'"
        + ")");
    BuildozerCreate create2 = skylark.eval("c", "c = "
        + "buildozer.create("
        + "    target = 'bara/bar:baz',"
        + "    rule_type = 'proto_library'"
        + ")");

    transform(create1.join(create2));
    assertThatPath(checkoutDir).containsFiles("bara/bar/BUILD", "copy/bar/BUILD");
  }

  @Test
  public void testBatchTargetNotFoundIsError() throws Exception {
    BuildozerCreate create1 = skylark.eval("c", "c = "
        + "buildozer.create("
        + "    target = 'copy/bar:baz',"
        + "    rule_type = 'proto_library'"
        + ")");
    BuildozerModify notFound = skylark.eval("c", "c = "
            + "     buildozer.modify(\n"
            + "       target = ['foo/bar:idontexist'],\n"
            + "       commands = [ buildozer.cmd('set config \"test\"')],\n"
            + "     )");
    Files.createDirectories(checkoutDir.resolve("foo/bar"));
    Files.write(checkoutDir.resolve("foo/bar/BUILD"), "".getBytes(UTF_8));
    TransformationStatus status = transform(create1.join(notFound));
    assertThat(status.isNoop()).isTrue();
    assertThat(status.getMessage()).contains("foo/bar:idontexist");
    assertThatPath(checkoutDir).containsFiles("copy/bar/BUILD");
  }

  @Test
  public void testBatchTargetNotFoundIsNoop() throws Exception {
    options.workflowOptions.ignoreNoop = true;
    BuildozerCreate create = skylark.eval("c", "c = "
        + "buildozer.create("
        + "    target = 'copy/bar:baz',"
        + "    rule_type = 'proto_library'"
        + ")");
    BuildozerModify notFound = skylark.eval("c", "c = "
        + "     buildozer.modify(\n"
        + "       target = ['foo/bar:idontexist'],\n"
        + "       commands = [ buildozer.cmd('set config \"test\"')],\n"
        + "     )");
    Files.createDirectories(checkoutDir.resolve("foo/bar"));
    Files.write(checkoutDir.resolve("foo/bar/BUILD"), "".getBytes(UTF_8));
    transform(create.join(notFound));
    assertThatPath(checkoutDir).containsFiles("copy/bar/BUILD");
  }

  @Test
  public void testBatchFileNotFoundIsNotNoop() throws Exception {
    options.workflowOptions.ignoreNoop = true;
    BuildozerCreate create = skylark.eval("c", "c = "
        + "buildozer.create("
        + "    target = 'copy/bar:baz',"
        + "    rule_type = 'proto_library'"
        + ")");
    BuildozerModify notFound = skylark.eval("c", "c = "
        + "     buildozer.modify(\n"
        + "       target = ['foo/bar:idontexist'],\n"
        + "       commands = [ buildozer.cmd('set config \"test\"')],\n"
        + "     )");
    ValidationException thrown =
        assertThrows(ValidationException.class, () -> transform(create.join(notFound)));
    assertThat(thrown).hasMessageThat().contains("foo/bar:idontexist");
  }

  @Test
  public void testBatchMixedExceptionsGetThrown() throws Exception {
    options.workflowOptions.ignoreNoop = true;
    BuildozerCreate create = skylark.eval("c", "c = "
        + "buildozer.create("
        + "    target = 'copy/bar:baz',"
        + "    rule_type = 'proto_library'"
        + ")");
    BuildozerModify targetNotFoundButIgnored = skylark.eval("c", "c = "
        + "     buildozer.modify(\n"
        + "       target = ['foo/bar:idontexist'],\n"
        + "       commands = [ buildozer.cmd('set config \"test\"')],\n"
        + "     )");
    BuildozerModify fileNotFound = skylark.eval("c", "c = "
        + "     buildozer.modify(\n"
        + "       target = ['nosuch:file'],\n"
        + "       commands = [ buildozer.cmd('set config \"test\"')],\n"
        + "     )");
    Files.createDirectories(checkoutDir.resolve("foo/bar"));
    Files.write(checkoutDir.resolve("foo/bar/BUILD"), "".getBytes(UTF_8));
    // this is fine
    transform(create.join(targetNotFoundButIgnored));
    // this is not
    ValidationException thrown =
        assertThrows(ValidationException.class,
            () -> transform(create.join(targetNotFoundButIgnored).join(fileNotFound)));
    assertThat(thrown).hasMessageThat().contains("nosuch:file");
  }
}
