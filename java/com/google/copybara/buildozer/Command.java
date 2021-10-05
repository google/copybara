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

package com.google.copybara.buildozer;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.copybara.exception.NonReversibleValidationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Printer;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkValue;

/** Represents a possibly-reversible Buildozer command. */
public final class Command implements StarlarkValue {

  private final String command;
  @Nullable
  private final String reverse;

  private Command(String command, @Nullable String reverse) {
    this.command = checkNotNull(command, "command");
    Preconditions.checkArgument(!command.trim().isEmpty(), "Found empty command");
    Preconditions.checkArgument(reverse == null || !reverse.trim().isEmpty(),
        "Found empty reversal command. Command was: %s", command);

    this.reverse = reverse;
    new ArgValidator(command).validate();
    if (reverse != null) {
      new ArgValidator(reverse).validate();
    }
  }

  static Command fromConfig(String command, @Nullable String reverse) throws EvalException {
    if (reverse == null) {
      List<String> components = Splitter.on(' ').limit(2).omitEmptyStrings().splitToList(command);
      if (components.size() == 2) {
        reverse = Command.reverse(components.get(0), components.get(1));
      }
    }

    try {
      return new Command(command, reverse);
    } catch (IllegalArgumentException ex) {
      throw new EvalException(ex);
    }
  }

  private static class ArgValidator {
    final List<String> argv;

    ArgValidator(String command) {
      this.argv = splitArgv(command);
    }

    void validateCount(boolean valid, String requirement) {
      Preconditions.checkArgument(valid,
          "'%s' requires %s, but got: %s", argv.get(0), requirement, argCount());
    }

    int argCount() {
      return argv.size() - 1;
    }

    void validate() {
      Preconditions.checkArgument(!argv.isEmpty(), "Expected an operation, but got empty string.");
      switch (argv.get(0)) {
        case "del_subinclude":
        case "rename":
        case "copy":
        case "copy_no_overwrite":
          validateCount(argCount() == 2, "exactly 2 arguments");
          break;
        case "fix":
        case "print":
        case "remove_comment":
          break; // can take 0+
        case "replace_subinclude":
        case "move":
          validateCount(argCount() >= 3, "at least 3 arguments");
          break;
        case "delete":
          validateCount(argCount() == 0, "exactly 0 arguments");
          break;
        case "replace":
          validateCount(argCount() == 3, "exactly 3 arguments");
          break;
        case "comment":
        case "remove":
        case "set":
        case "set_if_absent":
          validateCount(argCount() >= 1, "at least 1 argument");
          break;
        case "add":
        case "new_load":
        case "new":
          validateCount(argCount() >= 2, "at least 2 arguments");
          break;
        default:
          // We assume that all unary operations are covered.
          Preconditions.checkArgument(argCount() > 1,
              "Expected an operation, but got '%s'.", argv.get(0));
      }
    }
  }

  @Override
  public boolean isImmutable() {
    return true;
  }

  @Override
  public void repr(Printer printer) {
    Printer.format(printer, "buildozer.cmd(%s, reverse = %s)", command, reverse);
  }

  /**
   * Returns the command and arguments concatenated, which can be passed directly to Buildozer.
   */
  @Override
  public String toString() {
    return command;
  }

  /**
   * Returns the reverse version of this command.
   *
   * @throws UnsupportedOperationException if this instance is not reversible
   */
  Command reverse() throws NonReversibleValidationException {
    if (reverse == null) {
      throw new NonReversibleValidationException(
          "The current command is not auto-reversible and a reverse was not provided: " + command);
    }

    return new Command(reverse, command);
  }

  /** Calculates the reversal a command whose reversal has not been manually specified. */
  @Nullable
  private static String reverse(String commandName, String args) throws EvalException {
    switch (commandName) {
      case "add":
        return "remove " + args;
      case "remove":
        if (args.contains(" ")) {
          // Do not reverse 'remove attr' operation. Only 'remove attr value'
          return "add " + args;
        }
        return null;
      case "replace":
        List<String> reverseArgs = new ArrayList<>(splitArgv(args));
        if (reverseArgs.size() != 3) {
          throw Starlark.errorf(
              "Cannot reverse '%s %s', expected three arguments, but found %d.",
              commandName, args, reverseArgs.size());
        }
        Collections.swap(reverseArgs, 1, 2);
        return "replace " + Joiner.on(' ').join(reverseArgs);
    }
    return null;
  }

  private static List<String> splitArgv(String argv) {
    return Splitter.on(' ')
        .omitEmptyStrings()
        .splitToList(argv);
  }
}
