package com.google.copybara.util.console;


import static com.google.copybara.util.console.AnsiEscapes.Color.BLUE;
import static com.google.copybara.util.console.AnsiEscapes.Color.GREEN;
import static com.google.copybara.util.console.AnsiEscapes.Color.RED;
import static com.google.copybara.util.console.AnsiEscapes.Color.YELLOW;

import java.io.PrintStream;

/**
 * A console that prints the the output using fancy ANSI capabilities.
 */
public final class AnsiConsole implements Console {

  private final PrintStream output;
  private boolean lastWasProgress = false;
  private final Object lock = new Object();

  //blue red yellow blue green red
  public AnsiConsole(PrintStream output) {
    this.output = output;
    // Just because we can!
    output.println(BLUE.write("C")
        + RED.write("o")
        + YELLOW.write("p")
        + BLUE.write("y")
        + GREEN.write("b")
        + RED.write("a")
        + BLUE.write("r")
        + RED.write("a")+" source mover"
    );
  }

  @Override
  public void error(String message) {
    synchronized (lock) {
      lastWasProgress = false;
      output.println(RED.write("ERROR: ") + message);
    }
  }

  @Override
  public void warn(String message) {
    synchronized (lock) {
      lastWasProgress = false;
      output.println(YELLOW.write("WARN: ") + message);
    }
  }

  @Override
  public void info(String message) {
    synchronized (lock) {
      lastWasProgress = false;
      output.println(GREEN.write("INFO: ") + message);
    }
  }

  @Override
  public void progress(String progress) {
    synchronized (lock) {
      if (lastWasProgress) {
        output.print(AnsiEscapes.oneLineUp() + AnsiEscapes.deleteLine());
      }
      output.println(GREEN.write("Task: ") + progress);
      lastWasProgress = true;
    }
  }
}
