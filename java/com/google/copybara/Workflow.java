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

import static com.google.copybara.util.FileUtil.CopySymlinkStrategy.FAIL_OUTSIDE_SYMLINKS;

import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.copybara.Destination.Writer;
import com.google.copybara.Destination.WriterResult;
import com.google.copybara.Info.MigrationReference;
import com.google.copybara.util.DiffUtil;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
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
public abstract class Workflow<R extends Origin.Reference> implements Migration {

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
  abstract WorkflowOptions workflowOptions();

  @Nullable
  abstract Transformation reverseTransformForCheck();
  abstract boolean verbose();
  abstract boolean askForConfirmation();

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
        .add("reverseTransformForCheck", reverseTransformForCheck())
        .add("askForConfirmation", askForConfirmation())
        .toString();
  }

  @Override
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
    mode().run(new RunHelper<>(workdir, resolvedRef));
  }

  @Override
  public Info getInfo() throws RepoException, ValidationException {
    Writer writer = destination().newWriter(destinationFiles());
    String lastRef = writer.getPreviousRef(origin().getLabelName());
    R lastMigrated = (lastRef == null) ? null : origin().resolve(lastRef);
    R lastResolved = origin().resolve(/*sourceRef=*/ null);

    MigrationReference migrationRef = MigrationReference.create(
        String.format("workflow_%s", name()), lastMigrated, lastResolved);
    return Info.create(ImmutableList.of(migrationRef));
  }

  final class RunHelper<M extends R> {
    private final Path workdir;
    final M resolvedRef;
    private final Origin.Reader<R> reader;
    private final Destination.Writer writer;

    /**
     * @param workdir working directory to use for the transformations
     * @param resolvedRef reference to migrate
     */
    RunHelper(Path workdir, M resolvedRef) throws ValidationException {
      this.workdir = Preconditions.checkNotNull(workdir);
      this.resolvedRef = Preconditions.checkNotNull(resolvedRef);
      this.reader = origin().newReader(originFiles(), authoring());
      this.writer = destination().newWriter(destinationFiles());
    }

    M getResolvedRef() {
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

    Origin.Reader<R> getReader() {
      return reader;
    }

    /**
     * Performs a full migration, including checking out files from the origin, deleting excluded
     * files, transforming the code, and writing to the destination. This writes to the destination
     * exactly once.
     * @param ref reference to the version which will be written to the destination
     * @param processConsole console to use to print progress messages
     * @param metadata metadata of the change to be migrated
     * @param changes changes included in this migration
     *
     * @return The result of this migration
     */
    WriterResult migrate(R ref, Console processConsole, Metadata metadata,
        Changes changes)
        throws IOException, RepoException, ValidationException {
      return migrate(ref, processConsole, metadata, changes, /*destinationBaseline=*/ null);
    }

    WriterResult migrate(R ref, Console processConsole,
        Metadata metadata, Changes changes, @Nullable String destinationBaseline)
        throws IOException, RepoException, ValidationException {
      processConsole.progress("Cleaning working directory");
      FileUtil.deleteAllFilesRecursively(workdir);
      Path checkoutDir = workdir.resolve("checkout");
      Files.createDirectories(checkoutDir);

      processConsole.progress("Checking out the change");
      reader.checkout(ref, checkoutDir);

      // Remove excluded origin files.
      PathMatcher originFiles = originFiles().relativeTo(checkoutDir);
      processConsole.progress("Removing excluded origin files");

      int deleted = FileUtil.deleteFilesRecursively(
          checkoutDir, FileUtil.notPathMatcher(originFiles));
      if (deleted != 0) {
        processConsole.info(
            String.format("Removed %d files from workdir that do not match origin_files", deleted));
      }

      Path originCopy = null;
      if (reverseTransformForCheck() != null) {
        console().progress("Making a copy or the workdir for reverse checking");
        originCopy = Files.createDirectories(workdir.resolve("origin"));
        FileUtil.copyFilesRecursively(checkoutDir, originCopy, FAIL_OUTSIDE_SYMLINKS);
      }

      TransformWork transformWork = new TransformWork(checkoutDir, metadata, changes, console());
      transformation().transform(transformWork);

      if (reverseTransformForCheck() != null) {
        console().progress("Checking that the transformations can be reverted");
        Path reverse = Files.createDirectories(workdir.resolve("reverse"));
        FileUtil.copyFilesRecursively(checkoutDir, reverse, FAIL_OUTSIDE_SYMLINKS);
        reverseTransformForCheck().transform(
            new TransformWork(reverse, metadata, changes, console())
        );
        String diff = new String(DiffUtil.diff(originCopy, reverse, verbose()),
            StandardCharsets.UTF_8);
        if (!diff.trim().isEmpty()) {
          console().error("Non reversible transformations:\n"
              + DiffUtil.colorize(console(), diff));
          throw new ValidationException(String.format("Workflow '%s' is not reversible", name()));
        }
      }

      // TODO(malcon): Pass metadata object instead
      TransformResult transformResult = new TransformResult(checkoutDir, ref,
          transformWork.getAuthor(),
          transformWork.getMessage());
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
      return reader.changes(getLastRev(), resolvedRef);
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

      String previousRef = writer.getPreviousRef(origin().getLabelName());
      return (previousRef == null) ? null : origin().resolve(previousRef);
    }
  }
}
