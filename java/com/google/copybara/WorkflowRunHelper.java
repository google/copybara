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

import static com.google.copybara.GeneralOptions.OUTPUT_ROOT_FLAG;
import static com.google.copybara.util.FileUtil.CopySymlinkStrategy.FAIL_OUTSIDE_SYMLINKS;

import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.Destination.DestinationStatus;
import com.google.copybara.Destination.Writer;
import com.google.copybara.DestinationEffect.Type;
import com.google.copybara.Origin.Baseline;
import com.google.copybara.Origin.Reader;
import com.google.copybara.Origin.Reader.ChangesResponse;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.feedback.Action;
import com.google.copybara.feedback.FinishHookContext;
import com.google.copybara.monitor.EventMonitor;
import com.google.copybara.monitor.EventMonitor.ChangeMigrationFinishedEvent;
import com.google.copybara.monitor.EventMonitor.ChangeMigrationStartedEvent;
import com.google.copybara.profiler.Profiler;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.transform.SkylarkConsole;
import com.google.copybara.util.DiffUtil;
import com.google.copybara.util.DiffUtil.DiffFile;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.Glob;
import com.google.copybara.util.InsideGitDirException;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.ProgressPrefixConsole;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Runs a single migration step for a {@link Workflow}, using its configuration.
 */
public class WorkflowRunHelper<O extends Revision, D extends Revision> {

  private static final Logger logger = Logger.getLogger(WorkflowRunHelper.class.getName());

  private final Workflow<O, D> workflow;
  private final Path workdir;
  private final O resolvedRef;
  private final Origin.Reader<O> originReader;
  protected final Destination.Writer<D> writer;
  @Nullable final String rawSourceRef;

  WorkflowRunHelper(
      Workflow<O, D> workflow,
      Path workdir,
      O resolvedRef,
      Reader<O> originReader,
      Writer<D> destinationWriter,
      @Nullable String rawSourceRef) {
    this.workflow = Preconditions.checkNotNull(workflow);
    this.workdir = Preconditions.checkNotNull(workdir);
    this.resolvedRef = Preconditions.checkNotNull(resolvedRef);
    this.originReader = Preconditions.checkNotNull(originReader);
    this.writer = Preconditions.checkNotNull(destinationWriter);
    this.rawSourceRef = rawSourceRef;
  }

  /**
   * origin_files used for this workflow
   */
  protected Glob getOriginFiles(){
    return workflow.getOriginFiles();
  }

  ChangeMigrator<O, D> getMigratorForChange(Change<?> change)
      throws RepoException, ValidationException {
    return getMigratorForChange(change, /*dryRun=*/ false);
  }

  /**
   * Get a migrator for the current change.
   */
  ChangeMigrator<O, D> getMigratorForChange(Change<?> change, boolean dryRun)
      throws RepoException, ValidationException {
    return getDefaultMigrator(dryRun);
  }

  /**
   * Get a default migrator with the workflow dry-run mode
   */
  ChangeMigrator<O, D> getDefaultMigrator() throws ValidationException {
    return getDefaultMigrator(workflow.isDryRunMode());
  }

  private ChangeMigrator<O, D> getDefaultMigrator(boolean dryRun) throws ValidationException {
    return new ChangeMigrator<>(
        workflow,
        workdir,
        originReader,
        // Create a new writer if dryrun is requested
        dryRun ? createDryRunWriter() : writer,
        resolvedRef,
        rawSourceRef);
  }

  public Profiler profiler() {
    return workflow.profiler();
  }

  final Writer<D> createDryRunWriter() throws ValidationException {
    return workflow.getDestination().newWriter(new WriterContext(
        workflow.getName(),
        workflow.getWorkflowOptions().workflowIdentityUser,
        /*dryRun=*/ true,
        resolvedRef));
  }

  protected Path getWorkdir() {
    return workdir;
  }

  O getResolvedRef() {
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

  private boolean isInitHistory() {
    return workflow.isInitHistory();
  }

  boolean isSquashWithoutHistory() {
    return workflow.getWorkflowOptions().squashSkipHistory;
  }

  Destination getDestination() {
    return workflow.getDestination();
  }

  Origin.Reader<O> getOriginReader() {
    return originReader;
  }

  Destination.Writer<D> getDestinationWriter() {
    return writer;
  }

  boolean destinationSupportsPreviousRef() {
    return writer.supportsHistory();
  }

  void maybeValidateRepoInLastRevState(@Nullable Metadata metadata) throws RepoException,
      ValidationException, IOException {
    if (!workflow.isCheckLastRevState() || isForce()) {
      return;
    }

    workflow
        .getGeneralOptions()
        .ioRepoTask(
            "validate_last_rev",
            () -> {
              O lastRev =
                  workflow.getGeneralOptions().repoTask("get_last_rev", this::maybeGetLastRev);

              if (lastRev == null) {
                // Not the job of this function to check for lastrev status.
                return null;
              }
              Change<O> change = originReader.change(lastRev);
              Changes changes = new Changes(ImmutableList.of(change), ImmutableList.of());
              ChangeMigrator<O, D> migrator = getMigratorForChange(change, /*dryRun=*/ true);

              try {
                workflow
                    .getGeneralOptions()
                    .ioRepoTask(
                        "migrate",
                        () ->
                            // We pass lastRev as the lastRev. This is not correct but we cannot
                            // know the previous rev of the last rev. Furthermore, this should only
                            // be used for generating messages, so users shouldn't care about the
                            // value (but they might care about its presence, so it cannot be null.
                            migrator.doMigrate(
                                lastRev,
                                lastRev,
                                new ProgressPrefixConsole(
                                    "Validating last migration: ", workflow.getConsole()),
                                metadata == null
                                    ? new Metadata(
                                    change.getMessage(), change.getAuthor(),
                                    ImmutableSetMultimap.of())
                                    : metadata,
                                changes,
                                /*destinationBaseline=*/ null,
                                lastRev));
                throw new ValidationException(
                    "Migration of last-rev '"
                        + lastRev.asString()
                        + "' didn't"
                        + " result in an empty change. This means that the result change of that"
                        + " migration was modified ouside of Copybara or that new changes happened"
                        + " later in the destination without using Copybara. Use --force if you"
                        + " really want to do the migration.");
              } catch (EmptyChangeException ignored) {
                // EmptyChangeException ignored
              }
              return null;
            });
  }

  ChangesResponse<O> getChanges(@Nullable O from, O to) throws RepoException, ValidationException {
    try (ProfilerTask ignore = workflow.profiler().start("get_changes")) {
      return originReader.changes(from, to);
    }
  }

  /**
   * Migrate a change for a workflow. Can overwrite the reader, writer, transformations, etc.
   */
  public static class ChangeMigrator<O extends Revision, D extends Revision> {

    private final Workflow<O, D> workflow;
    private final Path workdir;
    private final O resolvedRef;
    private final Reader<O> reader;
    private final Writer<D> writer;
    @Nullable
    private final String rawSourceRef;

    ChangeMigrator(Workflow<O, D> workflow, Path workdir, Reader<O> reader,
        Writer<D> writer, O resolvedRef, @Nullable String rawSourceRef) {
      this.workflow = Preconditions.checkNotNull(workflow);
      this.workdir = Preconditions.checkNotNull(workdir);
      this.resolvedRef = Preconditions.checkNotNull(resolvedRef);
      this.reader = Preconditions.checkNotNull(reader);
      this.writer = Preconditions.checkNotNull(writer);
      this.rawSourceRef = rawSourceRef;
    }

    /**
     * Return true if this change can be skipped because it would generate a noop in the
     * destination.
     *
     * <p>First we check if the change contains the files of the change and if they match
     * origin_files. Then we also check for potential changes in the config for configs that are
     * stored in the origin.
     */
    final boolean skipChange(Change<?> currentChange) {
      boolean skipChange = shouldSkipChange(currentChange);
      if (skipChange) {
        workflow.getConsole().verboseFmt("Skipped change %s as it would create an empty result.",
            currentChange.toString());
      }
      return skipChange;
    }

    /**
     * Returns true iff the given change should be skipped based on the origin globs and flags
     * provided.
     */
    final boolean shouldSkipChange(Change<?> currentChange) {
      if (workflow.isMigrateNoopChanges()) {
        return false;
      }
      // We cannot know the files included. Try to migrate then.
      if (currentChange.getChangeFiles() == null) {
        return false;
      }
      PathMatcher pathMatcher = getOriginFiles().relativeTo(Paths.get("/"));
      for (String changedFile : currentChange.getChangeFiles()) {
        if (pathMatcher.matches(Paths.get("/" + changedFile))) {
          return false;
        }
      }
      // This is an heuristic for cases where the Copybara configuration is stored in the same
      // folder as the origin code but excluded.
      //
      // The config root can be a subfolder of the files as seen by the origin. For example:
      // admin/copy.bara.sky could be present in the origin as root/admin/copy.bara.sky.
      // This might give us some false positives but they would be noop migrations.
      for (String changesFile : currentChange.getChangeFiles()) {
        for (String configPath : getConfigFiles()) {
          if (changesFile.endsWith(configPath)) {
            workflow.getConsole()
                .infoFmt("Migrating %s because %s config file changed at that revision",
                    currentChange.getRevision().asString(), changesFile);
            return false;
          }
        }
      }
      return true;
    }

    protected Set<String> getConfigFiles() {
      return workflow.configPaths();
    }

    protected Glob getOriginFiles() {
      return workflow.getOriginFiles();
    }

    protected Glob getDestinationFiles() {
      return workflow.getDestinationFiles();
    }

    protected Transformation getTransformation() {
      return workflow.getTransformation();
    }

    @Nullable
    protected Transformation getReverseTransformForCheck() {
      return workflow.getReverseTransformForCheck();
    }

    public final Profiler profiler() {
      return workflow.profiler();
    }

    /**
     * Performs a full migration, including checking out files from the origin, deleting excluded
     * files, transforming the code, and writing to the destination. This writes to the destination
     * exactly once.
     *
     * @param rev revision to the version which will be written to the destination
     * @param lastRev last revision that was migrated
     * @param processConsole console to use to print progress messages
     * @param metadata metadata of the change to be migrated
     * @param changes changes included in this migration
     * @param destinationBaseline it not null, use this baseline in the destination
     * @param changeIdentityRevision the revision to be used for computing the change identity
     */
    final ImmutableList<DestinationEffect> migrate(
        O rev,
        @Nullable O lastRev,
        Console processConsole,
        Metadata metadata,
        Changes changes,
        @Nullable Baseline<O> destinationBaseline,
        @Nullable O changeIdentityRevision)
        throws IOException, RepoException, ValidationException {
      ImmutableList<DestinationEffect> effects = ImmutableList.of();
      try {
        workflow.eventMonitor().onChangeMigrationStarted(new ChangeMigrationStartedEvent());
        effects =
            doMigrate(
                rev, lastRev, processConsole, metadata, changes, destinationBaseline,
                changeIdentityRevision);
      } catch (EmptyChangeException empty) {
        effects =
            ImmutableList.of(
                new DestinationEffect(
                    Type.NOOP,
                    empty.getMessage(),
                    changes.getCurrent(),
                    /*destinationRef=*/ null));
        throw empty;
      } catch (ValidationException | IOException | RepoException | RuntimeException e) {
        boolean userError = e instanceof ValidationException;
        effects =
            ImmutableList.of(
                new DestinationEffect(
                    userError ? Type.ERROR : Type.TEMPORARY_ERROR,
                    "Errors happened during the migration",
                    changes.getCurrent(),
                    /*destinationRef=*/ null,
                    ImmutableList.of(e.getMessage() != null ? e.getMessage() : e.toString())));
        throw e;
      } finally {
        try {
          boolean dryRunModeFlag = workflow.getGeneralOptions().dryRunMode;
          if (!dryRunModeFlag) {
            SkylarkConsole console = new SkylarkConsole(workflow.getConsole());
            try (ProfilerTask ignored = profiler().start("finish_hooks")) {
              List<DestinationEffect> hookDestinationEffects = new ArrayList<>();
              // Only do this once for all the actions
              LazyResourceLoader<Endpoint> originEndpoint =
                  LazyResourceLoader.memoized(reader::getFeedbackEndPoint);
              LazyResourceLoader<Endpoint> destinationEndpoint =
                  LazyResourceLoader.memoized(writer::getFeedbackEndPoint);
              for (Action action : workflow.getAfterMigrationActions()) {
                try (ProfilerTask ignored2 = profiler().start(action.getName())) {
                  logger.log(Level.INFO, "Running after migration hook: " + action.getName());
                  FinishHookContext context =
                      new FinishHookContext(
                          action,
                          originEndpoint,
                          destinationEndpoint,
                          ImmutableList.copyOf(effects),
                          resolvedRef,
                          console);
                  action.run(context);
                  hookDestinationEffects.addAll(context.getNewDestinationEffects());
                }
              }
              effects =
                  ImmutableList.<DestinationEffect>builder()
                      .addAll(effects)
                      .addAll(hookDestinationEffects)
                      .build();
            }
          } else if (dryRunModeFlag && !workflow.getAfterMigrationActions().isEmpty()) {
            workflow.getConsole()
                .infoFmt(
                    "Not calling 'after_migration' actions because of %s mode",
                    GeneralOptions.DRY_RUN_FLAG);
          }
        } finally {
          workflow.eventMonitor()
              .onChangeMigrationFinished(new ChangeMigrationFinishedEvent(effects));
        }
      }
      return effects;
    }

    private ImmutableList<DestinationEffect> doMigrate(
        O rev,
        @Nullable O lastRev,
        Console processConsole,
        Metadata metadata,
        Changes changes,
        @Nullable Baseline<O> destinationBaseline,
        @Nullable O changeIdentityRevision)
        throws IOException, RepoException, ValidationException {
      Path checkoutDir = workdir.resolve("checkout");
      try (ProfilerTask ignored = profiler().start("prepare_workdir")) {
        processConsole.progress("Cleaning working directory");
        if (Files.exists(workdir)) {
          FileUtil.deleteRecursively(workdir);
        }
        Files.createDirectories(checkoutDir);
      }
      processConsole.progress("Checking out the change");

      try (ProfilerTask ignored = profiler().start(
          "origin.checkout", profiler().taskType(workflow.getOrigin().getType()))) {
        reader.checkout(rev, checkoutDir);
      }

      // Remove excluded origin files.
      PathMatcher originFiles = getOriginFiles().relativeTo(checkoutDir);
      processConsole.progress("Removing excluded origin files");

      int deleted = FileUtil.deleteFilesRecursively(
          checkoutDir, FileUtil.notPathMatcher(originFiles));
      if (deleted != 0) {
        processConsole.infoFmt(
            "Removed %d files from workdir that do not match origin_files", deleted);
      }

      Path originCopy = null;
      if (getReverseTransformForCheck() != null) {
        try (ProfilerTask ignored = profiler().start("reverse_copy")) {
          workflow.getConsole().progress("Making a copy or the workdir for reverse checking");
          originCopy = Files.createDirectories(workdir.resolve("origin"));
          FileUtil.copyFilesRecursively(checkoutDir, originCopy, FAIL_OUTSIDE_SYMLINKS);
        }
      }

      TransformWork transformWork =
          new TransformWork(
              checkoutDir,
              metadata,
              changes,
              workflow.getConsole(),
              new MigrationInfo(workflow.getOrigin().getLabelName(), writer),
              resolvedRef,
              /*ignoreNoop=*/ false)
              .withLastRev(lastRev)
              .withCurrentRev(rev);
      try (ProfilerTask ignored = profiler().start("transforms")) {
        getTransformation().transform(transformWork);
      }

      if (getReverseTransformForCheck() != null) {
        workflow.getConsole().progress("Checking that the transformations can be reverted");
        Path reverse;
        try (ProfilerTask ignored = profiler().start("reverse_copy")) {
          reverse = Files.createDirectories(workdir.resolve("reverse"));
          FileUtil.copyFilesRecursively(checkoutDir, reverse, FAIL_OUTSIDE_SYMLINKS);
        }

        try (ProfilerTask ignored = profiler().start("reverse_transform")) {
          getReverseTransformForCheck()
              .transform(
                  new TransformWork(
                      reverse,
                      transformWork.getMetadata(),
                      changes,
                      workflow.getConsole(),
                      new MigrationInfo(/*originLabel=*/ null, null),
                      resolvedRef,
                      /*ignoreNoop=*/ false));
        }
        String diff;
        try {
          diff = new String(DiffUtil.diff(originCopy, reverse, workflow.isVerbose(),
              workflow.getGeneralOptions().getEnvironment()),
              StandardCharsets.UTF_8);
        } catch (InsideGitDirException e) {
          throw new ValidationException(
              "Cannot use 'reversible_check = True' because Copybara temporary directory (%s) is"
                  + " inside a git directory (%s). Please remove the git repository or use %s"
                  + " flag.",
              e.getPath(), e.getGitDirPath(), OUTPUT_ROOT_FLAG);
        }
        if (!diff.trim().isEmpty()) {
          workflow.getConsole().error("Non reversible transformations:\n"
              + DiffUtil.colorize(workflow.getConsole(), diff));
          throw new ValidationException("Workflow '%s' is not reversible", workflow.getName());
        }
      }

      workflow.getConsole()
          .progress("Checking that destination_files covers all files in transform result");
      new ValidateDestinationFilesVisitor(getDestinationFiles(), checkoutDir)
          .verifyFilesToWrite();

      // TODO(malcon): Pass metadata object instead
      TransformResult transformResult =
          new TransformResult(
              checkoutDir,
              rev,
              transformWork.getAuthor(),
              transformWork.getMessage(),
              resolvedRef,
              workflow.getName(),
              changes,
              rawSourceRef,
              workflow.isSetRevId(),
              transformWork::getAllLabels);

      if (destinationBaseline != null) {
        transformResult = transformResult.withBaseline(destinationBaseline.getBaseline());
        if (workflow.isSmartPrune() && workflow.getWorkflowOptions().canUseSmartPrune()) {
          ValidationException.checkCondition(destinationBaseline.getOriginRevision() != null,
              "smart_prune is not compatible with %s flag for now",
              WorkflowOptions.CHANGE_REQUEST_PARENT_FLAG);
          Path baselineWorkdir = Files.createDirectories(workdir.resolve("baseline"));
          reader.checkout(destinationBaseline.getOriginRevision(), baselineWorkdir);
          TransformWork baselineTransformWork =
              new TransformWork(
                  baselineWorkdir,
                  // We don't care about the message or author and this guarantees that it will
                  // work with the transformations
                  metadata,
                  // We don't care about the changes that are imported.
                  changes,
                  new ProgressPrefixConsole("Migrating baseline for diff: ", workflow.getConsole()),
                  new MigrationInfo(workflow.getOrigin().getLabelName(), writer),
                  resolvedRef,
                  // Doesn't guarantee that we will not run a ignore_noop = False core.transform but
                  // reduces the chances.
                  /*ignoreNoop=*/true)
                  // Again, we don't care about this
                  .withLastRev(lastRev)
                  .withCurrentRev(destinationBaseline.getOriginRevision());
          try (ProfilerTask ignored = profiler().start("baseline_transforms")) {
            getTransformation().transform(baselineTransformWork);
          }
          try {
            ImmutableList<DiffFile> affectedFiles = DiffUtil
                .diffFiles(baselineWorkdir, checkoutDir, workflow.getGeneralOptions().isVerbose(),
                    workflow.getGeneralOptions().getEnvironment());
            transformResult = transformResult.withAffectedFilesForSmartPrune(affectedFiles);
          } catch (InsideGitDirException e) {
            throw new ValidationException("Error computing diff for smart_prune: " + e.getMessage(),
                e.getCause());
          }
        }
      }
      transformResult = transformResult
          .withAskForConfirmation(workflow.isAskForConfirmation())
          .withIdentity(workflow.getMigrationIdentity(changeIdentityRevision, transformWork));

      ImmutableList<DestinationEffect> result;
      try (ProfilerTask ignored = profiler().start(
          "destination.write", profiler().taskType(workflow.getDestination().getType()))) {
        result = writer.write(transformResult, getDestinationFiles(), processConsole);
      }
      Verify.verifyNotNull(result, "Destination returned a null result.");
      Verify
          .verify(!result.isEmpty(), "Destination " + writer + " returned an empty set of effects");
      return result;
    }
  }

  /**
   * Get last imported revision or fail if it cannot be found.
   *
   * @throws RepoException if a last revision couldn't be found
   */
  @Nullable
  O getLastRev() throws RepoException, ValidationException {
    O lastRev = maybeGetLastRev();
    if (lastRev == null && !isInitHistory()) {
      throw new CannotResolveRevisionException(String.format(
          "Previous revision label %s could not be found in %s and --last-rev or --init-history "
              + "flags were not passed",
          getOriginLabelName(), workflow.getDestination()));
    }
    return lastRev;
  }

  String getOriginLabelName() {
    return workflow.getOrigin().getLabelName();
  }

  /**
   * Returns the last revision that was imported from this origin to the destination. Returns
   * {@code null} if it cannot be determined.
   *
   * <p>If {@code --last-rev} is specified, that revision will be used. Otherwise, the previous
   * revision will be resolved in the destination with the origin label.
   *
   * <p>If {@code --init-history} it will return null, if a last revision cannot be resolved in the
   * destination. The reason is to avoid users importing accidentally all the history again by using
   * the flag when they shouldn't.
   */
  @Nullable
  private O maybeGetLastRev() throws RepoException, ValidationException {
    if (workflow.getLastRevisionFlag() != null) {
      try {
        return originResolve(workflow.getLastRevisionFlag());
      } catch (RepoException e) {
        throw new CannotResolveRevisionException(
            "Could not resolve --last-rev flag. Please make sure it exists in the origin: "
                + workflow.getLastRevisionFlag(), e);
      }
    }
    DestinationStatus status = writer.getDestinationStatus(
        workflow.getDestinationFiles(),
        getOriginLabelName());
    try {
      O lastRev = (status == null) ? null : originResolve(status.getBaseline());
      if (lastRev != null && workflow.isInitHistory()) {
        getConsole().warnFmt(
            "Ignoring %s because a previous imported revision '%s' was found in the destination.",
            WorkflowOptions.INIT_HISTORY_FLAG, lastRev.asString());
      }
      return lastRev;
    } catch (CannotResolveRevisionException e) {
      if (workflow.isInitHistory()) {
        // Expected to not find a revision if --init-history is provided
        return null;
      }
      throw e;
    }
  }

  /**
   * Resolve a string representation of a revision using the origin
   */
   O originResolve(String revStr) throws RepoException, ValidationException {
    return workflow.getOrigin().resolve(revStr);
  }


  public EventMonitor eventMonitor() {
    return workflow.eventMonitor();
  }

}
