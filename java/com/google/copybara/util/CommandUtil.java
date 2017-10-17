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

import com.google.common.base.Stopwatch;
import com.google.copybara.shell.BadExitStatusException;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import com.google.copybara.shell.CommandResult;
import com.google.copybara.shell.ShellUtils;
import com.google.copybara.shell.SimpleKillableObserver;
import com.google.copybara.shell.TerminationStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An utility class for executing commands and logging the output appropriately.
 */
public final class CommandUtil {

  private static final Logger logger = Logger.getLogger(CommandUtil.class.getName());
  /**
   * No input for the command.
   */
  public static final byte[] NO_INPUT = new byte[]{};

  private CommandUtil() {}

  /**
   * Executes a {@link Command} and writes to the console and the log depending on the exit code of
   * the command and the verbose flag.
   */
  public static CommandOutputWithStatus executeCommand(Command cmd, boolean verbose)
      throws CommandException {
    return executeCommand(cmd, NO_INPUT, verbose);
  }

  /**
   * Executes a {@link Command} with the given input and writes to the console and the log depending
   * on the exit code of the command and the verbose flag.
   */
  public static CommandOutputWithStatus executeCommand(
      Command cmd, byte[] input, boolean verbose) throws CommandException {
    Stopwatch stopwatch = Stopwatch.createStarted();
    String startMsg = "Executing ["
        + ShellUtils.prettyPrintArgv(Arrays.asList(cmd.getCommandLineElements())) + "]";
    logger.log(Level.INFO, startMsg);
    if (verbose) {
      System.err.println(startMsg);
    }
    ByteArrayOutputStream stdoutCollector = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrCollector = new ByteArrayOutputStream();

    CommandResult cmdResult;

    TerminationStatus exitStatus = null;
    try {
      cmdResult = cmd.execute(input, new SimpleKillableObserver(),
          // If verbose we stream to the user console too
          verbose ? new DemultiplexOutputStream(System.err, stdoutCollector) : stdoutCollector,
          verbose ? new DemultiplexOutputStream(System.err, stderrCollector) : stderrCollector,
          true);
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
      String finishMsg = "Command '" + cmd.getCommandLineElements()[0] + "' finished in "
          + stopwatch + ". " + (exitStatus != null ? exitStatus.toString() : "(No exit status)");

      logOutput(Level.INFO, cmd, "STDOUT", stdoutCollector);
      logOutput(Level.INFO, cmd, "STDERR", stderrCollector);
      logger.log(Level.INFO, finishMsg);

      if (verbose) {
        System.err.println(finishMsg);
      }
    }
  }

  /**
   * Log to the appropiate log level the output of the command
   */
  private static void logOutput(Level level, Command cmd, final String outputType,
      ByteArrayOutputStream outputBytes) {

    String string = asString(outputBytes).trim();
    if (string.isEmpty()) {
      return;
    }
    for (String line : string.split(System.lineSeparator())) {
      logger.log(level, "'" + cmd.getCommandLineElements()[0] + "' " + outputType + ": " + line);
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
