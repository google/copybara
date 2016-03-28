package com.google.copybara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.devtools.build.lib.shell.Command;
import com.google.devtools.build.lib.shell.CommandException;
import com.google.devtools.build.lib.shell.CommandResult;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

/**
 * Tests that we can execute commands with Bazel shell library.
 */
public class CommandTest {

  @Test
  public void testCommand() throws CommandException {
    Command command = new Command(new String[]{"echo", "hello", "world"});
    CommandResult result = command.execute();
    assertTrue(result.getTerminationStatus().success());
    assertEquals("hello world\n", new String(result.getStdout(), StandardCharsets.UTF_8));
  }
}
