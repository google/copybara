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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Iterables;
import com.google.copybara.Destination.Writer;
import com.google.copybara.Origin.Reader;
import com.google.copybara.config.ConfigLoader;
import com.google.copybara.config.ConfigValidator;
import com.google.copybara.util.console.Message;
import com.google.copybara.util.console.Message.MessageType;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * An extension of {@link Workflow} that is capable of reloading itself, reading the configuration
 * from the origin location provided.
 *
 * <p>How it works is with the method {@link Workflow#newRunHelper(Path, Revision, String)} and
 * {@link WorkflowRunHelper#forChanges(Iterable)}. The core implementation returns a regular run
 * helper that always returns {@code this} for any changes, which means that no config is read and
 * the workflow remains immutable.
 *
 * <p>The service uses this implementation to provide a {@link ReloadingRunHelper} that is capable
 * of reading the configuration for the current change being migrated, perform some security and
 * validation checks, and provide a new run helper instance.
 */
public class ReadConfigFromChangeWorkflow<O extends Revision, D extends Revision>
    extends Workflow<O, D> {
  private final Logger logger = Logger.getLogger(this.getClass().getName());

  private final Options options;
  private final Function<Revision, ConfigLoader<?>> configLoaderProvider;
  private final ConfigValidator configValidator;
  /**
   * Last writer returned by the workflow so that we can maintain state in ITERATIVE mode.
   */
  @Nullable
  private Writer<D> lastWriter;

  public ReadConfigFromChangeWorkflow(Workflow<O, D> workflow, Options options,
      Function<Revision, ConfigLoader<?>> configLoaderProvider, ConfigValidator configValidator) {
    super(
        workflow.getName(),
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
        workflow.isAskForConfirmation(),
        workflow.getMainConfigFile(),
        workflow.getAllConfigFiles(),
        workflow.isDryRunMode(),
        workflow.isCheckLastRevState(),
        workflow.getOnFinishActions());
    this.options = checkNotNull(options, "options");
    this.configLoaderProvider = checkNotNull(configLoaderProvider, "configLoaderProvider");
    this.configValidator = checkNotNull(configValidator, "configValidator");
  }

  @Override
  protected WorkflowRunHelper<O, D> newRunHelper(Path workdir, O resolvedRef,
      String rawSourceRef)
      throws ValidationException, RepoException {
    Reader<O> reader = this.getOrigin().newReader(this.getOriginFiles(), this.getAuthoring());
    String groupId = computeGroupIdentity(reader.getGroupIdentity(resolvedRef));
    return new ReloadingRunHelper(
        this, options, getName(), workdir, resolvedRef, this.isDryRunMode(), /*oldWriter=*/null,
        reader, groupId, rawSourceRef);
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
    private final Options options;
    private final String workflowName;

    private ReloadingRunHelper(
        Workflow<O, D> workflow, Options options, String workflowName, Path workdir, O resolvedRef,
        boolean dryRun, @Nullable Writer<D> oldWriter, Reader<O> originReader,
        @Nullable String groupId, @Nullable String rawSourceRef)
        throws ValidationException, RepoException {
      super(workflow, workdir, resolvedRef,
            originReader,
            workflow.getDestination()
                    .newWriter(workflow.getDestinationFiles(), dryRun, groupId, oldWriter),
            groupId, rawSourceRef);
      this.workflow = workflow;
      this.options = checkNotNull(options, "options");
      this.workflowName = checkNotNull(workflowName, "workflowName");
    }

    @Override
    protected WorkflowRunHelper<O, D> forChanges(Iterable<? extends Change<?>> currentChanges)
        throws RepoException, ValidationException, IOException {
      // Why last? If it's iterative mode, there is only one change, and if it's squash we want
      // the last one.
      if (Iterables.isEmpty(currentChanges)) {
        // Squash workflows can provide a list of empty changes if no migration is pending.
        return this;
      }

      @SuppressWarnings("unchecked")
      Change<O> lastChange = (Change<O>) Iterables.getLast(currentChanges);
      logger.info(String.format("Loading configuration for change '%s %s'",
                                lastChange.refAsString(), lastChange.firstLineMessage()));

      ConfigLoader<?> configLoader = configLoaderProvider.apply(lastChange.getRevision());
      Config config = configLoader.loadConfig(options);
      // The service config validator already checks that the configuration matches the registry,
      // checking that the origin and destination haven't changed.
      List<String> errors =
          configValidator
              .validate(config, workflowName)
              .stream()
              .filter(message -> message.getType() == MessageType.ERROR)
              .map(Message::getText)
              .collect(Collectors.toList());
      if (!errors.isEmpty()) {
        throw new ValidationException(
            String.format(
                "Invalid configuration [ref '%s': %s ]: '%s': \n%s",
                lastChange.refAsString(), configLoader.location(), workflowName,
                Joiner.on('\n').join(errors)));
      }

      Migration migration = config.getMigration(workflowName);
      if (!(migration instanceof Workflow)) {
        throw new ValidationException(
            String.format(
                "Invalid configuration [ref '%s': %s ]: '%s' is not a workflow",
                lastChange.refAsString(), configLoader.location(), workflowName));
      }
      @SuppressWarnings("unchecked")
      Workflow<O, D> workflowForChange = (Workflow<O, D>) migration;
      //noinspection unchecked
      ReloadingRunHelper helper = new ReloadingRunHelper(
          workflowForChange, options, workflowName, getWorkdir(), getResolvedRef(),
          workflowForChange.isDryRunMode(), lastWriter,
          workflowForChange.getOrigin()
              .newReader(workflowForChange.getOriginFiles(), workflowForChange.getAuthoring()),
          getGroupId(), rawSourceRef);
      lastWriter = helper.writer;
      return helper;
    }

    @Override
    protected WorkflowRunHelper<O, D> withDryRun()
        throws RepoException, ValidationException, IOException {
      return new ReloadingRunHelper(
          workflow, options, workflowName, getWorkdir(), getResolvedRef(),
          // Not sharing old writer status on purpose for dry run to avoid accidents.
          /*dryRun=*/true, /*oldWriter=*/writer,
          workflow.getOrigin().newReader(workflow.getOriginFiles(), workflow.getAuthoring()),
          getGroupId(), rawSourceRef);
    }
  }
}
