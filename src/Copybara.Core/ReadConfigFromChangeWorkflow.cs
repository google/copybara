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

using System.Collections.Immutable;
using System.Globalization;
using Copybara.Common;
using Copybara.Config;
using Copybara.Exceptions;
using Copybara.Revision;
using ChangeMigrationFinishedEvent = Copybara.Monitor.IEventMonitor.ChangeMigrationFinishedEvent;

namespace Copybara;

/// <summary>
/// An extension of <see cref="Workflow{O,D}"/> that is capable of reloading itself, reading the
/// configuration from the origin location provided.
///
/// <para>The core implementation returns a regular run helper that always returns <c>this</c> for any
/// changes, which means that no config is read and the workflow remains immutable. This
/// implementation provides a <c>ReloadingRunHelper</c> that reads the configuration for the current
/// change being migrated, performs security and validation checks, and provides a new run helper
/// instance.</para>
/// </summary>
/// <typeparam name="O">Origin revision type.</typeparam>
/// <typeparam name="D">Destination revision type.</typeparam>
public class ReadConfigFromChangeWorkflow<O, D> : Workflow<O, D>
    where O : class, IRevision
    where D : class, IRevision
{
    private readonly ConfigLoader _configLoader;
    private readonly ConfigValidator _configValidator;

    internal ReadConfigFromChangeWorkflow(
        Workflow<O, D> workflow,
        Options options,
        ConfigLoader configLoader,
        ConfigValidator configValidator)
        : base(
            workflow.GetName(),
            workflow.GetDescription(),
            workflow.GetOrigin(),
            workflow.GetDestination(),
            workflow.GetAuthoring(),
            workflow.GetTransformation(),
            workflow.GetLastRevisionFlag(),
            workflow.IsInitHistory(),
            options.Get<GeneralOptions>(),
            workflow.GetOriginFiles(),
            workflow.GetDestinationFiles(),
            workflow.GetMode(),
            workflow.GetWorkflowOptions(),
            workflow.GetReverseTransformForCheck(),
            workflow.GetReversibleCheckIgnoreFiles(),
            workflow.IsAskForConfirmation(),
            workflow.GetMainConfigFile(),
            workflow.GetAllConfigFiles(),
            workflow.IsDryRunMode(),
            workflow.IsCheckLastRevState(),
            workflow.GetAfterMigrationActions().ToImmutableArray(),
            workflow.GetAfterAllMigrationActions().ToImmutableArray(),
            workflow.GetChangeIdentity(),
            workflow.IsSetRevId(),
            workflow.IsSmartPrune(),
            workflow.GetMergeImport(),
            workflow.GetAutoPatchfileConfiguration(),
            workflow.AfterMergeTransformations,
            workflow.IsMigrateNoopChanges(),
            workflow.CustomRevId(),
            workflow.IsCheckout(),
            workflow.GetConsistencyFileConfig(),
            workflow.GetExpectedFixedRef(),
            workflow.GetPinnedFixedRef(),
            workflow.GetDefinitionStack(),
            workflow.GetDefinitionStackLocals())
    {
        _configLoader = Preconditions.CheckNotNull(configLoader, "configLoaderProvider");
        _configValidator = Preconditions.CheckNotNull(configValidator, "configValidator");
    }

    public override WorkflowRunHelper<O, D> NewRunHelper(
        string workdir,
        O resolvedRef,
        string? rawSourceRef,
        Action<ChangeMigrationFinishedEvent> migrationFinishedMonitor)
    {
        IOrigin<O>.IReader<O> reader = GetOrigin().NewReader(GetOriginFiles(), GetAuthoring());
        return new ReloadingRunHelper(
            this,
            this,
            GetName(),
            workdir,
            resolvedRef,
            CreateWriter(resolvedRef),
            reader,
            rawSourceRef,
            migrationFinishedMonitor);
    }

    public override string ToString() => "ReadConfigFromChangeWorkflow{}";

    /// <summary>
    /// A <see cref="WorkflowRunHelper{O,D}"/> that reloads itself based on the change being imported,
    /// loading the configuration from the origin, after performing security and validation checks.
    /// </summary>
    private sealed class ReloadingRunHelper : WorkflowRunHelper<O, D>
    {
        private readonly ReadConfigFromChangeWorkflow<O, D> _outer;
        private readonly Workflow<O, D> _workflow;
        private readonly string _workflowName;

        internal ReloadingRunHelper(
            ReadConfigFromChangeWorkflow<O, D> outer,
            Workflow<O, D> workflow,
            string workflowName,
            string workdir,
            O resolvedRef,
            IDestination<D>.IWriter<D> writer,
            IOrigin<O>.IReader<O> originReader,
            string? rawSourceRef,
            Action<ChangeMigrationFinishedEvent> migrationFinishedMonitor)
            : base(workflow, workdir, resolvedRef, originReader, writer, rawSourceRef,
                migrationFinishedMonitor)
        {
            _outer = outer;
            _workflow = workflow;
            _workflowName = Preconditions.CheckNotNull(workflowName, "workflowName");
        }

        internal override ChangeMigrator<O, D> GetMigratorForChangeAndWriter(
            Change<O> change, IDestination<D>.IWriter<D> writer)
        {
            Preconditions.CheckNotNull(change);

            // logger.info("Loading configuration for change '%s %s'", change.getRef(), ...);

            Config.Config config = _outer._configLoader.LoadForRevision(
                GetConsole(), change.GetRevision());
            // The service config validator already checks that the configuration matches the
            // registry, checking that the origin and destination haven't changed.
            IReadOnlyList<string> errors = _outer._configValidator
                .Validate(config, _workflowName)
                .GetErrors();
            ValidationException.CheckCondition(
                errors.Count == 0,
                "Invalid configuration [ref '%s': %s ]: '%s': \n%s",
                change.Ref,
                _outer._configLoader.Location(),
                _workflowName,
                string.Join("\n", errors));

            IMigration migration = config.GetMigration(_workflowName);
            ValidationException.CheckCondition(
                migration is Workflow<O, D>,
                "Invalid configuration [ref '%s': %s ]: '%s' is not a workflow",
                change.Ref,
                _outer._configLoader.Location(),
                _workflowName);
            var workflowForChange = (Workflow<O, D>)migration;
            IOrigin<O>.IReader<O> newReader = workflowForChange.GetOrigin()
                .NewReader(workflowForChange.GetOriginFiles(), workflowForChange.GetAuthoring());
            return new ReloadingChangeMigrator(
                _workflow,
                workflowForChange,
                GetWorkdir(),
                newReader,
                writer,
                GetResolvedRef(),
                RawSourceRef,
                GetMigrationFinishedMonitor());
        }
    }

    private sealed class ReloadingChangeMigrator : ChangeMigrator<O, D>
    {
        private readonly Workflow<O, D> _changeWorkflow;

        internal ReloadingChangeMigrator(
            Workflow<O, D> headWorkflow,
            Workflow<O, D> changeWorkflow,
            string workdir,
            IOrigin<O>.IReader<O> reader,
            IDestination<D>.IWriter<D> writer,
            O resolvedRef,
            string? rawSourceRef,
            Action<ChangeMigrationFinishedEvent> migrationFinishedMonitor)
            : base(headWorkflow, workdir, reader, writer, resolvedRef, rawSourceRef,
                migrationFinishedMonitor)
        {
            _changeWorkflow = Preconditions.CheckNotNull(changeWorkflow);
        }

        protected override Workflow<O, D> GetWorkflow() => _changeWorkflow;
    }
}
