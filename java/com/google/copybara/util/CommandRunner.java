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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.copybara.shell.AbnormalTerminationException;
import com.google.copybara.shell.BadExitStatusException;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import com.google.copybara.shell.Killable;
import com.google.copybara.shell.KillableObserver;
import com.google.copybara.shell.ShellUtils;
import com.google.copybara.shell.TerminationStatus;
import com.google.copybara.shell.TimeoutKillableObserver;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Allows running a {@link Command} with easier stderr/stdout and logging management.
 */
public final class CommandRunner {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * No input for the command.
   */
  public static final byte[] NO_INPUT = new byte[]{};
  // By default we kill the command after 15 minutes.
  public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(15);
  public static final int MAX_COMMAND_LENGTH = 40000;

  private final Command cmd;
  private final boolean verbose;
  private final byte[] input;
  private final int maxOutLogLines;
  private final Duration timeout;
  private final ImmutableList<KillableObserver> additionalObservers;
  private final Optional<OutputStream> asyncStdoutStream;
  private final Optional<OutputStream> asyncErrStream;
  private final Optional<CommandExecutor> executor;


  private CommandRunner(Command cmd, boolean verbose, byte[] input, int maxOutLogLines,
      Duration timeout,
      ImmutableList<KillableObserver> additionalObservers,
      Optional<OutputStream> stdoutStream,
      Optional<OutputStream> errStream,
      Optional<CommandExecutor> executor) {
    this.cmd = Preconditions.checkNotNull(cmd);
    this.verbose = verbose;
    this.input = Preconditions.checkNotNull(input);
    this.maxOutLogLines = maxOutLogLines;
    this.timeout = Preconditions.checkNotNull(timeout);
    this.additionalObservers = Preconditions.checkNotNull(additionalObservers);
    this.asyncStdoutStream = Preconditions.checkNotNull(stdoutStream);
    this.asyncErrStream = Preconditions.checkNotNull(errStream);
    this.executor = Preconditions.checkNotNull(executor);
  }

  public CommandRunner(Command cmd) {
    this(cmd, false, NO_INPUT, -1, DEFAULT_TIMEOUT, ImmutableList.of(),
        Optional.empty(), Optional.empty(), Optional.empty());
  }

  public CommandRunner(Command cmd, Duration timeout) {
    this(cmd, false, NO_INPUT, -1, timeout, ImmutableList.of(),
        Optional.empty(), Optional.empty(), Optional.empty());
  }

  /**
   * Sets the verbose level for the command execution.
   */
  @CheckReturnValue
  public CommandRunner withVerbose(boolean verbose) {
    return new CommandRunner(
        this.cmd, verbose, this.input, this.maxOutLogLines, timeout, additionalObservers,
        asyncStdoutStream, asyncErrStream, executor);
  }

  /**
   * Sets the input for the command execution.
   */
  @CheckReturnValue
  public CommandRunner withInput(byte[] input) {
    return new CommandRunner(
        this.cmd, this.verbose, input, this.maxOutLogLines, timeout, additionalObservers,
        asyncStdoutStream, asyncErrStream, executor);
  }

  /**
   * Sets the maximum number of output lines logged per stream
   */
  @CheckReturnValue
  public CommandRunner withMaxStdOutLogLines(int lines) {
    return new CommandRunner(
        this.cmd, this.verbose, this.input, lines, timeout, additionalObservers,
        asyncStdoutStream, asyncErrStream, executor);
  }

  /**
   * Adds another observer to the call executions
   */
  @CheckReturnValue
  public CommandRunner withObserver(KillableObserver observer) {
    return new CommandRunner(
        this.cmd, this.verbose, this.input, maxOutLogLines, timeout,
        ImmutableList.<KillableObserver>builder().addAll(additionalObservers).add(observer).build(),
        asyncStdoutStream, asyncErrStream, executor);
  }

  /**
   * Sets a stream to redirect stdOut output to
   */
  @CheckReturnValue
  public CommandRunner withStdOutStream(OutputStream stream) {
    return new CommandRunner(
        this.cmd, this.verbose, this.input, maxOutLogLines, timeout,
        additionalObservers,
        Optional.ofNullable(stream),
        asyncErrStream,
        executor);
  }

  /**
   * Sets a stream to redirect stdErr output to
   */
  @CheckReturnValue
  public CommandRunner withStdErrStream(OutputStream stream) {
    return new CommandRunner(
        this.cmd, this.verbose, this.input, maxOutLogLines, timeout,
        additionalObservers,
        asyncStdoutStream,
        Optional.ofNullable(stream),
        executor);
  }

  /**
   * Sets a stream to redirect stdErr output to
   */
  @CheckReturnValue
  public CommandRunner withCommandExecutor(CommandExecutor runner) {
    return new CommandRunner(
        this.cmd, this.verbose, this.input, maxOutLogLines, timeout,
        additionalObservers,
        asyncStdoutStream,
        asyncErrStream,
        Optional.of(runner));
  }

  /**
   * Executes a {@link Command} with the given input and writes to the console and the log depending
   * on the exit code of the command and the verbose flag.
   */
  public CommandOutputWithStatus execute() throws CommandException {
    Stopwatch stopwatch = Stopwatch.createStarted();
    String startMsg = ShellUtils.prettyPrintArgv(Arrays.asList(cmd.getCommandLineElements()));
    startMsg = startMsg.length() > MAX_COMMAND_LENGTH
        ? startMsg.substring(0, MAX_COMMAND_LENGTH) + "..." : startMsg;
    String validStartMsg = "Executing [" + startMsg + "]";
    logger.atInfo().log("%s", validStartMsg);
    if (verbose) {
      System.err.println(validStartMsg);
    }
    TerminationStatus exitStatus = null;
    CombinedKillableObserver cmdMonitor =
        new CombinedKillableObserver(timeout, additionalObservers.toArray(new KillableObserver[0]));
    ByteArrayOutputStream stdoutCollector = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrCollector = new ByteArrayOutputStream();
    try {
      if (asyncStdoutStream.isPresent()) {
        stdoutCollector.write("stdOut redirected to external observer.".getBytes(UTF_8));
      }
      if (asyncErrStream.isPresent()) {
        stderrCollector.write("stdErr redirected to external observer.".getBytes(UTF_8));
      }
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Error writing output.");
    }
    OutputStream stdoutStream = commandOutputStream(asyncStdoutStream.orElse(stdoutCollector));
    OutputStream stderrStream = commandOutputStream(asyncErrStream.orElse(stderrCollector));

    try {
      CommandExecutor runner = executor.orElse(new DefaultExecutor());
      TerminationStatus status =
          runner.getCommandOutputWithStatus(cmd, input, cmdMonitor, stdoutStream, stderrStream);
      exitStatus = status;
      return new CommandOutputWithStatus(
          status,
          stdoutCollector.toByteArray(),
          stderrCollector.toByteArray());
    } catch (BadExitStatusException e) {
      exitStatus = e.getResult().getTerminationStatus();
      maybeTreatTimeout(stdoutCollector, stderrCollector, cmdMonitor, e);
      throw new BadExitStatusWithOutputException(e.getCommand(), e.getResult(), e.getMessage(),
          stdoutCollector.toByteArray(),
          stderrCollector.toByteArray());
    } catch (AbnormalTerminationException e) {
      maybeTreatTimeout(stdoutCollector, stderrCollector, cmdMonitor, e);
      throw e;
    } finally {
      String commandName = cmd.getCommandLineElements()[0];

      if (maxOutLogLines != 0) {
        logOutput(
            Level.INFO,
            String.format("'%s' STDOUT: ", commandName),
            stdoutCollector,
            maxOutLogLines);
        logOutput(
            Level.INFO,
            String.format("'%s' STDERR: ", commandName),
            stderrCollector,
            maxOutLogLines);
      }

      String finishMsg;
      if (cmdMonitor.hasTimedOut()) {
        finishMsg = String.format(
            "Command '%s' was killed after timeout. Execution time %s. %s",
            commandName, formatDuration(stopwatch.elapsed()),
            exitStatus != null ? exitStatus.toString() : "(No exit status)");
        logger.atSevere().log("%s", finishMsg);
      } else {
        finishMsg = String.format(
            "Command '%s' finished in %s. %s",
            commandName, formatDuration(stopwatch.elapsed()),
            exitStatus != null ? exitStatus.toString() : "(No exit status)");
        logger.atInfo().log("%s", finishMsg);
      }
      if (verbose) {
        System.err.println(finishMsg);
      }
    }
  }

  /**
   * Format a duration to a human-readable string. This assumes that the duration is less than
   * 24 hours, which should always be true for a command (usually takes from a few ms to a few
   * minutes).
   */
  private String formatDuration(Duration duration) {
    return LocalTime.MIDNIGHT.plus(duration).format(DateTimeFormatter.ofPattern("mm:ss.SSS"));
  }

private static class DefaultExecutor implements CommandExecutor {

  @Override
  public TerminationStatus getCommandOutputWithStatus(Command cmd, byte[] input,
      KillableObserver cmdMonitor, OutputStream stdoutStream, OutputStream stderrStream)
      throws CommandException {
    return cmd.execute(input, cmdMonitor, stdoutStream, stderrStream, true)
        .getTerminationStatus();
  }
}

  private void maybeTreatTimeout(ByteArrayOutputStream stdoutCollector,
      ByteArrayOutputStream stderrCollector, CombinedKillableObserver cmdMonitor,
      AbnormalTerminationException e) throws CommandTimeoutException {
    if (!cmdMonitor.hasTimedOut()) {
      return;
    }
    String msg = String.format(
        "Command '%s' killed by Copybara after timeout (%ds)."
            + " If this fails during a fetch use --fetch-timeout flag.\n"
            + "Exit info: %s",
        cmd.getCommandLineElements()[0],
        timeout.getSeconds(),
        e.getResult().getTerminationStatus());
    throw new CommandTimeoutException(e.getCommand(), e.getResult(), msg,
        stdoutCollector.toByteArray(),
        stderrCollector.toByteArray(),
        timeout);
  }

  /**
   * Creates the necessary OutputStream to be passed to the {@link Command#execute()}.
   */
  private OutputStream commandOutputStream(OutputStream outputStream) {
    // If verbose we stream to the user console too
    return verbose ? new MultiplexOutputStream(System.err, outputStream) : outputStream;
  }

  /**
   * Log to the appropriate log level the output of the command
   */
  private static void logOutput(
      Level level, String prefix, ByteArrayOutputStream outputBytes, int maxLogLines) {
    String string = asString(outputBytes).trim();
    if (string.isEmpty()) {
      return;
    }
    int lines = 0;
    for (String line : Splitter.on(System.lineSeparator()).split(string)) {
      logger.at(level).log("%s%s", prefix, line);
      lines++;
      if (maxLogLines >= 0 && lines >= maxLogLines) {
        logger.at(level).log("%s... truncated after %d line(s)", prefix, maxLogLines);
        break;
      }
    }
  }

  private static String asString(ByteArrayOutputStream outputBytes) {
    return new String(outputBytes.toByteArray(), UTF_8);
  }

  /**
   * An {@link OutputStream} that can output to two {@code OutputStream}
   */
  private static class MultiplexOutputStream extends OutputStream {

    private final OutputStream s1;
    private final OutputStream s2;

    private MultiplexOutputStream(OutputStream s1, OutputStream s2) {
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

  /**
   * Multiplex KillableObserver to allow monitoring processes
   */
  private static class CombinedKillableObserver implements KillableObserver {

    private final TimeoutKillableObserver timed;
    private final ImmutableList<KillableObserver> others;

    private CombinedKillableObserver(Duration timeout, KillableObserver... others) {
      this.timed = new TimeoutKillableObserver(timeout);
      this.others = ImmutableList.copyOf(others);
    }

    @Override
    public void startObserving(Killable killable) {
      timed.startObserving(killable);
      for (KillableObserver other : others) {
        other.startObserving(killable);
      }
    }

    @Override
    public void stopObserving(Killable killable) {
      timed.stopObserving(killable);
      for (KillableObserver other : others) {
        other.stopObserving(killable);
      }
    }

    /**
     * Returns true if the observed process was killed by this observer.
     */
    public boolean hasTimedOut() {
      return timed.hasTimedOut();
    }
  }

  /**
   * Interface to insert custom command options not otherwise supported by the type.
   */
  public interface CommandExecutor {

    /**
     * Actual invocation of cmd.Execute. This hook is required as implementations of Command differ
     * between versions and implementations. Use with caution.
     *
     * @param cmd cmd to to be run
     * @param input input
     * @param cmdMonitor Observer for terminating job
     * @param stdoutStream Stream for cmd.execute
     * @param stderrStream Stream for cmd.execute
     */
    TerminationStatus getCommandOutputWithStatus(
        Command cmd,
        byte[] input,
        KillableObserver cmdMonitor,
        OutputStream stdoutStream,
        OutputStream stderrStream)
        throws CommandException;
  }
}
