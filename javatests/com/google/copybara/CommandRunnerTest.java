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
import static java.nio.charset.StandardCharsets.UTF_8;
import static junit.framework.TestCase.fail;

import com.beust.jcommander.internal.Lists;
import com.google.common.base.Strings;
import com.google.copybara.util.CommandOutputWithStatus;
import com.google.copybara.util.CommandRunner;
import com.google.copybara.util.CommandTimeoutException;
import com.google.copybara.shell.AbnormalTerminationException;
import com.google.copybara.shell.BadExitStatusException;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import com.google.copybara.shell.Killable;
import com.google.copybara.shell.KillableObserver;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that we can execute commands with Bazel shell library.
 */
@RunWith(JUnit4.class)
public class CommandRunnerTest {

  private static final int LINES_SIZE = 1000;

  private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
  private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
  private final List<String> logLines = Lists.newLinkedList();

  @Test
  public void testCommand() throws Exception {
    Command command = new Command(new String[]{"echo", "hello", "world"});
    CommandOutputWithStatus result = runCommand(new CommandRunner(command));
    assertThat(result.getTerminationStatus().success()).isTrue();
    assertThat(result.getStdout()).isEqualTo("hello world\n");
    assertThat(outContent.toByteArray()).isEmpty();
    assertThat(errContent.toByteArray()).isEmpty();
    assertLogContains(
        "Executing [echo hello world]", "'echo' STDOUT: hello world", "Command 'echo' finished");
  }

  @Test
  public void testTimeout() throws Exception {
    Command command = bashCommand(""
        + "echo stdout msg\n"
        + ">&2 echo stderr msg\n"
        + "sleep 10\n");
    try {
      runCommand(new CommandRunner(command, Duration.ofSeconds(1)));
      fail();
    } catch (CommandTimeoutException e) {
      assertThat(e.getOutput().getStdout()).contains("stdout msg");
      assertThat(e.getOutput().getStderr()).contains("stderr msg");
      assertThat(e.getMessage())
          .containsMatch("Command '.*' killed by Copybara after timeout \\(1s\\)");
      assertThat(e.getTimeout()).isEquivalentAccordingToCompareTo(Duration.ofSeconds(1));
    }
  }

  @Test
  public void testObserverCanTerminate() throws Exception {
    KillableObserver tester = new KillableObserver() {
      @Override
      public void startObserving(Killable killable) {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException ignored) {
          // ignored
        }
        killable.kill();
      }

      @Override
      public void stopObserving(Killable killable) {
      }
    };
    Command command = bashCommand(""
        + "echo stdout msg\n"
        + ">&2 echo stderr msg\n"
        + "sleep 10\n");
    try {
      runCommand(new CommandRunner(command, Duration.ofSeconds(90)).withObserver(tester));
      fail();
    } catch (AbnormalTerminationException e) {
      assertThat(e.getMessage()).containsMatch("Process terminated by signal 15");
      assertThat(e.getMessage())
          .doesNotContainMatch("Command '.*' killed by Copybara after timeout \\(1s\\)");
    }
  }

  @Test
  public void testTimeoutCustomExitCode() throws Exception {
    Command command = bashCommand(""
        + "trap 'exit 42' SIGTERM SIGINT\n"
        + "echo stdout msg\n"
        + ">&2 echo stderr msg\n"
        + "sleep 10\n");
    try {
      runCommand(new CommandRunner(command, Duration.ofSeconds(1)));
      fail();
    } catch (CommandTimeoutException e) {
      assertThat(e.getTimeout()).isEquivalentAccordingToCompareTo(Duration.ofSeconds(1));
      assertThat(e.getOutput().getStderr()).contains("stderr msg");
      assertThat(e.getMessage())
          .containsMatch("Command '.*' killed by Copybara after timeout \\(1s\\)");
      assertThat(e.getOutput().getStdout()).contains("stdout msg");
    }
  }

  private Command bashCommand(String bashScript) throws IOException {
    Path tempFile = Files.createTempFile("test", "file");
    Files.write(tempFile, bashScript.getBytes(UTF_8));
    Files.setPosixFilePermissions(tempFile, PosixFilePermissions.fromString("rwxr-xr--"));
    return new Command(new String[]{tempFile.toAbsolutePath().toString()});
  }

  @Test
  public void testCommandWithVerbose() throws Exception {
    Command command = new Command(new String[]{"echo", "hello", "world"});
    CommandOutputWithStatus result = runCommand(new CommandRunner(command).withVerbose(true));
    assertThat(result.getTerminationStatus().success()).isTrue();
    assertThat(result.getStdout()).isEqualTo("hello world\n");
    assertThat(outContent.toByteArray()).isEmpty();
    String stderr = new String(errContent.toByteArray(), UTF_8);
    // Using contains() because stderr also gets all the logs
    assertThat(stderr).contains("hello world\n");
    // Verify that logging is redirected, even with verbose
    assertLogContains(
        "Executing [echo hello world]", "'echo' STDOUT: hello world", "Command 'echo' finished");
  }

  @Test
  public void testMaxCommandLength() throws Exception {
    String[] args = new String[10000];
    args[0] = "echo";
    for (int i = 1; i < 10000; i++) {
      args[i] = "hello world!";
    }
    Command command = new Command(args);
    CommandOutputWithStatus result = runCommand(new CommandRunner(command).withVerbose(true));
    assertThat(result.getTerminationStatus().success()).isTrue();
    assertLogContains("...", "'echo' STDOUT: hello world!", "Command 'echo' finished" );
  }

  @Test
  public void testCommandWithMaxLogLines() throws Exception {
    Command command = new Command(new String[]{"echo", "hello\n", "world"});
    CommandOutputWithStatus result =
        runCommand(new CommandRunner(command).withMaxStdOutLogLines(1));
    assertThat(result.getTerminationStatus().success()).isTrue();
    assertThat(result.getStdout()).isEqualTo("hello\n world\n");
    assertThat(outContent.toByteArray()).isEmpty();
    assertLogContains("Executing [echo 'hello\n' world]",
        "'echo' STDOUT: hello",
        "'echo' STDOUT: ... truncated after 1 line(s)");
  }

  @Test
  public void testCommandWithVerboseLargeOutput() throws Exception {
    Path largeFile = Files.createTempDirectory("test").resolve("large_file.txt");
    String singleLine = Strings.repeat("A", 100);
    try (BufferedWriter writer = Files.newBufferedWriter(largeFile)) {
      for (int i = 0; i < LINES_SIZE; i++) {
        writer.write(singleLine + "\n");
      }
    }
    Command command = new Command(new String[]{"cat", largeFile.toAbsolutePath().toString()});
    CommandOutputWithStatus result = runCommand(new CommandRunner(command).withVerbose(true));
    assertThat(result.getTerminationStatus().success()).isTrue();

    for (int i = 1; i < LINES_SIZE - 1; i++) {
      assertThat(logLines.get(i)).endsWith(singleLine);
    }
  }


  private CommandOutputWithStatus runCommand(CommandRunner commandRunner) throws CommandException {
    Logger logger = Logger.getLogger(CommandRunner.class.getName());
    StreamHandler handler = new StreamHandler() {
      @Override
      public synchronized void publish(LogRecord record) {
        logLines.add(record.getMessage());
      }
    };
    logger.addHandler(handler);
    PrintStream outRestore = System.out;
    PrintStream errRestore = System.err;
    System.setOut(new PrintStream(outContent));
    System.setErr(new PrintStream(errContent));
    try {
      return commandRunner.execute();
    } finally {
      System.setOut(outRestore);
      System.setErr(errRestore);
      logger.removeHandler(handler);
    }
  }

  private void assertLogContains(String... messages) {
    int i = 0;
    for (String message : messages) {
      assertThat(logLines.get(i)).contains(message);
      i++;
    }
  }
}
