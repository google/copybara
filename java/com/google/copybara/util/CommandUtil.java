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

package com.google.copybara.util;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.copybara.shell.BadExitStatusException;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import com.google.copybara.shell.CommandResult;
import com.google.copybara.shell.ShellUtils;
import com.google.copybara.shell.TerminationStatus;
import com.google.copybara.shell.TimeoutKillableObserver;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckReturnValue;

/**
 * An utility class for executing commands and logging the output appropriately.
 * TODO(danielromero): Rename to CommandRunner
 */
public final class CommandUtil {

  private static final Logger logger = Logger.getLogger(CommandUtil.class.getName());
  /**
   * No input for the command.
   */
  public static final byte[] NO_INPUT = new byte[]{};
  // TODO(malcon): Make this a flag
  // Kill the command after 15 minutes.
  private static final long COMMAND_TIMEOUT = TimeUnit.MINUTES.toMillis(15);

  private final Command cmd;
  private final boolean verbose;
  private final byte[] input;

  private CommandUtil(Command cmd, boolean verbose, byte[] input) {
    this.cmd = Preconditions.checkNotNull(cmd);
    this.verbose = verbose;
    this.input = Preconditions.checkNotNull(input);
  }

  public CommandUtil(Command cmd) {
    this(cmd, false, NO_INPUT);
  }

  /**
   * Sets the verbose level for the command execution.
   */
  @CheckReturnValue
  public CommandUtil withVerbose(boolean verbose) {
    return new CommandUtil(this.cmd, verbose, this.input);
  }

  /**
   * Sets the input for the command execution.
   */
  @CheckReturnValue
  public CommandUtil withInput(byte[] input) {
    return new CommandUtil(this.cmd, this.verbose, input);
  }

  /**
   * Executes a {@link Command} with the given input and writes to the console and the log depending
   * on the exit code of the command and the verbose flag.
   */
  public CommandOutputWithStatus execute() throws CommandException {
    Stopwatch stopwatch = Stopwatch.createStarted();
    String startMsg = "Executing ["
        + ShellUtils.prettyPrintArgv(Arrays.asList(cmd.getCommandLineElements())) + "]";
    logger.log(Level.INFO, startMsg);
    if (verbose) {
      System.err.println(startMsg);
    }
    ByteArrayOutputStream stdoutCollector = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrCollector = new ByteArrayOutputStream();

    OutputStream stdoutStream = commandOutputStream(verbose, stdoutCollector);
    OutputStream stderrStream = commandOutputStream(verbose, stderrCollector);
    TerminationStatus exitStatus = null;
    try {
      CommandResult cmdResult =
          cmd.execute(
              input,
              new TimeoutKillableObserver(COMMAND_TIMEOUT),
              stdoutStream, stderrStream, true);
      exitStatus = cmdResult.getTerminationStatus();
      return new CommandOutputWithStatus(
          cmdResult.getTerminationStatus(),
          stdoutCollector.toByteArray(),
          stderrCollector.toByteArray());
    } catch (BadExitStatusException e) {
      exitStatus = e.getResult().getTerminationStatus();
      throw new BadExitStatusWithOutputException(e.getCommand(), e.getResult(), e.getMessage(),
          stdoutCollector.toByteArray(),
          stderrCollector.toByteArray());
    } finally {
      String commandName = cmd.getCommandLineElements()[0];

      logOutput(Level.INFO, String.format("'%s' STDOUT: ", commandName), stdoutCollector);
      logOutput(Level.INFO, String.format("'%s' STDERR: ", commandName), stderrCollector);

      String finishMsg =
          String.format(
              "Command '%s' finished in %s. %s",
              commandName, stopwatch,
              exitStatus != null ? exitStatus.toString() : "(No exit status)");
      logger.log(Level.INFO, finishMsg);
      if (verbose) {
        System.err.println(finishMsg);
      }
    }
  }

  /**
   * Creates the necessary OutputStream to be passed to the {@link Command#execute()}.
   */
  private static OutputStream commandOutputStream(boolean verbose, OutputStream outputStream) {
    // If verbose we stream to the user console too
    return verbose ? new DemultiplexOutputStream(System.err, outputStream) : outputStream;
  }

  /**
   * Log to the appropiate log level the output of the command
   */
  private static void logOutput(Level level, String prefix, ByteArrayOutputStream outputBytes) {
    String string = asString(outputBytes).trim();
    if (string.isEmpty()) {
      return;
    }
    for (String line : string.split(System.lineSeparator())) {
      logger.log(level, prefix + line);
    }
  }

  private static String asString(ByteArrayOutputStream outputBytes) {
    return new String(outputBytes.toByteArray(), StandardCharsets.UTF_8);
  }

  /**
   * An {@link OutputStream} that can output to two {@code OutputStream}
   */
  private static class DemultiplexOutputStream extends OutputStream {

    private final OutputStream s1;
    private final OutputStream s2;

    private DemultiplexOutputStream(OutputStream s1, OutputStream s2) {
      this.s1 = s1;
      this.s2 = s2;
    }

    @Override
    public void write(int b) throws IOException {
      s1.write(b);
      s2.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      s1.write(b, off, len);
      s2.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
      s1.flush();
      s2.flush();
    }

    @Override
    public void close() throws IOException {
      IOException ex = null;
      try {
        s1.close();
      } catch (IOException e) {
        ex = e;
      }
      // We favor the second exception over the first one on close. But this is fine for now.
      s2.close();
      if (ex != null) {
        throw ex;
      }
    }
  }
}
