/*
 * Copyright (C) 2019 Google Inc.
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

package com.google.copybara.transform.debug;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.copybara.Workflow;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.RecordsProcessCallDestination;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

@RunWith(JUnit4.class)
public class TransformDebugTest {

  private OptionsBuilder options;
  private SkylarkTestExecutor skylark;
  private Path workdir;
  private DummyOrigin origin;
  private RecordsProcessCallDestination destination;
  private Console console;

  @Before
  public void setup() throws Exception {
    options = new OptionsBuilder();
    origin = new DummyOrigin();
    destination = new RecordsProcessCallDestination();
    options.testingOptions.origin = origin;
    options.testingOptions.destination = destination;
    skylark = new SkylarkTestExecutor(options);
    workdir = Files.createTempDirectory("workdir");
    options.setHomeDir(Files.createTempDirectory("home").toString());
    options.setWorkdirToRealTempDir();
    console = Mockito.mock(Console.class);
    when(console.colorize(any(), anyString())).thenAnswer(
        (Answer<String>) invocationOnMock ->
            (String) invocationOnMock.getArguments()[1]
    );
  }

  @Test
  public void testDiffFileMatch() throws Exception {
    options.debug.debugFileBreak = "test1.txt";
    mockAnswer("Replace foo1", "d", "c");
    runWorkflow();
    // One diff is shown when stopped, then one is shown when d is pressed
    verify(console, times(2)).info(eq(""
        + "\n"
        + "diff --git a/before/test1.txt b/after/test1.txt\n"
        + "index 1715acd..05c4fe6 100644\n"
        + "--- a/before/test1.txt\n"
        + "+++ b/after/test1.txt\n"
        + "@@ -1 +1 @@\n"
        + "-foo1\n"
        + "+bar1\n"));
  }

  @Test
  public void testFileGlobMatch() throws Exception {
    options.debug.debugFileBreak = "test*.txt";
    mockAnswer("Replace foo1", "c");
    mockAnswer("Replace foo2", "c");
    runWorkflow();
    verify(console).ask(matches("Debugger stopped after 'Replace foo1'(.|\n)*"), anyString(),any());
    verify(console).ask(matches("Debugger stopped after 'Replace foo2'(.|\n)*"), anyString(),any());
  }

  @Test
  public void testTransformMatch() throws Exception {
    options.debug.debugTransformBreak = "Replace foo1";
    mockAnswer("Replace foo1", "c");
    runWorkflow();
    verify(console).ask(matches("Debugger stopped after 'Replace foo1'(.|\n)*"), anyString(),any());
  }

  @Test
  public void testMetadataMatch() throws Exception {
    options.debug.debugMetadataBreak = true;
    mockAnswer("Adding header to the message", "c");
    runWorkflow();
    verify(console).ask(
        matches("Debugger stopped after 'Adding header to the message(.|\n)*"), anyString(),any());
  }

  @Test
  public void testStop() throws Exception {
    options.debug.debugFileBreak = "test1.txt";
    mockAnswer("Replace foo1", "s");
    ValidationException e = assertThrows(ValidationException.class, () -> runWorkflow());
    assertThat(e).hasMessageThat().contains("Stopped by user");
  }

  private void mockAnswer(String description,
      String answer, String... answers) throws IOException {
    when(
        console.ask(matches("Debugger stopped after '" + description + "'(.|\n)*"),
            anyString(), any()))
        .thenReturn(answer, answers);
  }

  private void runWorkflow() throws Exception {
    Workflow<?, ?> wf = (Workflow<?, ?>) skylark.loadConfig(""
        + "core.workflow(\n"
        + "    name = 'default',\n"
        + "    origin = testing.origin(),\n"
        + "    destination = testing.destination(),\n"
        + "    authoring = " + "authoring.overwrite('foo <foo@example.com>'),\n"
        + "    transformations = ["
        + "       core.replace('foo1', 'bar1', paths = glob(['test1.txt'])),"
        + "       metadata.add_header('AAAA'),"
        + "       core.replace('foo2', 'bar2', paths = glob(['test2.txt'])),"
        + "    ],\n"
        + ")")
        .getMigration("default");

    Files.write(workdir.resolve("test1.txt"), "foo1\n".getBytes(UTF_8));
    Files.write(workdir.resolve("test2.txt"), "foo2\n".getBytes(UTF_8));
    wf.getTransformation().transform(TransformWorks.of(workdir, "test", console));
  }
}
