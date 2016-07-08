package com.google.copybara.util.console;


import static com.google.copybara.util.console.AnsiEscapes.Color.BLUE;
import static com.google.copybara.util.console.AnsiEscapes.Color.GREEN;
import static com.google.copybara.util.console.AnsiEscapes.Color.RED;
import static com.google.copybara.util.console.AnsiEscapes.Color.YELLOW;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Scanner;

/**
 * A console that prints the the output using fancy ANSI capabilities.
 */
public final class AnsiConsole implements Console {

  private static final ImmutableSet<String> YES = ImmutableSet.of("y", "yes");
  private static final ImmutableSet<String> NO = ImmutableSet.of("n", "no");

  private final InputStream input;
  private final PrintStream output;
  private boolean lastWasProgress = false;
  private final Object lock = new Object();

  //blue red yellow blue green red
  public AnsiConsole(InputStream input, PrintStream output) {
    this.input = Preconditions.checkNotNull(input);
    this.output = Preconditions.checkNotNull(output);
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

  @Override
  public boolean promptConfirmation(String message) {
    Scanner scanner = new Scanner(input);
    while (true) {
      printPromptMessage(message);
      if (scanner.hasNextLine()) {
        String answer = scanner.nextLine().trim().toLowerCase();
        if (YES.contains(answer)) {
          return true;
        }
        if (NO.contains(answer)) {
          return false;
        }
      }
    }
  }

  private void printPromptMessage(String message) {
    synchronized (lock) {
      lastWasProgress = false;
      output.print(YELLOW.write("WARN: ") + message + " [y/n] ");
    }
  }
}
