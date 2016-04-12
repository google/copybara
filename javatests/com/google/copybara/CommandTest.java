package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.util.CommandOutput;
import com.google.copybara.util.CommandUtil;
import com.google.devtools.build.lib.shell.Command;
import com.google.devtools.build.lib.shell.CommandException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that we can execute commands with Bazel shell library.
 */
@RunWith(JUnit4.class)
public class CommandTest {

  @Test
  public void testCommand() throws CommandException {
    Command command = new Command(new String[]{"echo", "hello", "world"});
    CommandOutput result = CommandUtil.executeCommand(command, /*verbose=*/false);
    assertThat(result.getTerminationStatus().success()).isTrue();
    assertThat(result.getStdoutAsString()).isEqualTo("hello world\n");
  }
}
