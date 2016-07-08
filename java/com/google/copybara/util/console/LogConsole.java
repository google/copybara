package com.google.copybara.util.console;

import com.google.common.base.Preconditions;

import java.io.InputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.annotation.Nullable;

/**
 * A simple console logger that prefixes the output with the time
 */
public class LogConsole implements Console {

  private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd HH:mm:ss.SSS");
  @Nullable
  private final InputStream input;
  private final PrintStream output;

  /**
   * Creates a new instance of {@link LogConsole} with write capabilities, only.
   */
  public static LogConsole writeOnlyConsole(PrintStream output) {
    return new LogConsole(/*input*/ null, Preconditions.checkNotNull(output));
  }

  /**
   * Creates a new instance of {@link LogConsole} with read and write capabilities.
   */
  public static LogConsole readWriteConsole(InputStream input, PrintStream output) {
    return new LogConsole(Preconditions.checkNotNull(input), Preconditions.checkNotNull(output));
  }

  private LogConsole(InputStream input, PrintStream output) {
    this.input = input;
    this.output = Preconditions.checkNotNull(output);
    output.println("Copybara source mover");
  }

  @Override
  public void error(String message) {
    printMessage("ERROR", message);
  }

  @Override
  public void warn(String message) {
    printMessage("WARN", message);
  }

  @Override
  public void info(String message) {
    printMessage("INFO", message);
  }

  @Override
  public void progress(final String task) {
    printMessage("TASK", task);
  }

  @Override
  public boolean promptConfirmation(String message) {
    Preconditions.checkState(input != null,
        "LogConsole cannot read user input if system console is not present.");
    return new ConsolePrompt(input, new PromptPrinter() {
      @Override
      public void print(String message) {
        output.print(dateFormat.format(new Date()) + " WARN: " + message + " [y/n] ");
      }
    }).promptConfirmation(message);
  }

  private void printMessage(final String messageKind, String message) {
    output.println(dateFormat.format(new Date()) + " " + messageKind + ": " + message);
  }
}
