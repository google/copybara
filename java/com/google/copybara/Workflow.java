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

import static com.google.copybara.ValidationException.checkCondition;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.copybara.Destination.DestinationStatus;
import com.google.copybara.Destination.Writer;
import com.google.copybara.Info.MigrationReference;
import com.google.copybara.Origin.Reader;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.profiler.Profiler;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
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
  private final boolean initHistoryFlag;
  private final Console console;
  private final GeneralOptions generalOptions;
  private final Glob originFiles;
  private final Glob destinationFiles;
  private final WorkflowMode mode;
  private final WorkflowOptions workflowOptions;

  @Nullable
  private final Transformation reverseTransformForCheck;
  private final boolean verbose;
  private final boolean askForConfirmation;
  private final boolean force;
  private final ConfigFile<?> mainConfigFile;
  private final Supplier<ImmutableMap<String, ? extends ConfigFile<?>>> allConfigFiles;
  private final boolean dryRunMode;
  private final boolean checkLastRevState;

  public Workflow(
      String name,
      Origin<O> origin,
      Destination<D> destination,
      Authoring authoring,
      Transformation transformation,
      @Nullable String lastRevisionFlag,
      boolean initHistoryFlag,
      GeneralOptions generalOptions,
      Glob originFiles,
      Glob destinationFiles,
      WorkflowMode mode,
      WorkflowOptions workflowOptions,
      @Nullable Transformation reverseTransformForCheck,
      boolean askForConfirmation,
      ConfigFile<?> mainConfigFile,
      Supplier<ImmutableMap<String, ? extends ConfigFile<?>>> allConfigFiles,
      boolean dryRunMode, boolean checkLastRevState) {
    this.name = Preconditions.checkNotNull(name);
    this.origin = Preconditions.checkNotNull(origin);
    this.destination = Preconditions.checkNotNull(destination);
    this.authoring = Preconditions.checkNotNull(authoring);
    this.transformation = Preconditions.checkNotNull(transformation);
    this.lastRevisionFlag = lastRevisionFlag;
    this.initHistoryFlag = initHistoryFlag;
    this.console = Preconditions.checkNotNull(generalOptions.console());
    this.generalOptions = generalOptions;
    this.originFiles = Preconditions.checkNotNull(originFiles);
    this.destinationFiles = Preconditions.checkNotNull(destinationFiles);
    this.mode = Preconditions.checkNotNull(mode);
    this.workflowOptions = Preconditions.checkNotNull(workflowOptions);
    this.reverseTransformForCheck = reverseTransformForCheck;
    this.verbose = generalOptions.isVerbose();
    this.askForConfirmation = askForConfirmation;
    this.force = generalOptions.isForced();
    this.mainConfigFile = Preconditions.checkNotNull(mainConfigFile);
    this.allConfigFiles = allConfigFiles;
    this.checkLastRevState = checkLastRevState;
    this.dryRunMode = dryRunMode;
  }

  @Override
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
    validateFlags();
    try (ProfilerTask ignore = profiler().start("run/" + name)) {
      console.progress("Getting last revision: "
          + "Resolving " + ((sourceRef == null) ? "origin reference" : sourceRef));
      O resolvedRef = generalOptions.repoTask("origin.resolve_source_ref",
          () ->origin.resolve(sourceRef));

      logger.log(Level.INFO, String.format(
              "Running Copybara for workflow '%s' and ref '%s': %s",
              name, resolvedRef.asString(),
              this.toString()));
      logger.log(Level.INFO, String.format("Using working directory : %s", workdir));
      try (ProfilerTask ignored = profiler().start(mode.toString().toLowerCase())) {
        mode.run(newRunHelper(workdir, resolvedRef));
      }
    }
  }

  /**
   * Validates if flags are compatible with this workflow.
   *
   * @throws ValidationException if flags are invalid for this workflow
   */
  private void validateFlags() throws ValidationException {
    checkCondition(!isInitHistory() || mode != WorkflowMode.CHANGE_REQUEST,
        "%s is not compatible with %s",
            WorkflowOptions.INIT_HISTORY_FLAG, WorkflowMode.CHANGE_REQUEST);
    checkCondition(!isCheckLastRevState() || mode != WorkflowMode.CHANGE_REQUEST,
            "%s is not compatible with %s",
                WorkflowOptions.CHECK_LAST_REV_STATE, WorkflowMode.CHANGE_REQUEST);
  }

  protected WorkflowRunHelper<O, D> newRunHelper(Path workdir, O resolvedRef)
      throws ValidationException, RepoException {

    Reader<O> reader = getOrigin()
        .newReader(getOriginFiles(), getAuthoring());
    String groupId = computeGroupIdentity(reader.getGroupIdentity(resolvedRef));
    Writer<D> writer = getDestination().newWriter(getDestinationFiles(), dryRunMode, groupId,
        /*oldWriter=*/ null);
    return new WorkflowRunHelper<>(this, workdir, resolvedRef, reader, writer,
        groupId);
  }

  Set<String> configPaths() {
    return allConfigFiles.get().keySet();
  }

  @Override
  public Info<? extends Revision> getInfo() throws RepoException, ValidationException {
    return generalOptions.repoTask(
        "info",
        (Callable<Info<? extends Revision>>)
            () -> {
              O lastResolved =
                  generalOptions.repoTask(
                      "origin.last_resolved", () -> origin.resolve(/* reference= */ null));

              Reader<O> oReader = origin.newReader(originFiles, authoring);
              String groupIdentity = oReader.getGroupIdentity(lastResolved);
              DestinationStatus destinationStatus =
                  generalOptions.repoTask(
                      "destination.previous_ref", () -> getDestinationStatus(groupIdentity));

              O lastMigrated =
                  generalOptions.repoTask(
                      "origin.last_migrated",
                      () ->
                          (destinationStatus == null)
                              ? null
                              : origin.resolve(destinationStatus.getBaseline()));

              ImmutableList<Change<O>> changes =
                  generalOptions.repoTask(
                      "origin.changes", () -> oReader.changes(lastMigrated, lastResolved));

              MigrationReference<O> migrationRef =
                  MigrationReference.create(
                      String.format("workflow_%s", name), lastMigrated, changes);
              return Info.create(ImmutableList.of(migrationRef));
            });
  }

  @Nullable
  private DestinationStatus getDestinationStatus(String groupIdentity)
      throws RepoException, ValidationException {
    if (getLastRevisionFlag() != null) {
      return new DestinationStatus(getLastRevisionFlag(), ImmutableList.of());
    }
    // TODO(malcon): Should be dryRun=true but some destinations are still not implemented.
    // Should be K since info doesn't write but only read.
    return destination.newWriter(destinationFiles, /*dryrun=*/false,
        computeGroupIdentity(groupIdentity), /*oldWriter=*/null)
        .getDestinationStatus(origin.getLabelName());
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

  public boolean isInitHistory() {
    return initHistoryFlag;
  }

  public WorkflowMode getMode() {
    return mode;
  }

  @Override
  public String getModeString() {
    return mode.toString();
  }

  public boolean isCheckLastRevState() {
    return checkLastRevState;
  }

  public boolean isDryRunMode() {
    return dryRunMode;
  }

  /**
   * Migration identity tries to create a stable identifier for the migration that is stable between
   * Copybara invocations for the same reference. For example it will contain the copy.bara.sky
   * config file location relative to the root, the workflow name or the context reference used in
   * the request.
   *
   * <p>This identifier can be used by destinations to reuse code reviews, etc.
   */
  String getMigrationIdentity(Revision requestedRevision) {
    boolean contextRefDefined = requestedRevision.contextReference() != null;
    String ctxRef =
        contextRefDefined ? requestedRevision.contextReference() : requestedRevision.asString();

    return computeIdentity("ChangeIdentity", ctxRef);
  }

  /**
   * Visible for extension
   */
  @Nullable
  protected String computeGroupIdentity(@Nullable String originGroupId) {
    return originGroupId == null ? null : computeIdentity("OriginGroupIdentity", originGroupId);
  }

  private String computeIdentity(String type, String ref) {
    String identity = MoreObjects.toStringHelper(type)
            .add("type", "workflow")
            .add("config_path", mainConfigFile.relativeToRoot())
            .add("workflow_name", this.name)
        .add("context_ref", ref)
            .toString();
    String hash = BaseEncoding.base16().encode(
        Hashing.md5()
                    .hashString(identity, StandardCharsets.UTF_8)
                    .asBytes());

    // Important to log the source of the hash and the hash for debugging purposes.
    logger.info("Computed migration identity hash for " + identity + " as " + hash);
    return hash;
  }

  @Override
  public ConfigFile<?> getMainConfigFile() {
    return mainConfigFile;
  }

  public Profiler profiler() {
    return generalOptions.profiler();
  }

  public Supplier<ImmutableMap<String, ? extends ConfigFile<?>>> getAllConfigFiles() {
    return allConfigFiles;
  }

  public GeneralOptions getGeneralOptions() {
    return generalOptions;
  }
}
