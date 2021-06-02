/*
 * Copyright (C) 2020 Google Inc.
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


package com.google.copybara.format;


import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.GeneralOptions;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
import com.google.copybara.exception.NonReversibleValidationException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.BadExitStatusWithOutputException;
import com.google.copybara.util.CommandOutput;
import com.google.copybara.util.CommandOutputWithStatus;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.Consoles;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import javax.annotation.Nullable;

/** Format using buildifier */
public class BuildifierFormat implements Transformation {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final BuildifierOptions buildifierOptions;
  private final GeneralOptions generalOptions;

  private final Glob glob;
  private final LintMode lintMode;
  private final ImmutableList<String> warnings;
  @Nullable private final String type;

  BuildifierFormat(
      BuildifierOptions buildifierOptions,
      GeneralOptions generalOptions,
      Glob glob,
      LintMode lintMode,
      ImmutableList<String> warnings,
      @Nullable String type) {
    this.buildifierOptions = Preconditions.checkNotNull(buildifierOptions);
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.glob = Preconditions.checkNotNull(glob);
    this.lintMode = Preconditions.checkNotNull(lintMode);
    this.warnings = warnings;
    this.type = type;
  }

  @Override
  public TransformationStatus transform(TransformWork work)
      throws IOException, ValidationException {
    PathMatcher pathMatcher = glob.relativeTo(work.getCheckoutDir());
    ImmutableList.Builder<String> paths = ImmutableList.builder();
    Files.walkFileTree(
        work.getCheckoutDir(),
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            if (pathMatcher.matches(file)) {
              paths.add(file.toAbsolutePath().toString());
            }
            return FileVisitResult.CONTINUE;
          }
        });
    ImmutableList<String> builtPaths = paths.build();
    if (builtPaths.isEmpty()) {
      return TransformationStatus.noop(glob + " didn't match any build file to format");
    }
    for (List<String> sublist : Iterables.partition(builtPaths, buildifierOptions.batchSize)) {
      run(work.getConsole(), work.getCheckoutDir(), sublist);
    }
    return TransformationStatus.success();
  }

  /** Runs buildifier with the given arguments. */
  private void run(Console console, Path checkoutDir, Iterable<String> args)
      throws IOException, ValidationException {
    ImmutableList.Builder<String> argBuilder = new Builder<String>()
        .add(buildifierOptions.buildifierBin);
    if (type != null) {
      argBuilder.add("-type=" + type);
    }
    if (lintMode != LintMode.OFF) {
      argBuilder.add("-lint=" + lintMode.toString().toLowerCase());
      if (!warnings.isEmpty()) {
        argBuilder.add("-warnings=" + Joiner.on(",").join(warnings));
      }
    }
    String[] argv = argBuilder.addAll(args).build().toArray(new String[0]);

    try {
      Command cmd = new Command(argv, /*environmentVariables*/ null, checkoutDir.toFile());
      CommandOutputWithStatus output = generalOptions.newCommandRunner(cmd)
          .withVerbose(generalOptions.isVerbose())
          .execute();
      if (!output.getStdout().isEmpty()) {
        logger.atInfo().log("buildifier stdout: %s", output.getStdout());
      }
      if (!output.getStderr().isEmpty()) {
        logger.atInfo().log("buildifier stderr: %s", output.getStderr());
      }
    } catch (BadExitStatusWithOutputException e) {
      log(console, e.getOutput());
      checkCondition(e.getResult().getTerminationStatus().getExitCode() != 1,
          "Build file(s) couldn't be formatted because there was a syntax error");
      throw new IOException("Failed to execute buildifier with args: " + args, e);
    } catch (CommandException e) {
      throw new IOException("Failed to execute buildifier with args: " + args, e);
    }
  }

  private static void log(Console console, CommandOutput output) {
    Consoles.logLines(console, "buildifier stdout: ", output.getStdout());
    Consoles.logLines(console, "buildifier stderr: ", output.getStderr());
  }

  @Override
  public Transformation reverse() throws NonReversibleValidationException {
    return this;
  }

  @Override
  public String describe() {
    return "Buildifier";
  }

  /**
   * Valid modes that we support for buildifier -lint flag.
   */
  public enum LintMode {
    OFF,
    // WARN, // Not exposed for now since we don't show the stderr/out warnings in the console
    FIX
  }
}
