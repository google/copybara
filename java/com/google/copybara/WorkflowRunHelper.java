/*
 * Copyright (C) 2017 Google LLC
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;
import static com.google.copybara.GeneralOptions.OUTPUT_ROOT_FLAG;
import static com.google.copybara.TransformWork.COPYBARA_CONFIG_PATH_LABEL;
import static com.google.copybara.TransformWork.COPYBARA_WORKFLOW_NAME_LABEL;
import static com.google.copybara.exception.ValidationException.checkCondition;
import static com.google.copybara.util.FileUtil.CopySymlinkStrategy.FAIL_OUTSIDE_SYMLINKS;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.io.MoreFiles;
import com.google.copybara.Destination.DestinationStatus;
import com.google.copybara.Destination.Writer;
import com.google.copybara.Origin.Baseline;
import com.google.copybara.Origin.Reader;
import com.google.copybara.Origin.Reader.ChangesResponse;
import com.google.copybara.TransformWork.ResourceSupplier;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.effect.DestinationEffect;
import com.google.copybara.effect.DestinationEffect.DestinationRef;
import com.google.copybara.effect.DestinationEffect.Type;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.ChangeRejectedException;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.RedundantChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.exception.VoidOperationException;
import com.google.copybara.monitor.EventMonitor.ChangeMigrationFinishedEvent;
import com.google.copybara.monitor.EventMonitor.ChangeMigrationStartedEvent;
import com.google.copybara.monitor.EventMonitor.EventMonitors;
import com.google.copybara.profiler.Profiler;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.revision.Change;
import com.google.copybara.revision.Changes;
import com.google.copybara.revision.Revision;
import com.google.copybara.util.ApplyDestinationPatch;
import com.google.copybara.util.AutoPatchUtil;
import com.google.copybara.util.CommandLineDiffUtil;
import com.google.copybara.util.ConsistencyFile;
import com.google.copybara.util.DiffUtil;
import com.google.copybara.util.DiffUtil.DiffFile;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.FileUtil.CopySymlinkStrategy;
import com.google.copybara.util.Glob;
import com.google.copybara.util.InsideGitDirException;
import com.google.copybara.util.MergeImportTool;
import com.google.copybara.util.MergeImportTool.MergeRunner;
import com.google.copybara.util.console.AnsiColor;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.PrefixConsole;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.re2j.Pattern;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Runs a single migration step for a {@link Workflow}, using its configuration.
 */
public class WorkflowRunHelper<O extends Revision, D extends Revision> {

  private final Workflow<O, D> workflow;
  private final Path workdir;
  private final O resolvedRef;
  private final Origin.Reader<O> originReader;
  protected final Destination.Writer<D> writer;
  @Nullable
  final String rawSourceRef;
  private final Consumer<ChangeMigrationFinishedEvent> migrationFinishedMonitor;

  public WorkflowRunHelper(
      Workflow<O, D> workflow,
      Path workdir,
      O resolvedRef,
      Reader<O> originReader,
      Writer<D> destinationWriter,
      @Nullable String rawSourceRef,
      Consumer<ChangeMigrationFinishedEvent> migrationFinishedMonitor) {
    this.workflow = checkNotNull(workflow);
    this.workdir = checkNotNull(workdir);
    this.resolvedRef = checkNotNull(resolvedRef);
    this.originReader = checkNotNull(originReader);
    this.writer = checkNotNull(destinationWriter);
    this.rawSourceRef = rawSourceRef;
    this.migrationFinishedMonitor = checkNotNull(migrationFinishedMonitor);
  }

  public Consumer<ChangeMigrationFinishedEvent> getMigrationFinishedMonitor() {
    return migrationFinishedMonitor;
  }

  /**
   * origin_files used for this workflow
   */
  protected Glob getOriginFiles() {
    return workflow.getOriginFiles();
  }

  ChangeMigrator<O, D> getMigratorForChange(Change<?> change)
      throws RepoException, ValidationException {
    return getMigratorForChangeAndWriter(change, writer);
  }

  ChangeMigrator<O, D> getMigratorForChangeAndWriter(Change<?> change, Writer<D> writer)
      throws ValidationException, RepoException {
    return new ChangeMigrator<>(workflow, workdir, originReader, writer, resolvedRef, rawSourceRef,
        migrationFinishedMonitor);
  }

  /**
   * Get a default migrator for the current writer
   */
  ChangeMigrator<O, D> getDefaultMigrator() {
    return new ChangeMigrator<>(workflow, workdir, originReader, writer, resolvedRef, rawSourceRef,
        migrationFinishedMonitor);
  }

  public Profiler profiler() {
    return workflow.profiler();
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

  String getChangeMessage(String message) {
    return workflow.getWorkflowOptions().forcedChangeMessage == null
        ? message
        : workflow.getWorkflowOptions().forcedChangeMessage;
  }

  public Author getFinalAuthor(Author author) {
    return workflowOptions().forcedAuthor == null ? author : workflowOptions().forcedAuthor;
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

  Destination<?> getDestination() {
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
              // Create a new writer so that state is not shared with the regular writer.
              // The current writer might have state from previous migrations, etc.
              ChangeMigrator<O, D> migrator =
                  getMigratorForChangeAndWriter(change, workflow.createDryRunWriter(resolvedRef));

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
                                new PrefixConsole(
                                    "Validating last migration: ", workflow.getConsole()),
                                metadata == null
                                    ? new Metadata(
                                    change.getMessage(),
                                    change.getAuthor(),
                                    ImmutableSetMultimap.of())
                                    : metadata,
                                changes,
                                /*destinationBaseline=*/ null,
                                lastRev,
                                null));
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
    private final Consumer<ChangeMigrationFinishedEvent> migrationFinishedMonitor;

    ChangeMigrator(Workflow<O, D> workflow, Path workdir, Reader<O> reader,
        Writer<D> writer, O resolvedRef, @Nullable String rawSourceRef,
        Consumer<ChangeMigrationFinishedEvent> migrationFinishedMonitor) {
      this.workflow = checkNotNull(workflow);
      this.workdir = checkNotNull(workdir);
      this.resolvedRef = checkNotNull(resolvedRef);
      this.reader = checkNotNull(reader);
      this.writer = checkNotNull(writer);
      this.rawSourceRef = rawSourceRef;
      this.migrationFinishedMonitor = checkNotNull(migrationFinishedMonitor);
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

    protected Glob getReversibleCheckIgnoreFiles() {
      return workflow.getReversibleCheckIgnoreFiles();
    }

    public final Profiler profiler() {
      return workflow.profiler();
    }

    // provide the correct context reference when the --same-version flag is used
    private O getResolvedRefForTransform(O rev) {
      if (workflow.getMode() == WorkflowMode.SQUASH
          && workflow.getWorkflowOptions().importSameVersion) {
        return rev;
      }
      return resolvedRef;
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
     * @param originBaselineForMergeImport the revision to populate baseline for merge_import mode
     */
    @CanIgnoreReturnValue
    @SuppressWarnings("Finally")
    public final ImmutableList<DestinationEffect> migrate(
        O rev,
        @Nullable O lastRev,
        Console processConsole,
        Metadata metadata,
        Changes changes,
        @Nullable Baseline<O> destinationBaseline,
        @Nullable O changeIdentityRevision,
        @Nullable O originBaselineForMergeImport)
        throws IOException, RepoException, ValidationException {
      ImmutableList<DestinationEffect> effects = ImmutableList.of();
      Exception lastException = null;
      try {
        workflow.eventMonitors().dispatchEvent(
            m -> m.onChangeMigrationStarted(new ChangeMigrationStartedEvent()));
        effects =
            doMigrate(
                rev,
                lastRev,
                processConsole,
                metadata,
                changes,
                destinationBaseline,
                changeIdentityRevision,
                originBaselineForMergeImport);
      } catch (RedundantChangeException e) {
        effects =
            ImmutableList.of(
                new DestinationEffect(
                    Type.NOOP_AGAINST_PENDING_CHANGE,
                    String.format(
                        "Cannot migrate revisions [%s]: %s",
                        changes.getCurrent().isEmpty()
                            ? "Unknown"
                            : Joiner.on(", ")
                                .join(
                                    changes.getCurrent().stream()
                                        .map(c -> c.getRevision().asString())
                                        .iterator()),
                        e.getMessage()),
                    changes.getCurrent(),
                    new DestinationRef(e.getPendingRevision(), "commit", /*url=*/ null)));
        lastException = e;
        throw e;
      } catch (EmptyChangeException empty) {
        effects =
            ImmutableList.of(
                new DestinationEffect(
                    Type.NOOP,
                    String.format(
                        "Cannot migrate revisions [%s]: %s",
                        changes.getCurrent().isEmpty()
                            ? "Unknown"
                            : Joiner.on(", ")
                                .join(
                                    changes.getCurrent().stream()
                                        .map(c -> c.getRevision().asString())
                                        .iterator()),
                        empty.getMessage()),
                    changes.getCurrent(),
                    /* destinationRef= */ null));
        lastException = empty;
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
        lastException = e;
        throw e;
      } finally {
        try {
          if (!workflow.getGeneralOptions().dryRunMode) {
            try (ProfilerTask ignored = profiler().start("after_migration")) {
              effects = workflow.runHooks(effects, workflow.getAfterMigrationActions(),
                  // Only do this once for all the actions
                  LazyResourceLoader.memoized(reader::getFeedbackEndPoint),
                  // Only do this once for all the actions
                  LazyResourceLoader.memoized(writer::getFeedbackEndPoint),
                  resolvedRef);
            } catch (ValidationException | RepoException e) {
              if (lastException != null) {
                lastException.addSuppressed(e);
              } else {
                throw e;
              }
            }
          } else if (!workflow.getAfterMigrationActions().isEmpty()) {
            workflow.getConsole()
                .infoFmt(
                    "Not calling 'after_migration' actions because of %s mode",
                    GeneralOptions.DRY_RUN_FLAG);
          }
        } finally {
          migrationFinishedMonitor.accept(new ChangeMigrationFinishedEvent(effects,
              workflow.getOriginDescription(), workflow.getDestinationDescription()));
        }
      }
      return effects;
    }

    /**
     * Finish a migrate by noticing event monitor with the outcome effects
     *
     * @param effects The destination effect of the migration
     */
    final void finishedMigrate(ImmutableList<DestinationEffect> effects) {
      workflow.eventMonitors().dispatchEvent(
          m -> m.onChangeMigrationStarted(new ChangeMigrationStartedEvent()));
      migrationFinishedMonitor.accept(new ChangeMigrationFinishedEvent(effects,
          workflow.getOriginDescription(), workflow.getDestinationDescription()));
    }

    private boolean showDiffInOrigin(O rev, @Nullable O lastRev, Console processConsole)
        throws RepoException, ValidationException {
      if (!workflow.getWorkflowOptions().diffInOrigin
          || workflow.getMode() == WorkflowMode.CHANGE_REQUEST
          || workflow.getMode() == WorkflowMode.CHANGE_REQUEST_FROM_SOT
          || lastRev == null) {
        return false;
      }
      String diff = workflow.getOrigin().showDiff(lastRev, rev);
      if (diff == null) {
        throw new ValidationException("diff_in_origin is not supported by origin "
            + workflow.getOrigin().getType());
      }
      if (diff.isEmpty() && !workflow.getGeneralOptions().force) {
        throw new EmptyChangeException("No difference at diff_in_origin");
      }
      StringBuilder sb = new StringBuilder();
      for (String line : Splitter.on('\n').split(diff)) {
        sb.append("\n");
        if (line.startsWith("+")) {
          sb.append(processConsole.colorize(AnsiColor.GREEN, line));
        } else if (line.startsWith("-")) {
          sb.append(processConsole.colorize(AnsiColor.RED, line));
        } else {
          sb.append(line);
        }
      }
      processConsole.info(sb.toString());
      if (!processConsole.promptConfirmation(String.format("Continue to migrate with '%s' to "
          + "%s?", workflow.getMode(), workflow.getDestination().getType()))) {
        processConsole.warn("Migration aborted by user.");
        throw new ChangeRejectedException(
            "User aborted execution: did not confirm diff in origin changes.");
      }
      return true;
    }

    private ImmutableList<DestinationEffect> doMigrate(
        O rev,
        @Nullable O lastRev,
        Console processConsole,
        Metadata metadata,
        Changes changes,
        @Nullable Baseline<O> destinationBaseline,
        @Nullable O changeIdentityRevision,
        @Nullable O originBaselineForPrune)
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
      boolean isShowDiffInOrigin = showDiffInOrigin(rev, lastRev, processConsole);

      checkout(rev, processConsole, checkoutDir, "origin.checkout");

      Path originCopy = null;
      Console console = workflow.getConsole();
      if (getReverseTransformForCheck() != null) {
        try (ProfilerTask ignored = profiler().start("reverse_copy")) {
          console.progress("Making a copy or the workdir for reverse checking");
          originCopy = Files.createDirectories(workdir.resolve("origin"));
          try {
            FileUtil.copyFilesRecursively(checkoutDir, originCopy, FAIL_OUTSIDE_SYMLINKS);
          } catch (NoSuchFileException e) {
            throw new ValidationException(String.format(""
                + "Failed to perform reversible check of transformations due to symlink '%s' "
                + "that points outside the checkout dir. Consider removing this symlink from "
                + "your origin_files or, alternatively, set reversible_check = False in your "
                + "workflow.", e.getFile()), e
            );
          }
        }
      }
      // Lazy loading to avoid running afoul of checks unless the instance is actually used.
      LazyResourceLoader<Endpoint> originApi = c -> reader.getFeedbackEndPoint(c);
      LazyResourceLoader<Endpoint> destinationApi = c -> writer.getFeedbackEndPoint(c);
      ResourceSupplier<DestinationReader> destinationReader = () ->
          writer.getDestinationReader(console, destinationBaseline, checkoutDir);
      metadata =
          metadata.withHiddenLabels(
              workflow.getGeneralOptions().cliLabels().entrySet().stream()
                  .collect(
                      toImmutableListMultimap(
                          e -> GeneralOptions.CLI_FLAG_PREFIX + Ascii.toUpperCase(e.getKey()),
                          e -> e.getValue())));
      TransformWork transformWork =
          new TransformWork(
                  checkoutDir,
                  metadata,
                  changes,
                  console,
                  new MigrationInfo(workflow.getRevIdLabel(), writer),
                  getResolvedRefForTransform(rev),
                  originApi,
                  destinationApi,
                  destinationReader)
              .withLastRev(lastRev)
              .withCurrentRev(rev)
              .withDestinationInfo(writer.getDestinationInfo());
      transformWork.addLabel(COPYBARA_CONFIG_PATH_LABEL,
          workflow.getMainConfigFile().getIdentifier(), "=", true);
      transformWork.addLabel(COPYBARA_WORKFLOW_NAME_LABEL, workflow.getName(), "=", true);

      try (ProfilerTask ignored = profiler().start("transforms")) {
        TransformationStatus status = getTransformation().transform(transformWork);
        if (status.isNoop()) {
          showInfoAboutNoop(console);
          status.throwException(console, workflow.getWorkflowOptions().ignoreNoop);
        }
      } catch (VoidOperationException e) {
        // This happens if an inner sequence throws noop as an exception.
        showInfoAboutNoop(console);
        throw e;
      }

      if (getReverseTransformForCheck() != null) {
        console.progress("Checking that the transformations can be reverted");
        Path reverse;
        try (ProfilerTask ignored = profiler().start("reverse_copy")) {
          reverse = Files.createDirectories(workdir.resolve("reverse"));
          try {
            FileUtil.copyFilesRecursively(checkoutDir, reverse, FAIL_OUTSIDE_SYMLINKS);
          } catch (NoSuchFileException e) {
            throw new ValidationException(""
                + "Failed to perform reversible check of transformations due to a symlink that "
                + "points outside the checkout dir. Consider removing this symlink from your "
                + "origin_files or, alternatively, set reversible_check = False in your "
                + "workflow.", e
            );
          }
        }

        try (ProfilerTask ignored = profiler().start("reverse_transform")) {
          TransformationStatus status =
              getReverseTransformForCheck()
                  .transform(
                      new TransformWork(
                              reverse,
                              transformWork.getMetadata(),
                              changes,
                              console,
                              new MigrationInfo(/* originLabel= */ null, null),
                              getResolvedRefForTransform(rev),
                              destinationApi,
                              originApi,
                              () -> DestinationReader.NOT_IMPLEMENTED)
                          .withDestinationInfo(writer.getDestinationInfo()));
          if (status.isNoop()) {
            console.warnFmt("No-op detected running the transformations in reverse. The most"
                + " probably cause is that the transformations are not reversible.");
            status.throwException(console, workflow.getWorkflowOptions().ignoreNoop);
          }
        }
        String diff;
        try {
          byte[] byteDiff =
              DiffUtil.diff(
                  originCopy,
                  reverse,
                  workflow.isVerbose(),
                  workflow.getGeneralOptions().getEnvironment());

          // This should be more optimal than parsing a potential huge diff file.
          if (getReversibleCheckIgnoreFiles() != null) {
            PathMatcher pathMatcher =
                getReversibleCheckIgnoreFiles().relativeTo(Paths.get("origin"));
            diff = DiffUtil.filterDiff(byteDiff, (s -> !pathMatcher.matches(Paths.get(s))));
          } else {
            diff = new String(byteDiff, UTF_8);
          }
        } catch (InsideGitDirException e) {
          throw new ValidationException(String.format(
              "Cannot use 'reversible_check = True' because Copybara temporary directory (%s) is"
                  + " inside a git directory (%s). Please remove the git repository or use %s"
                  + " flag.", e.getPath(), e.getGitDirPath(), OUTPUT_ROOT_FLAG));
        }
        if (!diff.trim().isEmpty()) {
          console.errorFmt("Copybara detected non-reversible transformations. This is detected"
              + " by running the transformations forward and then reversing them. The result was"
              + " a non-empty diff (If transformations were reversible, the diff should be none):\n"
              + "%s\n"
              + "Reversible workflows are recommended so that workflows can run in both"
              + " directions. For example for upstreaming internal changes. This feature can"
              + " be deactivated by setting core.workflow(..., reversible_check = False)"
              + " field.", DiffUtil.colorize(console, diff));
          throw new ValidationException(
              String.format("Workflow '%s' is not reversible", workflow.getName()));
        }
      }

      console
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
                  /* requestedRevision= */ getResolvedRefForTransform(rev),
                  workflow.getName(),
                  changes,
                  rawSourceRef,
                  workflow.isSetRevId(),
                  transformWork::getAllLabels,
                  workflow.getRevIdLabel())
              .withDestinationInfo(transformWork.getDestinationInfo());

      ImmutableList<String> mergeErrorPaths = null;
      if (workflow.isMergeImport()) {
        mergeErrorPaths =
            runMergeImport(
                console,
                writer,
                destinationBaseline,
                checkoutDir,
                lastRev,
                metadata,
                originBaselineForPrune,
                originApi,
                destinationApi,
                transformWork);
        if (mergeErrorPaths == null) {
          console.warn(
            "Unable to determine a baseline; disabling merge import. This is expected"
              + " if this is an initial import. Otherwise, you may have to provide a"
              + " baseline using --baseline-for-merge-import, or using an existing"
              + " consistency file.");
        }
      }
      if (mergeErrorPaths == null) {
        mergeErrorPaths = ImmutableList.of();
        if (workflow.getConsistencyFilePath() != null) {
          byte[] consistencyFileContents =
              ConsistencyFile.generateNoDiff(
                      checkoutDir, workflow.getDestination().getHashFunction())
                  .toBytes();
          Files.createDirectories(
              checkoutDir.resolve(workflow.getConsistencyFilePath()).getParent());
          Files.write(
              checkoutDir.resolve(workflow.getConsistencyFilePath()), consistencyFileContents);
        }
      }
      if (destinationBaseline != null) {
        transformResult = transformResult.withBaseline(destinationBaseline.getBaseline());
        if (workflow.isSmartPrune() && workflow.getWorkflowOptions().canUseSmartPrune()) {
          checkCondition(
              destinationBaseline.getOriginRevision() != null,
              "smart_prune is not compatible with %s flag for now",
              WorkflowOptions.CHANGE_REQUEST_PARENT_FLAG);
          Path baselineWorkdir =
              checkoutBaselineAndTransform(
                  "destination_baseline",
                  lastRev,
                  metadata,
                  destinationBaseline.getOriginRevision(),
                  console,
                  originApi,
                  destinationApi,
                  destinationReader);
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
      transformResult =
          transformResult
              .withAskForConfirmation(workflow.isAskForConfirmation())
              .withDiffInOrigin(isShowDiffInOrigin)
              .withIdentity(workflow.getMigrationIdentity(changeIdentityRevision, transformWork))
              .withApprovalsProvider(workflow.getOrigin().getApprovalsProvider());

      ImmutableList<DestinationEffect> result;
      try (ProfilerTask ignored = profiler().start(
          "destination.write", profiler().taskType(workflow.getDestination().getType()))) {
        result = writer.write(transformResult, getDestinationFiles(), processConsole);
      }
      Verify.verifyNotNull(result, "Destination returned a null result.");
      Verify
          .verify(!result.isEmpty(), "Destination " + writer + " returned an empty set of effects");

      if (!mergeErrorPaths.isEmpty()) {
        DestinationEffect mergeErrorDestinationEffect =
            new DestinationEffect(
                Type.CREATED,
                String.format("Found merge errors for paths: %s", mergeErrorPaths),
                ImmutableList.of(),
                new DestinationRef("merge_error", "merge_error", null));

        result =
            ImmutableList.<DestinationEffect>builder()
                .addAll(result)
                .add(mergeErrorDestinationEffect)
                .build();
      }
      return result;
    }

    /**
     * @return a list of paths that resulted in merge errors, or null if a
     * baseline could not be determined
     */
    private @Nullable ImmutableList<String> runMergeImport(
        Console console,
        Writer<?> writer,
        Baseline<?> destinationBaseline,
        Path checkoutDir,
        O lastRev,
        Metadata metadata,
        @Nullable O originBaselineForPrune,
        LazyResourceLoader<Endpoint> originApi,
        LazyResourceLoader<Endpoint> destinationApi,
        TransformWork transformWork)
        throws IOException, ValidationException, RepoException {
      DestinationReader reader = writer.getDestinationReader(console, destinationBaseline,
          checkoutDir);
      // get a glob excluding any patch files
      Glob patchlessDestinationFiles = patchlessDestinationFiles(workflow);

      Path destinationFilesWorkdir = Files.createDirectories(workdir.resolve("destination"));
      reader.copyDestinationFilesToDirectory(
          patchlessDestinationFiles, destinationFilesWorkdir);

      Path baselineWorkdir = null;
      if (workflow.getMergeImport().useConsistencyFile()
          && !workflow.disableConsistencyMergeImport()) {
        // If there is a consistency file, then use it.
        // Otherwise, fall back to baseline import.
        Path consistencyFileWorkdir =
            Files.createDirectories(workdir.resolve("currentConsistencyFile"));
        reader.copyDestinationFilesToDirectory(
            consistencyFileGlob(workflow), consistencyFileWorkdir);

        if (reader.exists(workflow.getConsistencyFilePath())) {
          try {
            @Nullable
            String consistencyFileVersion = reader.lastModified(workflow.getConsistencyFilePath());
            DestinationReader consistencyFileVersionReader = reader;
            if (consistencyFileVersion != null) {
              consistencyFileVersionReader =
                  writer.getDestinationReader(console, consistencyFileVersion, checkoutDir);
            }
            baselineWorkdir = checkoutConsistencyFileBaseline(consistencyFileVersionReader);
          } catch (UnsupportedOperationException e) {
            throw new ValidationException(
                "Destination does not support consistency file operations", e);
          }
        } else {
          // onboarding case, using import baseline
          console.info("No consistency file found. Generating new consistency file.");
          console.warn(
              "When enabling the consistency file along with Merge Import for a project that\n"
                  + "does not have one, it is recommended to:\n"
                  + "\n"
                  + "-   Not make any configuration changes apart from enabling the consistency"
                  + " file\n"
                  + "\n"
                  + "-   Import the same version as is already in the destination (through\n"
                  + "    `--same-version` or passing in the reference)\n"
                  + "\n"
                  + "Using Copybara in this way should generate the consistency file without\n"
                  + "introducing other changes. This should minimize the amount of review"
                  + " required\n"
                  + "for the resulting destination change. Autopatch generation is especially\n"
                  + "recommended for reviewing the destination-specific patches.");
        }
      }

      if (baselineWorkdir == null) {
        if (originBaselineForPrune == null) {
          return null;
        }
        baselineWorkdir =
            checkoutBaselineAndTransform(
                "baseline",
                lastRev,
                metadata,
                originBaselineForPrune,
                console,
                originApi,
                destinationApi,
                () -> reader);
      }

      Path preMergeImportWorkdir = Files.createDirectories(workdir.resolve("premerge"));
      FileUtil.copyFilesRecursively(
          checkoutDir,
          preMergeImportWorkdir,
          CopySymlinkStrategy.IGNORE_INVALID_SYMLINKS,
          Glob.ALL_FILES);
      Pattern debugPattern =
          workflow.getWorkflowOptions().debugMergeImport != null
              ? Pattern.compile(workflow.getWorkflowOptions().debugMergeImport)
              : null;
      MergeRunner mergeRunner =
          new CommandLineDiffUtil(
              workflow.getGeneralOptions().getDiffBin(),
              workflow.getGeneralOptions().getEnvironment(),
              debugPattern);
      if (workflow.getGeneralOptions().isTemporaryFeature("use_patch_merge", false)
          || workflow.getMergeImport().mergeStrategy()
              == MergeImportConfiguration.MergeStrategy.PATCH_MERGE) {
        mergeRunner =
            new ApplyDestinationPatch(
                console,
                workflow.getGeneralOptions().patchBin,
                workflow.getGeneralOptions().getEnvironment());
      }

      MergeImportTool mergeImportTool =
          new MergeImportTool(
              console,
              mergeRunner,
              workflow.getWorkflowOptions().threadsForMergeImport,
              debugPattern);
      ImmutableList<String> mergeErrorPaths;
      try (ProfilerTask ignored = profiler().start("merge_tool")) {
        mergeErrorPaths =
            mergeImportTool.mergeImport(
                checkoutDir,
                destinationFilesWorkdir,
                baselineWorkdir,
                Files.createDirectories(workdir.resolve("merge_import")),
                workflow.getMergeImport().paths(),
                Path.of(workflow.getMergeImport().packagePath()));
      }
      try (ProfilerTask ignore = profiler().start("after_merge_transformations")) {
        workflow.afterMergeTransformations.transform(transformWork);
      }

      Optional<byte[]> consistencyFile = Optional.empty();
      if (workflow.isConsistencyFileMergeImport()) {
        try {
          consistencyFile =
              Optional.of(
                  ConsistencyFile.generate(
                          preMergeImportWorkdir,
                          checkoutDir,
                          workflow.getDestination().getHashFunction(),
                          workflow.getGeneralOptions().getEnvironment(),
                          workflow.isVerbose())
                      .toBytes());
        } catch (InsideGitDirException e) {
          throw new ValidationException("Error generating consistency file", e);
        }
      }

      if (workflow.getAutoPatchfileConfiguration() != null) {
        try {
          AutoPatchUtil.generatePatchFiles(
              preMergeImportWorkdir == null ? baselineWorkdir : preMergeImportWorkdir,
              preMergeImportWorkdir == null ? destinationFilesWorkdir : checkoutDir,
              Path.of(workflow.getAutoPatchfileConfiguration().directoryPrefix()),
              workflow.getAutoPatchfileConfiguration().directory(),
              workflow.isVerbose(),
              workflow.getGeneralOptions().getEnvironment(),
              workflow.getAutoPatchfileConfiguration().header(),
              workflow.getAutoPatchfileConfiguration().suffix(),
              checkoutDir,
              workflow.getAutoPatchfileConfiguration().stripFilenames(),
              workflow.getAutoPatchfileConfiguration().stripLineNumbers(),
              workflow.getAutoPatchfileConfiguration().glob());
        } catch (InsideGitDirException e) {
          console.errorFmt(
              "Could not automatically generate patch files. Error received is %s", e.getMessage());
          throw new ValidationException("Error automatically generating patch files", e);
        }
      }
      // Write the ConsistencyFile after auto patches are generated so that it does not get
      // included in the auto patches.
      if (workflow.getConsistencyFilePath() != null && consistencyFile.isPresent()) {
        Files.createDirectories(checkoutDir.resolve(workflow.getConsistencyFilePath()).getParent());
        Files.write(checkoutDir.resolve(workflow.getConsistencyFilePath()), consistencyFile.get());
      }
      return mergeErrorPaths;
    }

    static Glob patchlessDestinationFiles(
        Workflow<? extends Revision, ? extends Revision> workflow) {
      Glob destinationFiles = workflow.getDestinationFiles();
      AutoPatchfileConfiguration autoPatchfileConfiguration =
          workflow.getAutoPatchfileConfiguration();
      if (autoPatchfileConfiguration != null) {
        destinationFiles = Glob.difference(destinationFiles, AutoPatchUtil.getAutopatchGlob(
            autoPatchfileConfiguration.directoryPrefix(), autoPatchfileConfiguration.directory()));
      }
      if (workflow.getMergeImport().useConsistencyFile()) {
        destinationFiles =
            Glob.difference(
                destinationFiles,
                Glob.createGlob(ImmutableList.of(workflow.getConsistencyFilePath())));
      }
      return destinationFiles;
    }

    static Glob consistencyFileGlob(Workflow<? extends Revision, ? extends Revision> workflow) {
      return Glob.createGlob(ImmutableList.of(workflow.getConsistencyFilePath()));
    }

    private Path checkoutConsistencyFileBaseline(DestinationReader reader)
        throws ValidationException, IOException, RepoException {
      Path consistencyFileWorkdir = Files.createDirectories(workdir.resolve("consistencyFile"));
      Path consistencyFilePath = consistencyFileWorkdir.resolve(workflow.getConsistencyFilePath());

      // copy the consistency file somewhere so we can parse it
      reader.copyDestinationFilesToDirectory(consistencyFileGlob(workflow), consistencyFileWorkdir);
      ConsistencyFile consistencyFile =
          ConsistencyFile.fromBytes(Files.readAllBytes(consistencyFilePath));

      // copy the current destination files to the baseline directory
      Glob baselineFiles = Glob.createSingleFilesGlob(consistencyFile.getFileHashes().keySet().asList());

      Path baselineWorkdir = Files.createDirectories(workdir.resolve("baseline"));
      reader.copyDestinationFilesToDirectory(baselineFiles, baselineWorkdir);

      ConsistencyFile.HashGetter hashGetter;
      if (reader.supportsGetHash()) {
        hashGetter = reader::getHash;
      } else {
        var hashFunction = workflow.getDestination().getHashFunction();
        hashGetter = (String path) -> {
          return MoreFiles.asByteSource(baselineWorkdir.resolve(path)).hash(hashFunction).toString();
        };
      }
      consistencyFile.validateDirectory(
          ConsistencyFile.filesInDir(baselineWorkdir), hashGetter);
      consistencyFile.reversePatches(
          baselineWorkdir, workflow.getGeneralOptions().getEnvironment());

      return baselineWorkdir;
    }

    private Path checkoutBaselineAndTransform(
        String subdirName,
        O lastRev,
        Metadata metadata,
        O baseline,
        Console console,
        LazyResourceLoader<Endpoint> originApi,
        LazyResourceLoader<Endpoint> destinationApi,
        ResourceSupplier<DestinationReader> destinationReader)
        throws IOException, RepoException, ValidationException {
      Path baselineWorkdir = Files.createDirectories(workdir.resolve(subdirName));

      PrefixConsole baselineConsole = new PrefixConsole("Migrating baseline for diff: ", console);
      checkout(baseline, baselineConsole, baselineWorkdir, "origin.baseline.checkout");

      TransformWork baselineTransformWork =
          new TransformWork(
              baselineWorkdir,
              // We don't care about the message or author and this guarantees that it will
              // work with the transformations
              metadata,
              // We don't care about the changes that are imported.
              Changes.EMPTY,
              baselineConsole,
              new MigrationInfo(workflow.getRevIdLabel(), writer),
              resolvedRef,
              originApi,
              destinationApi,
              destinationReader)
              // Again, we don't care about this
              .withLastRev(lastRev)
              .withCurrentRev(baseline)
              .withDestinationInfo(writer.getDestinationInfo());
      try (ProfilerTask ignored = profiler().start("baseline_transforms")) {
        TransformationStatus status = getTransformation().transform(baselineTransformWork);
        if (status.isNoop()
            // no-op baseline transformations are OK for smart prune - smart prune works by
            // comparing file contents with DiffUtil, meaning it is OK if the file paths are not
            // exactly as expected (because a core.move()) transformation didn't run
            && !workflow.isSmartPrune()) {
          console.warnFmt("No-op detected in baseline transformations");
          showInfoAboutNoop(console);
          status.throwException(console, workflow.getWorkflowOptions().ignoreNoop);
        }
      }
      return baselineWorkdir;
    }

    private void showInfoAboutNoop(Console console) {
      console.warnFmt("No-op detected, this could happen for several reasons:\n\n"
              + "    - origin_files doesn't include the files. Current origin_files: %s\n\n"
              + "    - Previous transformations didn't do what you were expecting. You can"
              + " inspect the work directory state (if run locally) at %s\n\n"
              + "    - Current version of the config doesn't work for an older (or newer)"
              + " revision being migrated. This can be fixed by either wrapping the failing"
              + " transformation with %s"
              + " so that it is ignored or, if your origin supports it, using"
              + " %s flag to sync the config version to the change"
              + " being migrated.",
          console.colorize(AnsiColor.YELLOW, workflow.getOriginFiles().toString()),
          console.colorize(AnsiColor.YELLOW, workdir.toString()),
          console.colorize(AnsiColor.YELLOW,
              "core.transform([your_transformation], noop_behavior = \"IGNORE_NOOP\")"),
          console.colorize(AnsiColor.YELLOW, "--read-config-from-change"));
    }

    private void checkout(
        O rev, Console processConsole, Path checkoutDir, String profileDescription)
        throws RepoException, ValidationException, IOException {
      if (workflow.isCheckout()) {
        try (ProfilerTask ignored = profiler().start(
            profileDescription, profiler().taskType(workflow.getOrigin().getType()))) {
          reader.checkout(rev, checkoutDir);
        }
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
    }
  }

  public Path importAndTransformRevision(
      Console console,
      O lastRev,
      O currentRev,
      ResourceSupplier<DestinationReader> destinationReader)
      throws RepoException, ValidationException, IOException {

    ChangeMigrator<O, D> migrator = getDefaultMigrator();
    LazyResourceLoader<Endpoint> originApi = c -> getOriginReader().getFeedbackEndPoint(c);
    LazyResourceLoader<Endpoint> destinationApi =
        c -> getDestinationWriter().getFeedbackEndPoint(c);

    return migrator.checkoutBaselineAndTransform(
        "premerge",
        lastRev,
        new Metadata("foo", new Author("foo", "foo@foo.com"), ImmutableSetMultimap.of()),
        currentRev,
        console,
        originApi,
        destinationApi,
        destinationReader);
  }

  /**
   * Get last imported revision or fail if it cannot be found.
   *
   * @throws RepoException if a last revision couldn't be found
   */
  @Nullable
  public O getLastRev() throws RepoException, ValidationException {
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
    return workflow.getRevIdLabel();
  }

  String getLabelNameWhenOrigin() throws ValidationException {
    return workflow.customRevId() == null
        ? workflow.getDestination().getLabelNameWhenOrigin()
        : workflow.customRevId();
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
        return originResolveLastRev(workflow.getLastRevisionFlag());
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
      O lastRev = (status == null) ? null : originResolveLastRev(status.getBaseline());
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
      throw new CannotResolveRevisionException(
          String.format(
              "Latest destination change has value '%s' for label '%s', but this does not resolve"
                  + " in the origin. This commonly happens if changes were merged outside the"
                  + " Source of Truth, several copybara workflows use the same label or if the"
                  + " origin history was re-written. Manually set the '--last-rev' flag to the"
                  + " export baseline to export a valid state to the destination.",
              (status == null) ? null : status.getBaseline(), getOriginLabelName()),
          e);
    }
  }

  /**
   * Resolve a string representation of a revision using the origin
   */
  O originResolveLastRev(String revStr) throws RepoException, ValidationException {
    return workflow.getOrigin().resolveLastRev(revStr);
  }

  public EventMonitors eventMonitors() {
    return workflow.eventMonitors();
  }

}
