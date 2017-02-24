/*
 * Copyright (C) 2017 Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.copybara.Destination.WriterResult;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.util.DiffUtil;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import javax.annotation.Nullable;

/**
 * Runs a single migration step for a {@link Workflow}, using its configuration.
 */
public class WorkflowRunHelper<O extends Revision, D extends Revision> {

  private final Workflow<O, D> workflow;
  private final Path workdir;
  private final O resolvedRef;
  private final Origin.Reader<O> originReader;
  @Nullable
  private final Destination.Reader<D> destinationReader;
  private final Destination.Writer writer;

  /**
   * @param workdir working directory to use for the transformations
   * @param resolvedRef revision to migrate
   */
  public WorkflowRunHelper(Workflow<O, D> workflow, Path workdir, O resolvedRef)
      throws ValidationException, IOException, RepoException {
    this.workflow = Preconditions.checkNotNull(workflow);
    this.workdir = Preconditions.checkNotNull(workdir);
    this.resolvedRef = Preconditions.checkNotNull(resolvedRef);
    this.originReader = workflow.getOrigin()
        .newReader(workflow.getOriginFiles(), workflow.getAuthoring());
    this.writer = workflow.getDestination().newWriter(workflow.getDestinationFiles(),
                                                      workflow.getMigrationIdentity(resolvedRef));
    this.destinationReader = workflow.getDestination().newReader(workflow.getDestinationFiles());
  }

  protected WorkflowRunHelper<O, D> forChanges(Changes changes)
      throws RepoException, ValidationException, IOException {
    return this;
  }

  protected Path getWorkdir() {
    return workdir;
  }

  protected O getResolvedRef() {
    return resolvedRef;
  }

  /**
   * Authoring configuration.
   */
  Authoring getAuthoring() {
    return workflow.getAuthoring();
  }

  /**
   * Console to use for printing messages.
   */
  Console getConsole() {
    return workflow.getConsole();
  }

  /**
   * Options that change how workflows behave.
   */
  WorkflowOptions workflowOptions() {
    return workflow.getWorkflowOptions();
  }

  boolean isForce() {
    return workflow.isForce();
  }

  Destination getDestination() {
    return workflow.getDestination();
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
   *
   * @param rev revision to the version which will be written to the destination
   * @param processConsole console to use to print progress messages
   * @param metadata metadata of the change to be migrated
   * @param changes changes included in this migration
   * @return The result of this migration
   */
  WriterResult migrate(O rev, Console processConsole, Metadata metadata,
      Changes changes)
      throws IOException, RepoException, ValidationException {
    return migrate(rev, processConsole, metadata, changes, /*destinationBaseline=*/ null);
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
    PathMatcher originFiles = workflow.getOriginFiles().relativeTo(checkoutDir);
    processConsole.progress("Removing excluded origin files");

    int deleted = FileUtil.deleteFilesRecursively(
        checkoutDir, FileUtil.notPathMatcher(originFiles));
    if (deleted != 0) {
      processConsole.info(
          String.format("Removed %d files from workdir that do not match origin_files", deleted));
    }

    Path originCopy = null;
    if (workflow.getReverseTransformForCheck() != null) {
      workflow.getConsole().progress("Making a copy or the workdir for reverse checking");
      originCopy = Files.createDirectories(workdir.resolve("origin"));
      FileUtil.copyFilesRecursively(checkoutDir, originCopy, FAIL_OUTSIDE_SYMLINKS);
    }

    TransformWork transformWork =
        new TransformWork(
            checkoutDir,
            metadata,
            changes,
            workflow.getConsole(),
            new MigrationInfo(workflow.getOrigin().getLabelName(), getDestinationReader()),
            resolvedRef);
    workflow.getTransformation().transform(transformWork);

    if (workflow.getReverseTransformForCheck() != null) {
      workflow.getConsole().progress("Checking that the transformations can be reverted");
      Path reverse = Files.createDirectories(workdir.resolve("reverse"));
      FileUtil.copyFilesRecursively(checkoutDir, reverse, FAIL_OUTSIDE_SYMLINKS);
      workflow
          .getReverseTransformForCheck()
          .transform(
              new TransformWork(
                  reverse,
                  new Metadata(transformWork.getMessage(), transformWork.getAuthor()),
                  changes,
                  workflow.getConsole(),
                  new MigrationInfo(/*originLabel=*/ null, (ChangeVisitable) null),
                  resolvedRef));
      String diff = new String(DiffUtil.diff(originCopy, reverse, workflow.isVerbose()),
          StandardCharsets.UTF_8);
      if (!diff.trim().isEmpty()) {
        workflow.getConsole().error("Non reversible transformations:\n"
            + DiffUtil.colorize(workflow.getConsole(), diff));
        throw new ValidationException(String.format("Workflow '%s' is not reversible", workflow.getName()));
      }
    }

    workflow.getConsole().progress("Checking that destination_files covers all files in transform result");
    new ValidateDestinationFilesVisitor(workflow.getDestinationFiles(), checkoutDir)
        .verifyFilesToWrite();

    // TODO(malcon): Pass metadata object instead
    TransformResult transformResult =
        new TransformResult(
            checkoutDir, ref, transformWork.getAuthor(), transformWork.getMessage(), resolvedRef);
    if (destinationBaseline != null) {
      transformResult = transformResult.withBaseline(destinationBaseline);
    }

    transformResult = transformResult.withAskForConfirmation(workflow.isAskForConfirmation());

    WriterResult result = writer.write(transformResult, processConsole);
    Verify.verifyNotNull(result, "Destination returned a null result.");
    return result;
  }


  ImmutableList<Change<O>> changesSinceLastImport() throws RepoException, ValidationException {
    return originReader.changes(getLastRev(), resolvedRef);
  }

  /**
   * Get last imported revision or fail if it cannot be found.
   *
   * @throws RepoException if a last revision couldn't be found
   */
  O getLastRev() throws RepoException, ValidationException {
    O lastRev = maybeGetLastRev();
    if (lastRev == null) {
      throw new CannotResolveRevisionException(String.format(
          "Previous revision label %s could not be found in %s and --last-rev flag"
              + " was not passed", workflow.getOrigin().getLabelName(), workflow.getDestination()));
    }
    return lastRev;
  }

  /**
   * Returns the last revision that was imported from this origin to the destination. Returns
   * {@code null} if it cannot be determined.
   *
   * <p>If {@code --last-rev} is specified, that revision will be used. Otherwise, the previous
   * revision will be resolved in the destination with the origin label.
   */
  @Nullable
  O maybeGetLastRev() throws RepoException, ValidationException {
    if (workflow.getLastRevisionFlag() != null) {
      try {
        return workflow.getOrigin().resolve(workflow.getLastRevisionFlag());
      } catch (RepoException e) {
        throw new CannotResolveRevisionException(
            "Could not resolve --last-rev flag. Please make sure it exists in the origin: "
                + workflow.getLastRevisionFlag(),
            e);
      }
    }

    String previousRef = writer.getPreviousRef(workflow.getOrigin().getLabelName());
    return (previousRef == null) ? null : workflow.getOrigin().resolve(previousRef);
  }
}
