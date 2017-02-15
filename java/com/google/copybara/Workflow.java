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

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.Destination.Writer;
import com.google.copybara.Info.MigrationReference;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Represents a particular migration operation that can occur for a project. Each project can have
 * multiple workflows. Each workflow has a particular origin and destination.
 * @param <O> Origin revision type.
 * @param <D> Destination revision type.
 */
public class Workflow<O extends Revision, D extends Revision> implements Migration {

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

  public Workflow(
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
    mode.run(newRunHelper(workdir, resolvedRef));
  }

  protected WorkflowRunHelper<O, D> newRunHelper(Path workdir, O resolvedRef)
      throws ValidationException, IOException, RepoException {
    return new WorkflowRunHelper<>(this, workdir, resolvedRef);
  }

  @Override
  public Info getInfo() throws RepoException, ValidationException {
    Writer writer = destination.newWriter(destinationFiles);
    String lastRef = writer.getPreviousRef(origin.getLabelName());
    O lastMigrated = (lastRef == null) ? null : origin.resolve(lastRef);
    O lastResolved = origin.resolve(/*sourceRef=*/ null);

    ImmutableList<Change<O>> changes =
        origin.newReader(originFiles, authoring).changes(lastMigrated, lastResolved);

    MigrationReference<O> migrationRef = MigrationReference.create(
        String.format("workflow_%s", name), lastMigrated, changes);
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

  public Glob getOriginFiles() {
    return originFiles;
  }

  public Glob getDestinationFiles() {
    return destinationFiles;
  }

  public Console getConsole() {
    return console;
  }

  public WorkflowOptions getWorkflowOptions() {
    return workflowOptions;
  }

  public boolean isForce() {
    return force;
  }

  @Nullable
  public Transformation getReverseTransformForCheck() {
    return reverseTransformForCheck;
  }

  public boolean isVerbose() {
    return verbose;
  }

  @Nullable
  public String getLastRevisionFlag() {
    return lastRevisionFlag;
  }

  public WorkflowMode getMode() {
    return mode;
  }
}
