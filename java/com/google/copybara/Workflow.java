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

package com.google.copybara;

import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.copybara.Destination.WriterResult;
import com.google.copybara.util.DiffUtil;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.syntax.BaseFunction;
import com.google.devtools.build.lib.syntax.Environment;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.Runtime.NoneType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Represents a particular migration operation that can occur for a project. Each project can have
 * multiple workflows. Each workflow has a particular origin and destination.
 */
@AutoValue
public abstract class Workflow<R extends Origin.Reference> {

  private final Logger logger = Logger.getLogger(this.getClass().getName());

  abstract String configName();
  abstract String name();

  /**
   * The repository that represents the source of truth
   */
  public abstract Origin<R> origin();

  /**
   * The destination repository to copy to.
   */
  public abstract Destination destination();

  /**
   * The author mapping between an origin and a destination
   */
  public abstract Authoring authoring();

  /**
   * Transformation to run before writing them to the destination.
   */
  public abstract Transformation transformation();

  @Nullable abstract String lastRevisionFlag();
  abstract Console console();
  abstract Glob originFiles();
  abstract Glob destinationFiles();
  abstract WorkflowMode mode();
  abstract boolean includeChangeListNotes();
  abstract WorkflowOptions workflowOptions();

  @Nullable
  abstract Transformation reverseTransformForCheck();
  abstract boolean verbose();
  abstract boolean askForConfirmation();

  abstract ImmutableList<BaseFunction> messageTransformers();
  abstract Environment environment();

  /**
   * Overrides Autovalue {@code toString()}, filtering the fields that are not part of the
   * configuration: Console is not part of the config, configName is in the parent, and
   * lastRevisionFlag is a command-line flag.
   */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name())
        .add("origin", origin())
        .add("destination", destination())
        .add("authoring", authoring())
        .add("transformation", transformation())
        .add("originFiles", originFiles())
        .add("destinationFiles", destinationFiles ())
        .add("mode", mode())
        .add("includeChangeListNotes", includeChangeListNotes())
        .add("reverseTransformForCheck", reverseTransformForCheck())
        .add("askForConfirmation", askForConfirmation())
        .add("messageTransformer",
            Iterables.transform(messageTransformers(), new BaseFunctionToName()))
        .toString();
  }

  public void run(Path workdir, @Nullable String sourceRef)
      throws RepoException, IOException, ValidationException {
    console().progress("Cleaning working directory");
    FileUtil.deleteAllFilesRecursively(workdir);

    console().progress("Getting last revision: "
        + "Resolving " + ((sourceRef == null) ? "origin reference" : sourceRef));
    R resolvedRef = origin().resolve(sourceRef);
    logger.log(Level.INFO,
        String.format(
            "Running Copybara for config '%s', workflow '%s' and ref '%s': %s",
            configName(), name(), resolvedRef.asString(),
            this.toString()));
    logger.log(Level.INFO, String.format("Using working directory : %s", workdir));
    mode().run(new RunHelper(workdir, resolvedRef));
  }

  private static class BaseFunctionToName implements Function<BaseFunction, String> {

    @Override
    public String apply(BaseFunction baseFunction) {
      return baseFunction.getName();
    }
  }

  final class RunHelper {
    private final Path workdir;
    final R resolvedRef;
    private final Destination.Writer writer;

    /**
     * @param workdir working directory to use for the transformations
     * @param resolvedRef reference to migrate
     */
    RunHelper(Path workdir, R resolvedRef) {
      this.workdir = Preconditions.checkNotNull(workdir);
      this.resolvedRef = Preconditions.checkNotNull(resolvedRef);
      this.writer = destination().newWriter();
    }

    R getResolvedRef() {
      return resolvedRef;
    }

    /**
     * Authoring configuration.
     */
    Authoring getAuthoring() {
      return authoring();
    }

    /** Console to use for printing messages. */
    Console getConsole() {
      return console();
    }

    /**
     * Options that change how workflows behave.
     */
    WorkflowOptions workflowOptions() {
      return Workflow.this.workflowOptions();
    }

    Destination getDestination() {
      return destination();
    }

    Origin<R> getOrigin() {
      return origin();
    }

    /**
     * Performs a full migration, including checking out files from the origin, deleting excluded
     * files, transforming the code, and writing to the destination. This writes to the destination
     * exactly once.
     * @param ref reference to the version which will be written to the destination
     * @param author the author that the destination change will be attributed to
     * @param processConsole console to use to print progress messages
     * @param message change message to write to the destination
     *
     * @return The result of this migration
     */
    WriterResult migrate(R ref, Author author, Console processConsole, String message)
        throws IOException, RepoException, ValidationException {
      return migrate(ref, author, processConsole, message, /*destinationBaseline=*/ null);
    }

    WriterResult migrate(R ref, Author author, Console processConsole, String message,
        @Nullable String destinationBaseline)
        throws IOException, RepoException, ValidationException {
      processConsole.progress("Cleaning working directory");
      FileUtil.deleteAllFilesRecursively(workdir);
      Path checkoutDir = workdir.resolve("checkout");
      Files.createDirectories(checkoutDir);

      processConsole.progress("Checking out the change");
      origin().checkout(ref, checkoutDir);

      // Remove excluded origin files.
      if (!originFiles().isAllFiles()) {
        PathMatcher pathMatcher = originFiles().relativeTo(checkoutDir);
        processConsole.progress("Removing excluded origin files");

        int result = FileUtil.deleteFilesRecursively(
            checkoutDir, FileUtil.notPathMatcher(pathMatcher));
        logger.log(Level.INFO,
            String.format("Removed %s files from workdir that were excluded", result));

        if (result == 0) {
          workflowOptions().reportNoop(console(),
              "Nothing was deleted in the workdir for origin_files: " + originFiles());
        }
      }

      Path originCopy = null;
      if (reverseTransformForCheck() != null) {
        console().progress("Making a copy or the workdir for reverse checking");
        originCopy = Files.createDirectories(workdir.resolve("origin"));
        FileUtil.copyFilesRecursively(checkoutDir, originCopy);
      }

      transformation().transform(new TransformWork(checkoutDir, message), processConsole);

      if (reverseTransformForCheck() != null) {
        console().progress("Checking that the transformations can be reverted");
        Path reverse = Files.createDirectories(workdir.resolve("reverse"));
        FileUtil.copyFilesRecursively(checkoutDir, reverse);
        reverseTransformForCheck().transform(new TransformWork(reverse, message), processConsole);
        String diff = new String(DiffUtil.diff(originCopy, reverse, verbose()),
            StandardCharsets.UTF_8);
        if (!diff.trim().isEmpty()) {
          console().error("Non reversible transformations:\n"
              + DiffUtil.colorize(console(), diff));
          throw new ConfigValidationException(String.format(
              "Workflow '%s' is not reversible", workflowOptions().getWorkflowName()));
        }
      }

      TransformResult transformResult =
          new TransformResult(checkoutDir, ref, author, message, destinationFiles());
      if (destinationBaseline != null) {
        transformResult = transformResult.withBaseline(destinationBaseline);
      }

      transformResult = transformResult.withAskForConfirmation(askForConfirmation());

      WriterResult result = writer.write(transformResult, processConsole);
      Verify.verifyNotNull(result, "Destination returned a null result.");
      return result;
    }


    ImmutableList<Change<R>> changesSinceLastImport() throws RepoException {
      R lastRev = getLastRev();
      if (lastRev == null) {
        throw new RepoException(String.format(
                "Previous revision label %s could not be found in %s and --last-rev flag"
                + " was not passed", origin().getLabelName(), destination()));
      }
      return origin().changes(getLastRev(), resolvedRef, authoring());
    }

    /**
     * Returns the last revision that was imported from this origin to the destination. Returns
     * {@code null} if it cannot be determined.
     *
     * <p>If {@code --last-rev} is specified, that revision will be used. Otherwise, the previous
     * reference will be resolved in the destination with the origin label.
     */
    @Nullable
    R getLastRev() throws RepoException {
      if (lastRevisionFlag() != null) {
        try {
          return origin().resolve(lastRevisionFlag());
        } catch (RepoException e) {
          throw new RepoException(
              "Could not resolve --last-rev flag. Please make sure it exists in the origin: "
                  + lastRevisionFlag(),
              e);
        }
      }

      String previousRef = destination().getPreviousRef(origin().getLabelName());
      return (previousRef == null) ? null : origin().resolve(previousRef);
    }

    String configName() {
      return Workflow.this.configName();
    }

    boolean includeChangeListNotes() {
      return Workflow.this.includeChangeListNotes();
    }

    /**
     * Create change message from the changes. If it cannot be computed, use
     * the default message.
     */
    MsgTransformerCtx<R> transformMetadata(String defaultMessage,
        Author author, Iterable<Change<R>> currentChanges, Iterable<Change<R>> alreadyMigrated)
        throws ValidationException {
      MsgTransformerCtx<R> ctx = new MsgTransformerCtx<>(defaultMessage, author, currentChanges,
          alreadyMigrated);
      for (BaseFunction func : Workflow.this.messageTransformers()) {
        try {
          Object result = func.call(ImmutableList.<Object>of(ctx),/*kwargs=*/null,/*ast*/null,
              environment());
          if (!(result instanceof NoneType)) {
            throw new ValidationException("Message transformer functions should not return"
                + " anything, but '" + func.getName() + "' returned:" + result);
          }
        } catch (EvalException e) {
          throw new ValidationException("Error while executing the message transformer "
              + func.getName(), e);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("This should not happen.", e);
        }
      }
      return ctx;
    }
  }
}
