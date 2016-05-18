package com.google.copybara.util.console;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A simple console logger that prefixes the output with the time
 */
public class LogConsole implements Console {

  private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd HH:mm:ss.SSS");
  private final PrintStream output;
  private final Object lock = new Object();

  public LogConsole(PrintStream output) {
    this.output = output;
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

  private void printMessage(final String messageKind, String message) {
    synchronized (lock) {
      output.println(dateFormat.format(new Date()) + " " + messageKind + ": " + message);
    }
  }
}
