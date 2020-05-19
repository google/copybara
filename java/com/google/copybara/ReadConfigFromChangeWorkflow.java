/*
 * Copyright (C) 2018 Google Inc.
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

import static com.google.common.base.Joiner.on;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.copybara.exception.ValidationException.checkCondition;
import static java.lang.String.format;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.copybara.Destination.Writer;
import com.google.copybara.Origin.Reader;
import com.google.copybara.WorkflowRunHelper.ChangeMigrator;
import com.google.copybara.config.Config;
import com.google.copybara.config.ConfigValidator;
import com.google.copybara.config.Migration;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.monitor.EventMonitor.ChangeMigrationFinishedEvent;
import com.google.copybara.util.Glob;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * An extension of {@link Workflow} that is capable of reloading itself, reading the configuration
 * from the origin location provided.
 *
 * <p>How it works is with the method
 * {@link Workflow#newRunHelper(Path, Revision, String, Consumer<ChangeMigrationFinishedEvent>)} and
 * {@link WorkflowRunHelper}. The core implementation returns a regular run helper that always
 * returns {@code this} for any changes, which means that no config is read and the workflow remains
 * immutable.
 *
 * <p>The service uses this implementation to provide a {@link ReloadingRunHelper} that is capable
 * of reading the configuration for the current change being migrated, perform some security and
 * validation checks, and provide a new run helper instance.
 */
public class ReadConfigFromChangeWorkflow<O extends Revision, D extends Revision>
    extends Workflow<O, D> {
  private final Logger logger = Logger.getLogger(this.getClass().getName());

  private final ConfigLoader configLoader;
  private final ConfigValidator configValidator;

  ReadConfigFromChangeWorkflow(Workflow<O, D> workflow, Options options,
      ConfigLoader configLoader, ConfigValidator configValidator) {
    super(
        workflow.getName(),
        workflow.getDescription(),
        workflow.getOrigin(),
        workflow.getDestination(),
        workflow.getAuthoring(),
        workflow.getTransformation(),
        workflow.getLastRevisionFlag(),
        workflow.isInitHistory(),
        options.get(GeneralOptions.class),
        workflow.getOriginFiles(),
        workflow.getDestinationFiles(),
        workflow.getMode(),
        workflow.getWorkflowOptions(),
        workflow.getReverseTransformForCheck(),
        workflow.getReversibleCheckIgnoreFiles(),
        workflow.isAskForConfirmation(),
        workflow.getMainConfigFile(),
        workflow.getAllConfigFiles(),
        workflow.isDryRunMode(),
        workflow.isCheckLastRevState(),
        workflow.getAfterMigrationActions(),
        workflow.getAfterAllMigrationActions(),
        workflow.getChangeIdentity(),
        workflow.isSetRevId(),
        workflow.isSmartPrune(),
        workflow.isMigrateNoopChanges(),
        workflow.customRevId(),
        workflow.isCheckout());
    this.configLoader = checkNotNull(configLoader, "configLoaderProvider");
    this.configValidator = checkNotNull(configValidator, "configValidator");
  }

  @Override
  protected WorkflowRunHelper<O, D> newRunHelper(Path workdir, O resolvedRef,
      String rawSourceRef, Consumer<ChangeMigrationFinishedEvent> migrationFinishedMonitor)
      throws ValidationException {
    Reader<O> reader = this.getOrigin().newReader(this.getOriginFiles(), this.getAuthoring());
    return new ReloadingRunHelper(
        this,
        getName(),
        workdir,
        resolvedRef,
        createWriter(resolvedRef),
        reader,
        rawSourceRef,
        migrationFinishedMonitor);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).toString();
  }

  /**
   * A {@link WorkflowRunHelper} that reloads itself based on the change being imported, loading
   * the configuration from the origin, after performing security and validation checks.
   */
  private class ReloadingRunHelper extends WorkflowRunHelper<O, D> {
    private final Workflow<O, D> workflow;
    private final String workflowName;

    private ReloadingRunHelper(
        Workflow<O, D> workflow,
        String workflowName,
        Path workdir,
        O resolvedRef,
        Writer<D> writer,
        Reader<O> originReader,
        @Nullable String rawSourceRef,
        Consumer<ChangeMigrationFinishedEvent> migrationFinishedMonitor) {

      super(workflow, workdir, resolvedRef, originReader, writer, rawSourceRef,
          migrationFinishedMonitor);
      this.workflow = workflow;
      this.workflowName = checkNotNull(workflowName, "workflowName");
    }

    @Override
    ChangeMigrator<O, D> getMigratorForChangeAndWriter(Change<?> change, Writer<D> writer)
        throws ValidationException, RepoException {
      checkNotNull(change);

      logger.info(format("Loading configuration for change '%s %s'",
          change.getRef(), change.firstLineMessage()));

      Config config = ReadConfigFromChangeWorkflow.this.configLoader.
          loadForRevision(getConsole(), change.getRevision());
      // The service config validator already checks that the configuration matches the registry,
      // checking that the origin and destination haven't changed.
      List<String> errors =
          configValidator
              .validate(config, workflowName)
              .getErrors();
      checkCondition(errors.isEmpty(), "Invalid configuration [ref '%s': %s ]: '%s': \n%s",
          change.getRef(), configLoader.location(), workflowName, on('\n').join(errors));

      Migration migration = config.getMigration(workflowName);
      checkCondition(migration instanceof Workflow,
          "Invalid configuration [ref '%s': %s ]: '%s' is not a workflow", change.getRef(),
          configLoader.location(), workflowName);
      @SuppressWarnings("unchecked")
      Workflow<O, D> workflowForChange = (Workflow<O, D>) migration;
      Reader<O> newReader = workflowForChange
          .getOrigin()
          .newReader(workflowForChange.getOriginFiles(), workflowForChange.getAuthoring());
      return new ReloadingChangeMigrator<>(
          workflow,
          workflowForChange,
          getWorkdir(),
          newReader,
          writer,
          getResolvedRef(),
          rawSourceRef,
          getMigrationFinishedMonitor());
    }
  }

  private static final class
  ReloadingChangeMigrator<O extends Revision, D extends Revision> extends ChangeMigrator<O, D> {

    private final Workflow<O, D> changeWorkflow;

    ReloadingChangeMigrator(Workflow<O, D> headWorkflow, Workflow<O, D> changeWorkflow,
        Path workdir, Reader<O> reader,
        Writer<D> writer, O resolvedRef, @Nullable String rawSourceRef,
        Consumer<ChangeMigrationFinishedEvent> migrationFinishedMonitor) {
      super(headWorkflow, workdir, reader, writer, resolvedRef, rawSourceRef,
          migrationFinishedMonitor);
      this.changeWorkflow = Preconditions.checkNotNull(changeWorkflow);
    }

    @Override
    protected Set<String> getConfigFiles() {
      return changeWorkflow.configPaths();
    }

    @Override
    protected Glob getOriginFiles() {
      return changeWorkflow.getOriginFiles();
    }

    @Override
    protected Glob getDestinationFiles() {
      return changeWorkflow.getDestinationFiles();
    }

    @Override
    protected Transformation getTransformation() {
      return changeWorkflow.getTransformation();
    }

    @Override
    @Nullable
    protected Transformation getReverseTransformForCheck() {
      return changeWorkflow.getReverseTransformForCheck();
    }

    @Override
    protected Glob getReversibleCheckIgnoreFiles() {
      return changeWorkflow.getReversibleCheckIgnoreFiles();
    }

  }
}
