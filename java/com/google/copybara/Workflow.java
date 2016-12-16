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

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.Destination.Writer;
import com.google.copybara.Destination.WriterResult;
import com.google.copybara.Info.MigrationReference;
import com.google.copybara.authoring.Authoring;
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
 * @param <O> Origin reference type.
 * @param <D> Destination reference type.
 */
public final class Workflow<O extends Reference, D extends Reference> implements Migration {

  private final Logger logger = Logger.getLogger(this.getClass().getName());

  private final String name;
  private final Origin<O> origin;
  private final Destination<D> destination;
  private final Authoring authoring;
  private final Transformation transformation;

  @Nullable
  private final String lastRevisionFlag;
  private final Console console;
  private final Glob originFiles;
  private final Glob destinationFiles;
  private final WorkflowMode mode;
  private final WorkflowOptions workflowOptions;

  @Nullable
  private final Transformation reverseTransformForCheck;
  private final boolean verbose;
  private final boolean askForConfirmation;
  private final boolean force;

  Workflow(
      String name,
      Origin<O> origin,
      Destination<D> destination,
      Authoring authoring,
      Transformation transformation,
      @Nullable String lastRevisionFlag,
      Console console,
      Glob originFiles,
      Glob destinationFiles,
      WorkflowMode mode,
      WorkflowOptions workflowOptions,
      @Nullable Transformation reverseTransformForCheck,
      boolean verbose,
      boolean askForConfirmation,
      boolean force) {
    this.name = Preconditions.checkNotNull(name);
    this.origin = Preconditions.checkNotNull(origin);
    this.destination = Preconditions.checkNotNull(destination);
    this.authoring = Preconditions.checkNotNull(authoring);
    this.transformation = Preconditions.checkNotNull(transformation);
    this.lastRevisionFlag = lastRevisionFlag;
    this.console = Preconditions.checkNotNull(console);
    this.originFiles = Preconditions.checkNotNull(originFiles);
    this.destinationFiles = Preconditions.checkNotNull(destinationFiles);
    this.mode = Preconditions.checkNotNull(mode);
    this.workflowOptions = Preconditions.checkNotNull(workflowOptions);
    this.reverseTransformForCheck = reverseTransformForCheck;
    this.verbose = verbose;
    this.askForConfirmation = askForConfirmation;
    this.force = force;
  }

  public String getName() {
    return name;
  }

  /**
   * The repository that represents the source of truth
   */
  public Origin<O> getOrigin() {
    return origin;
  }

  /**
   * The destination repository to copy to.
   */
  public Destination<D> getDestination() {
    return destination;
  }

  /**
   * The author mapping between an origin and a destination
   */
  public Authoring getAuthoring() {
    return authoring;
  }

  /**
   * Transformation to run before writing them to the destination.
   */
  public Transformation getTransformation() {
    return transformation;
  }

  public boolean isAskForConfirmation() {
    return askForConfirmation;
  }

  /**
   * Includes only the fields that are part of the configuration: Console is not part of the config,
   * configName is in the parent, and lastRevisionFlag is a command-line flag.
   */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name)
        .add("origin", origin)
        .add("destination", destination)
        .add("authoring", authoring)
        .add("transformation", transformation)
        .add("originFiles", originFiles)
        .add("destinationFiles", destinationFiles)
        .add("mode", mode)
        .add("reverseTransformForCheck", reverseTransformForCheck)
        .add("askForConfirmation", askForConfirmation)
        .toString();
  }

  @Override
  public void run(Path workdir, @Nullable String sourceRef)
      throws RepoException, IOException, ValidationException {
    console.progress("Cleaning working directory");
    FileUtil.deleteAllFilesRecursively(workdir);

    console.progress("Getting last revision: "
        + "Resolving " + ((sourceRef == null) ? "origin reference" : sourceRef));
    O resolvedRef = origin.resolve(sourceRef);
    logger.log(Level.INFO,
        String.format(
            "Running Copybara for workflow '%s' and ref '%s': %s",
            name, resolvedRef.asString(),
            this.toString()));
    logger.log(Level.INFO, String.format("Using working directory : %s", workdir));
    mode.run(new RunHelper<>(workdir, resolvedRef));
  }

  @Override
  public Info getInfo() throws RepoException, ValidationException {
    Writer writer = destination.newWriter(destinationFiles);
    String lastRef = writer.getPreviousRef(origin.getLabelName());
    O lastMigrated = (lastRef == null) ? null : origin.resolve(lastRef);
    O lastResolved = origin.resolve(/*sourceRef=*/ null);

    MigrationReference migrationRef = MigrationReference.create(
        String.format("workflow_%s", name), lastMigrated, lastResolved);
    return Info.create(ImmutableList.of(migrationRef));
  }

  @Override
  public ImmutableSetMultimap<String, String> getOriginDescription() {
    return origin.describe(originFiles);
  }

  @Override
  public ImmutableSetMultimap<String, String> getDestinationDescription() {
    return destination.describe(destinationFiles);
  }

  final class RunHelper<M extends O> {
    private final Path workdir;
    final M resolvedRef;
    private final Origin.Reader<O> originReader;
    @Nullable private final Destination.Reader<D> destinationReader;
    private final Destination.Writer writer;

    /**
     * @param workdir working directory to use for the transformations
     * @param resolvedRef reference to migrate
     */
    RunHelper(Path workdir, M resolvedRef) throws ValidationException, RepoException {
      this.workdir = Preconditions.checkNotNull(workdir);
      this.resolvedRef = Preconditions.checkNotNull(resolvedRef);
      this.originReader = origin.newReader(originFiles, authoring);
      this.writer = destination.newWriter(destinationFiles);
      this.destinationReader = destination.newReader(destinationFiles);
    }

    M getResolvedRef() {
      return resolvedRef;
    }

    /**
     * Authoring configuration.
     */
    Authoring getAuthoring() {
      return authoring;
    }

    /** Console to use for printing messages. */
    Console getConsole() {
      return console;
    }

    /**
     * Options that change how workflows behave.
     */
    WorkflowOptions workflowOptions() {
      return workflowOptions;
    }

    boolean isForce() {
      return force;
    }

    Destination getDestination() {
      return destination;
    }

    Origin.Reader<O> getOriginReader() {
      return originReader;
    }

    Destination.Reader<D> getDestinationReader() {
      return destinationReader;
    }

    boolean destinationSupportsPreviousRef() {
      return writer.supportsPreviousRef();
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
    WriterResult migrate(O ref, Console processConsole, Metadata metadata,
        Changes changes)
        throws IOException, RepoException, ValidationException {
      return migrate(ref, processConsole, metadata, changes, /*destinationBaseline=*/ null);
    }

    WriterResult migrate(O ref, Console processConsole,
        Metadata metadata, Changes changes, @Nullable String destinationBaseline)
        throws IOException, RepoException, ValidationException {
      processConsole.progress("Cleaning working directory");
      FileUtil.deleteAllFilesRecursively(workdir);
      Path checkoutDir = workdir.resolve("checkout");
      Files.createDirectories(checkoutDir);

      processConsole.progress("Checking out the change");
      originReader.checkout(ref, checkoutDir);

      // Remove excluded origin files.
      PathMatcher originFiles = Workflow.this.originFiles.relativeTo(checkoutDir);
      processConsole.progress("Removing excluded origin files");

      int deleted = FileUtil.deleteFilesRecursively(
          checkoutDir, FileUtil.notPathMatcher(originFiles));
      if (deleted != 0) {
        processConsole.info(
            String.format("Removed %d files from workdir that do not match origin_files", deleted));
      }

      Path originCopy = null;
      if (reverseTransformForCheck != null) {
        console.progress("Making a copy or the workdir for reverse checking");
        originCopy = Files.createDirectories(workdir.resolve("origin"));
        FileUtil.copyFilesRecursively(checkoutDir, originCopy, FAIL_OUTSIDE_SYMLINKS);
      }

      TransformWork transformWork = new TransformWork(checkoutDir, metadata, changes, console,
          new MigrationInfo(origin.getLabelName(), getDestinationReader()));
      transformation.transform(transformWork);

      if (reverseTransformForCheck != null) {
        console.progress("Checking that the transformations can be reverted");
        Path reverse = Files.createDirectories(workdir.resolve("reverse"));
        FileUtil.copyFilesRecursively(checkoutDir, reverse, FAIL_OUTSIDE_SYMLINKS);
        reverseTransformForCheck.transform(

            new TransformWork(reverse, metadata, changes, console,
                new MigrationInfo(/*originLabel=*/ null, (ChangeVisitable) null))
        );
        String diff = new String(DiffUtil.diff(originCopy, reverse, verbose),
            StandardCharsets.UTF_8);
        if (!diff.trim().isEmpty()) {
          console.error("Non reversible transformations:\n"
              + DiffUtil.colorize(console, diff));
          throw new ValidationException(String.format("Workflow '%s' is not reversible", name));
        }
      }

      // TODO(malcon): Pass metadata object instead
      TransformResult transformResult = new TransformResult(checkoutDir, ref,
          transformWork.getAuthor(),
          transformWork.getMessage());
      if (destinationBaseline != null) {
        transformResult = transformResult.withBaseline(destinationBaseline);
      }

      transformResult = transformResult.withAskForConfirmation(askForConfirmation);

      WriterResult result = writer.write(transformResult, processConsole);
      Verify.verifyNotNull(result, "Destination returned a null result.");
      return result;
    }


    ImmutableList<Change<O>> changesSinceLastImport() throws RepoException, ValidationException {
      return originReader.changes(getLastRev(), resolvedRef);
    }

    /**
     * Get last imported reference or fail if it cannot be found.
     *
     * @throws RepoException if a last revision couldn't be found
     */
    O getLastRev() throws RepoException, ValidationException {
      O lastRev = maybeGetLastRev();
      if (lastRev == null) {
        throw new CannotResolveReferenceException(String.format(
                "Previous revision label %s could not be found in %s and --last-rev flag"
                + " was not passed", origin.getLabelName(), destination));
      }
      return lastRev;
    }

    /**
     * Returns the last revision that was imported from this origin to the destination. Returns
     * {@code null} if it cannot be determined.
     *
     * <p>If {@code --last-rev} is specified, that revision will be used. Otherwise, the previous
     * reference will be resolved in the destination with the origin label.
     */
    @Nullable
    O maybeGetLastRev() throws RepoException, ValidationException {
      if (lastRevisionFlag != null) {
        try {
          return origin.resolve(lastRevisionFlag);
        } catch (RepoException e) {
          throw new CannotResolveReferenceException(
              "Could not resolve --last-rev flag. Please make sure it exists in the origin: "
                  + lastRevisionFlag,
              e);
        }
      }

      String previousRef = writer.getPreviousRef(origin.getLabelName());
      return (previousRef == null) ? null : origin.resolve(previousRef);
    }
  }
}
