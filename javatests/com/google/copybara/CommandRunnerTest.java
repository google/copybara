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

package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import com.google.copybara.util.CommandOutputWithStatus;
import com.google.copybara.util.CommandRunner;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that we can execute commands with Bazel shell library.
 */
@RunWith(JUnit4.class)
public class CommandRunnerTest {

  private ByteArrayOutputStream outContent = new ByteArrayOutputStream();
  private ByteArrayOutputStream errContent = new ByteArrayOutputStream();

  @Before
  public void setUpStreams() {
    System.setOut(new PrintStream(outContent));
    System.setErr(new PrintStream(errContent));
  }

  @After
  public void restoreStreams() {
    System.setOut(System.out);
    System.setErr(System.err);
  }

  @Test
  public void testCommand() throws CommandException {
    Command command = new Command(new String[]{"echo", "hello", "world"});
    CommandOutputWithStatus result = new CommandRunner(command).execute();
    assertThat(result.getTerminationStatus().success()).isTrue();
    assertThat(result.getStdout()).isEqualTo("hello world\n");
    assertThat(outContent.toByteArray()).isEmpty();
    assertThat(errContent.toByteArray()).isEmpty();
  }

  @Test
  public void testCommandWithVerbose() throws CommandException {
    Command command = new Command(new String[]{"echo", "hello", "world"});
    CommandOutputWithStatus result = new CommandRunner(command)
        .withVerbose(true)
        .execute();
    assertThat(result.getTerminationStatus().success()).isTrue();
    assertThat(result.getStdout()).isEqualTo("hello world\n");
    assertThat(outContent.toByteArray()).isEmpty();
    String stderr = new String(errContent.toByteArray(), StandardCharsets.UTF_8);
    // Using contains() because stderr also gets all the logs
    assertThat(stderr).contains("hello world\n");
  }
}
