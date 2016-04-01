package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.build.lib.shell.Command;
import com.google.devtools.build.lib.shell.CommandException;
import com.google.devtools.build.lib.shell.CommandResult;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.charset.StandardCharsets;

/**
 * Tests that we can execute commands with Bazel shell library.
 */
@RunWith(JUnit4.class)
public class CommandTest {

  @Test
  public void testCommand() throws CommandException {
    Command command = new Command(new String[]{"echo", "hello", "world"});
    CommandResult result = command.execute();
    assertThat(result.getTerminationStatus().success()).isTrue();
    assertThat(new String(result.getStdout(), StandardCharsets.UTF_8)).isEqualTo("hello world\n");
  }
}
