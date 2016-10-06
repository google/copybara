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

package com.google.copybara.util.console;

import static com.google.copybara.util.console.AnsiColor.BLUE;
import static com.google.copybara.util.console.AnsiColor.GREEN;
import static com.google.copybara.util.console.AnsiColor.RED;
import static com.google.copybara.util.console.AnsiColor.YELLOW;

import com.google.common.base.Preconditions;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

/**
 * A console that prints the the output using fancy ANSI capabilities.
 */
public final class AnsiConsole implements Console {

  private final InputStream input;
  private final PrintStream output;
  private boolean lastWasProgress = false;
  private final Object lock = new Object();

  //blue red yellow blue green red
  public AnsiConsole(InputStream input, PrintStream output) {
    this.input = Preconditions.checkNotNull(input);
    this.output = Preconditions.checkNotNull(output);
  }

  @Override
  public void startupMessage() {
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
  public boolean promptConfirmation(String message) throws IOException {
    return new ConsolePrompt(input, msg -> {
      synchronized (lock) {
        lastWasProgress = false;
        output.print(YELLOW.write("WARN: ") + msg + " [y/n] ");
      }
    }).promptConfirmation(message);
  }

  @Override
  public String colorize(AnsiColor ansiColor, String message) {
    return ansiColor.write(message);
  }
}
