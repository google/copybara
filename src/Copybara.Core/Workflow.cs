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

using System.Collections.Immutable;
using System.Globalization;
using System.Text;
using Copybara.Action;
using Copybara.Authoring;
using Copybara.Common;
using Copybara.Config;
using Copybara.Effect;
using Copybara.Exceptions;
using Copybara.Revision;
using Copybara.TemplateToken;
using Copybara.Util;
using Starlark.Eval;
using Console = Copybara.Util.Console.Console;
using ChangeMigrationFinishedEvent = Copybara.Monitor.IEventMonitor.ChangeMigrationFinishedEvent;
using EventMonitors = Copybara.Monitor.IEventMonitor.EventMonitors;

namespace Copybara;

/// <summary>
/// Non-generic entry points for <see cref="Workflow{O,D}"/>.
///
/// <para>Starlark's <c>core.workflow</c> receives its origin and destination as untyped objects
/// (Java uses raw/wildcard types <c>Origin&lt;?&gt;</c> / <c>Destination&lt;?&gt;</c>). Because
/// .NET generics are reified — unlike Java's erasure — we cannot simply cast an
/// <c>IOrigin&lt;FolderRevision&gt;</c> to <c>IOrigin&lt;Revision&gt;</c>. Instead we reflect the
/// concrete revision type parameters out of the runtime origin/destination and instantiate the
/// correctly-typed <see cref="Workflow{O,D}"/>. This preserves the Java behavior while remaining
/// type-safe at runtime.</para>
/// </summary>
public static class Workflow
{
    internal const string COPYBARA_CONFIG_PATH_IDENTITY_VAR = "copybara_config_path";
    internal const string COPYBARA_WORKFLOW_NAME_IDENTITY_VAR = "copybara_workflow_name";
    internal const string COPYBARA_REFERENCE_IDENTITY_VAR = "copybara_reference";
    internal const string COPYBARA_REFERENCE_LABEL_VAR = "label:";

    /// <summary>
    /// Builds a <see cref="Workflow{O,D}"/> whose type parameters are inferred from the runtime
    /// types of <paramref name="origin"/> and <paramref name="destination"/>. All remaining
    /// arguments are forwarded, in order, to the <see cref="Workflow{O,D}"/> constructor.
    /// </summary>
    public static IMigration Create(
        string name,
        string? description,
        object origin,
        object destination,
        Authoring.Authoring authoring,
        ITransformation transformation,
        string? lastRevisionFlag,
        bool initHistoryFlag,
        GeneralOptions generalOptions,
        Glob originFiles,
        Glob destinationFiles,
        WorkflowMode mode,
        WorkflowOptions workflowOptions,
        ITransformation? reverseTransformForCheck,
        Glob reversibleCheckIgnoreFiles,
        bool askForConfirmation,
        ConfigFile mainConfigFile,
        Func<ImmutableDictionary<string, ConfigFile>> allConfigFiles,
        bool dryRunModeField,
        bool checkLastRevState,
        ImmutableArray<IAction> afterMigrationActions,
        ImmutableArray<IAction> afterAllMigrationActions,
        ImmutableArray<Token> changeIdentity,
        bool setRevId,
        bool smartPrune,
        MergeImportConfiguration? mergeImport,
        AutoPatchfileConfiguration? autoPatchfileConfiguration,
        ITransformation afterMergeTransformations,
        bool migrateNoopChanges,
        string? customRevId,
        bool checkout,
        ConsistencyFileConfiguration? consistencyFileConfig,
        string? expectedFixedRef,
        string? pinnedFixedRef,
        ImmutableArray<StarlarkThread.CallStackEntry> definitionStack,
        ImmutableArray<ImmutableDictionary<string, string>> definitionStackLocals)
    {
        Type originRevision = FindRevisionArg(origin.GetType(), typeof(IOrigin<>))
            ?? throw new ArgumentException(
                $"origin of type '{origin.GetType()}' does not implement IOrigin<>");
        Type destinationRevision = FindRevisionArg(destination.GetType(), typeof(IDestination<>))
            ?? throw new ArgumentException(
                $"destination of type '{destination.GetType()}' does not implement IDestination<>");

        Type workflowType = typeof(Workflow<,>).MakeGenericType(originRevision, destinationRevision);
        object? instance = Activator.CreateInstance(
            workflowType,
            name,
            description,
            origin,
            destination,
            authoring,
            transformation,
            lastRevisionFlag,
            initHistoryFlag,
            generalOptions,
            originFiles,
            destinationFiles,
            mode,
            workflowOptions,
            reverseTransformForCheck,
            reversibleCheckIgnoreFiles,
            askForConfirmation,
            mainConfigFile,
            allConfigFiles,
            dryRunModeField,
            checkLastRevState,
            afterMigrationActions,
            afterAllMigrationActions,
            changeIdentity,
            setRevId,
            smartPrune,
            mergeImport,
            autoPatchfileConfiguration,
            afterMergeTransformations,
            migrateNoopChanges,
            customRevId,
            checkout,
            consistencyFileConfig,
            expectedFixedRef,
            pinnedFixedRef,
            definitionStack,
            definitionStackLocals);
        return (IMigration)instance!;
    }

    /// <summary>
    /// Returns the revision type argument of the <paramref name="openInterface"/> (e.g.
    /// <c>IOrigin&lt;&gt;</c>) implemented by <paramref name="concrete"/>, or null if not found.
    /// </summary>
    private static Type? FindRevisionArg(Type concrete, Type openInterface)
    {
        foreach (Type i in concrete.GetInterfaces())
        {
            if (i.IsGenericType && i.GetGenericTypeDefinition() == openInterface)
            {
                return i.GetGenericArguments()[0];
            }
        }
        return null;
    }
}

/// <summary>
/// Represents a particular migration operation that can occur for a project. Each project can have
/// multiple workflows. Each workflow has a particular origin and destination.
/// </summary>
/// <typeparam name="O">Origin revision type.</typeparam>
/// <typeparam name="D">Destination revision type.</typeparam>
public class Workflow<O, D> : IMigration
    where O : class, IRevision
    where D : class, IRevision
{
    private readonly string _name;
    private readonly string? _description;
    private readonly IOrigin<O> _origin;
    private readonly IDestination<D> _destination;
    private readonly Authoring.Authoring _authoring;
    private readonly ITransformation _transformation;

    private readonly string? _lastRevisionFlag;
    private readonly bool _initHistoryFlag;
    private readonly Console _console;
    private readonly GeneralOptions _generalOptions;
    private readonly Glob _originFiles;
    private readonly Glob _destinationFiles;
    private readonly WorkflowMode _mode;
    private readonly WorkflowOptions _workflowOptions;

    private readonly ITransformation? _reverseTransformForCheck;
    private readonly bool _verbose;
    private readonly Glob _reversibleCheckIgnoreFiles;
    private readonly bool _askForConfirmation;
    private readonly bool _force;
    private readonly ConfigFile _mainConfigFile;
    private readonly Func<ImmutableDictionary<string, ConfigFile>> _allConfigFiles;
    private readonly bool _effectiveDryRunMode;
    private readonly bool _dryRunModeField;
    private readonly ImmutableArray<IAction> _afterMigrationActions;
    private readonly ImmutableArray<Token> _changeIdentity;
    private readonly bool _setRevId;
    private readonly bool _smartPrune;
    private readonly MergeImportConfiguration? _mergeImport;
    private readonly AutoPatchfileConfiguration? _autoPatchfileConfiguration;

    // Package-visible in Java ('final Transformation afterMergeTransformations').
    internal readonly ITransformation AfterMergeTransformations;
    private readonly bool _migrateNoopChanges;
    private readonly bool _checkLastRevState;
    private readonly ImmutableArray<IAction> _afterAllMigrationActions;
    private readonly string? _customRevId;
    private readonly bool _checkout;

    private readonly ConsistencyFileConfiguration? _consistencyFileConfig;
    private readonly string? _expectedFixedRef;
    private readonly string? _pinnedFixedRef;
    private readonly ImmutableArray<StarlarkThread.CallStackEntry> _definitionStack;
    private readonly ImmutableArray<ImmutableDictionary<string, string>> _definitionStackLocals;

    public Workflow(
        string name,
        string? description,
        IOrigin<O> origin,
        IDestination<D> destination,
        Authoring.Authoring authoring,
        ITransformation transformation,
        string? lastRevisionFlag,
        bool initHistoryFlag,
        GeneralOptions generalOptions,
        Glob originFiles,
        Glob destinationFiles,
        WorkflowMode mode,
        WorkflowOptions workflowOptions,
        ITransformation? reverseTransformForCheck,
        Glob reversibleCheckIgnoreFiles,
        bool askForConfirmation,
        ConfigFile mainConfigFile,
        Func<ImmutableDictionary<string, ConfigFile>> allConfigFiles,
        bool dryRunModeField,
        bool checkLastRevState,
        ImmutableArray<IAction> afterMigrationActions,
        ImmutableArray<IAction> afterAllMigrationActions,
        ImmutableArray<Token> changeIdentity,
        bool setRevId,
        bool smartPrune,
        MergeImportConfiguration? mergeImport,
        AutoPatchfileConfiguration? autoPatchfileConfiguration,
        ITransformation afterMergeTransformations,
        bool migrateNoopChanges,
        string? customRevId,
        bool checkout,
        ConsistencyFileConfiguration? consistencyFileConfig,
        string? expectedFixedRef,
        string? pinnedFixedRef,
        ImmutableArray<StarlarkThread.CallStackEntry> definitionStack,
        ImmutableArray<ImmutableDictionary<string, string>> definitionStackLocals)
    {
        _name = Preconditions.CheckNotNull(name);
        _description = description;
        _origin = Preconditions.CheckNotNull(origin);
        _destination = Preconditions.CheckNotNull(destination);
        _authoring = Preconditions.CheckNotNull(authoring);
        _transformation = Preconditions.CheckNotNull(transformation);
        _lastRevisionFlag = lastRevisionFlag;
        _initHistoryFlag = initHistoryFlag;
        _console = Preconditions.CheckNotNull(generalOptions.GetConsole());
        _generalOptions = generalOptions;
        _originFiles = Preconditions.CheckNotNull(originFiles);
        _destinationFiles = Preconditions.CheckNotNull(destinationFiles);
        _mode = mode;
        _workflowOptions = Preconditions.CheckNotNull(workflowOptions);
        _reverseTransformForCheck = reverseTransformForCheck;
        _verbose = generalOptions.IsVerbose();
        _reversibleCheckIgnoreFiles = reversibleCheckIgnoreFiles;
        _askForConfirmation = askForConfirmation;
        _force = generalOptions.IsForced();
        _mainConfigFile = Preconditions.CheckNotNull(mainConfigFile);
        _allConfigFiles = allConfigFiles;
        _checkLastRevState = checkLastRevState;
        _customRevId = customRevId;
        _checkout = checkout;
        _effectiveDryRunMode = dryRunModeField || generalOptions.DryRunMode;
        _dryRunModeField = dryRunModeField;
        _afterMigrationActions = afterMigrationActions;
        _afterAllMigrationActions = afterAllMigrationActions;
        _changeIdentity = changeIdentity;
        _setRevId = setRevId;
        _smartPrune = smartPrune;
        _mergeImport = mergeImport;
        _autoPatchfileConfiguration = autoPatchfileConfiguration;
        AfterMergeTransformations = afterMergeTransformations;
        _migrateNoopChanges = migrateNoopChanges;
        _consistencyFileConfig = consistencyFileConfig;
        _expectedFixedRef = expectedFixedRef;
        _pinnedFixedRef = pinnedFixedRef;
        _definitionStack = definitionStack;
        _definitionStackLocals = definitionStackLocals;
    }

    public string GetName() => _name;

    public string? GetDescription() => _description;

    /// <summary>The repository that represents the source of truth.</summary>
    public IOrigin<O> GetOrigin() => _origin;

    /// <summary>The destination repository to copy to.</summary>
    public IDestination<D> GetDestination() => _destination;

    /// <summary>The author mapping between an origin and a destination.</summary>
    public Authoring.Authoring GetAuthoring() => _authoring;

    /// <summary>Transformation to run before writing them to the destination.</summary>
    public ITransformation GetTransformation() => _transformation;

    public bool IsAskForConfirmation() => _askForConfirmation;

    /// <summary>
    /// Includes only the fields that are part of the configuration: Console is not part of the
    /// config, configName is in the parent, and lastRevisionFlag is a command-line flag.
    /// </summary>
    public override string ToString() =>
        $"Workflow{{name={_name}, origin={_origin}, destination={_destination}, authoring={_authoring},"
        + $" transformation={_transformation}, originFiles={_originFiles},"
        + $" destinationFiles={_destinationFiles}, mode={_mode},"
        + $" reverseTransformForCheck={_reverseTransformForCheck}, askForConfirmation={_askForConfirmation},"
        + $" checkLastRevState={_checkLastRevState}, afterMigrationActions=[{string.Join(", ", _afterMigrationActions)}],"
        + $" changeIdentity=[{string.Join(", ", _changeIdentity)}], setRevId={_setRevId}}}";

    public void Run(string workdir, IReadOnlyList<string> sourceRefs)
    {
        if (sourceRefs.Count > 1)
        {
            throw new CommandLineException(
                string.Format(
                    CultureInfo.InvariantCulture,
                    "Workflow does not support multiple source_ref arguments yet: {0}",
                    string.Join(", ", sourceRefs)));
        }

        string? sourceRef = sourceRefs.Count == 1 ? sourceRefs[0] : null;

        ValidateFlags();
        using (Profiler().Start("run/" + _name))
        {
            _console.Progress(
                "Getting last revision: Resolving "
                    + (sourceRef == null ? "origin reference" : sourceRef));
            O resolvedRef = _generalOptions.RepoTask(
                "origin.resolve_source_ref", () => _origin.Resolve(sourceRef!));

            if (!string.IsNullOrEmpty(_expectedFixedRef) && !string.IsNullOrEmpty(_pinnedFixedRef))
            {
                throw new CommandLineException(
                    "Using --expected-fixed-ref and --pinned-fixed-ref together is not supported.");
            }

            if (!string.IsNullOrEmpty(_expectedFixedRef))
            {
                ValidateExpectedFixedRef(resolvedRef);
            }

            if (!string.IsNullOrEmpty(_pinnedFixedRef))
            {
                resolvedRef = HandlePinnedFixedRef(resolvedRef, _pinnedFixedRef!);
            }

            var allEffects = ImmutableArray.CreateBuilder<DestinationEffect>();
            WorkflowRunHelper<O, D> helper = NewRunHelper(
                workdir,
                resolvedRef,
                sourceRef,
                @event =>
                {
                    allEffects.AddRange(@event.DestinationEffects);
                    EventMonitors().DispatchEvent(m => m.OnChangeMigrationFinished(@event));
                });
            try
            {
                using (Profiler().Start(_mode.ToString().ToLowerInvariant()))
                {
                    // Run-mode dispatch: mirrors Java's per-enum WorkflowMode.run(helper).
                    WorkflowModeRunner.Run(_mode, helper);
                }
            }
            finally
            {
                if (!GetGeneralOptions().DryRunMode)
                {
                    using (Profiler().Start("after_all_migration"))
                    {
                        ImmutableArray<DestinationEffect> effects = allEffects.ToImmutable();
                        IReadOnlyList<DestinationEffect> resultEffects = RunHooks(
                            effects,
                            GetAfterAllMigrationActions(),
                            // Only do this once for all the actions
                            LazyResourceLoader.Memoized<IEndpoint>(
                                c => helper.GetOriginReader().GetFeedbackEndPoint(c!)),
                            // Only do this once for all the actions
                            LazyResourceLoader.Memoized<IEndpoint>(
                                c => helper.GetDestinationWriter().GetFeedbackEndPoint(c!)),
                            resolvedRef);
                        if (effects.Length != resultEffects.Count)
                        {
                            _console.Warn(
                                "Effects where created in after_all_migrations, but they are ignored.");
                        }
                    }
                }
            }
        }
    }

    private void ValidateExpectedFixedRef(O resolvedRef)
    {
        if (!string.IsNullOrEmpty(_expectedFixedRef)
            && !string.IsNullOrEmpty(resolvedRef.FixedReference())
            && !resolvedRef.FixedReference()!.Equals(_expectedFixedRef, StringComparison.Ordinal))
        {
            throw new ValidationException(
                string.Format(
                    CultureInfo.InvariantCulture,
                    "Not migrating ref {0}, its fixed ref {1} did not match the expected fixed ref {2}.",
                    resolvedRef.ContextReference() ?? resolvedRef.AsString(),
                    resolvedRef.FixedReference(),
                    _expectedFixedRef));
        }
    }

    private O HandlePinnedFixedRef(O resolvedMigrationRef, string pinnedFixedRef)
    {
        // Resolve the ref specified with --pinned-fixed-ref to ensure it is in the local repo cache,
        // so `git merge-base` does not fail.
        try
        {
            O resolvedPinnedRef = _generalOptions.RepoTask(
                "origin.resolve_pinned_fixed_ref", () => _origin.Resolve(pinnedFixedRef));
            return _origin.ResolveAncestorRef(
                resolvedPinnedRef.FixedReference()!, resolvedMigrationRef);
        }
        catch (ValidationException e)
        {
            throw new ValidationException(
                string.Format(
                    CultureInfo.InvariantCulture,
                    "Could not enforce --pinned-fixed-ref. Cause: {0}",
                    e.Message),
                e);
        }
    }

    /// <summary>
    /// Validates if flags are compatible with this workflow.
    /// </summary>
    /// <exception cref="ValidationException">if flags are invalid for this workflow.</exception>
    private void ValidateFlags()
    {
        _console.VerboseFmt(
            "Using %s parallel threads for transformations", _workflowOptions.Threads);
        ValidationException.CheckCondition(
            !IsInitHistory() || _mode != WorkflowMode.ChangeRequest,
            "%s is not compatible with %s",
            WorkflowOptions.InitHistoryFlag,
            WorkflowMode.ChangeRequest);
        ValidationException.CheckCondition(
            !IsCheckLastRevState() || _mode != WorkflowMode.ChangeRequest,
            "%s is not compatible with %s",
            WorkflowOptions.CheckLastRevStateFlag,
            WorkflowMode.ChangeRequest);
        ValidationException.CheckCondition(
            !IsSmartPrune() || _mode == WorkflowMode.ChangeRequest,
            "'smart_prune = True' is only supported for CHANGE_REQUEST mode.");
        if (IsSetRevId())
        {
            ValidationException.CheckCondition(
                _mode != WorkflowMode.ChangeRequest || _customRevId == null,
                "custom_rev_id is not allowed to be used in CHANGE_REQUEST mode if"
                    + " set_rev_id is set to true. custom_rev_id is used for looking"
                    + " for the baseline in the origin. No revId is stored in the destination.");
        }
        else
        {
            ValidationException.CheckCondition(
                _mode == WorkflowMode.ChangeRequest || _mode == WorkflowMode.ChangeRequestFromSot,
                "'set_rev_id = False' is only supported"
                    + " for CHANGE_REQUEST and CHANGE_REQUEST_FROM_SOT mode.");
        }
    }

    public virtual WorkflowRunHelper<O, D> NewRunHelper(
        string workdir,
        O resolvedRef,
        string? rawSourceRef,
        Action<ChangeMigrationFinishedEvent> migrationFinishedMonitor)
    {
        IOrigin<O>.IReader<O> reader = GetOrigin().NewReader(GetOriginFiles(), GetAuthoring());
        return new WorkflowRunHelper<O, D>(
            this,
            workdir,
            resolvedRef,
            reader,
            CreateWriter(resolvedRef),
            rawSourceRef,
            migrationFinishedMonitor);
    }

    /// <summary>
    /// Return the config files relative to their roots. For example a config file like 'admin/foo/bar'
    /// with a root 'admin' would return 'foo/bar'.
    /// </summary>
    internal ISet<string> ConfigPaths()
    {
        var result = new HashSet<string>();
        foreach (var configFile in _allConfigFiles().Values)
        {
            result.Add(configFile.GetIdentifier());
        }
        return result;
    }

    public Info<IRevision> GetInfo()
    {
        return _generalOptions.RepoTask<Info<IRevision>>(
            "info",
            () =>
            {
                O? lastResolved = _generalOptions.RepoTask<O?>(
                    "origin.last_resolved", () => _origin.Resolve(reference: null!));

                IOrigin<O>.IReader<O> oReader = _origin.NewReader(_originFiles, _authoring);
                Change<O>? lastResolvedChange = null;
                try
                {
                    lastResolvedChange = _generalOptions.RepoTask<Change<O>?>(
                        "origin.last_resolved",
                        () => lastResolved == null ? null : oReader.Change(lastResolved));
                }
                catch (Exception e) when (e is RepoException or ValidationException)
                {
                    // logger.atInfo().withCause(e): Error resolving change for lastResolved
                }

                DestinationStatus? destinationStatus = _generalOptions.RepoTask<DestinationStatus?>(
                    "destination.previous_ref", () => GetDestinationStatus(lastResolved));

                O? lastMigrated = _generalOptions.RepoTask<O?>(
                    "origin.last_migrated",
                    () => destinationStatus == null
                        ? null
                        : _origin.ResolveLastRev(destinationStatus.GetBaseline()));

                Change<O>? lastMigratedChange = null;
                try
                {
                    lastMigratedChange = _generalOptions.RepoTask<Change<O>?>(
                        "origin.last_migrated",
                        () => lastMigrated == null ? null : oReader.Change(lastMigrated));
                }
                catch (Exception e) when (e is RepoException or ValidationException)
                {
                    // logger.atInfo().withCause(e): Error resolving change for lastMigrated
                }

                IReadOnlyList<Change<O>> allChanges = _generalOptions.RepoTask<IReadOnlyList<Change<O>>>(
                    "origin.changes",
                    () =>
                    {
                        Origin.ChangesResponse<O> changes = oReader.Changes(lastMigrated, lastResolved!);
                        if (!changes.IsEmpty())
                        {
                            return changes.GetChanges();
                        }
                        return ImmutableArray<Change<O>>.Empty;
                    });

                WorkflowRunHelper<O, D> helper = NewRunHelper(
                    // We shouldn't use this path for info
                    "shouldnt_be_used",
                    lastResolved!,
                    rawSourceRef: null,
                    // We don't create effects on info
                    _ => { });

                var affectedChanges = new List<Change<O>>();
                foreach (var change in allChanges)
                {
                    if (helper.GetMigratorForChange(change).ShouldSkipChange(change))
                    {
                        continue;
                    }
                    affectedChanges.Add(change);
                }

                MigrationReference<O> migrationRef = MigrationReference<O>.Create(
                    string.Format(CultureInfo.InvariantCulture, "workflow_{0}", _name),
                    lastMigrated,
                    lastMigratedChange,
                    affectedChanges,
                    lastResolvedChange);

                IReadOnlyList<Change<O>> originVersions = ImmutableArray<Change<O>>.Empty;
                if (_workflowOptions.InfoIncludeVersions)
                {
                    originVersions = oReader.GetVersions();
                }

                // TODO(port): reconcile - Info<IRevision> is the IMigration return type, but the
                // workflow computes Info<O>. Rebuild the info against the IRevision base type.
                return Info<IRevision>.Create(
                    GetOriginDescription(),
                    GetDestinationDescription(),
                    migrationRef is { } mr
                        ? new[] { RebaseMigrationReference(mr) }
                        : Array.Empty<MigrationReference<IRevision>>(),
                    originVersions.Select(c => (Change<IRevision>)(object)c).ToImmutableArray());
            });
    }

    private static MigrationReference<IRevision> RebaseMigrationReference(MigrationReference<O> mr) =>
        // TODO(port): reconcile - unchecked cast reflecting Java's Info<? extends Revision> variance.
        MigrationReference<IRevision>.Create(
            mr.GetLabel(),
            mr.LastMigrated,
            (Change<IRevision>?)(object?)mr.LastMigratedChange,
            mr.GetAvailableToMigrate().Select(c => (Change<IRevision>)(object)c),
            (Change<IRevision>?)(object?)mr.LastResolvedChange);

    private DestinationStatus? GetDestinationStatus(O? revision)
    {
        if (GetLastRevisionFlag() != null)
        {
            return new DestinationStatus(GetLastRevisionFlag()!, ImmutableArray<string>.Empty);
        }
        return CreateDryRunWriter(revision!)
            .GetDestinationStatus(GetDestinationFiles(), GetRevIdLabel());
    }

    internal string GetRevIdLabel() => _customRevId ?? _origin.GetLabelName();

    /// <summary>Create a writer that respects the effectiveDryRunMode value.</summary>
    internal IDestination<D>.IWriter<D> CreateWriter(O revision) =>
        _destination.NewWriter(new WriterContext(
            _name,
            _workflowOptions.WorkflowIdentityUser,
            _effectiveDryRunMode,
            revision,
            _destinationFiles.Roots()));

    /// <summary>Create a writer in dry-run mode.</summary>
    internal IDestination<D>.IWriter<D> CreateDryRunWriter(O revision) =>
        _destination.NewWriter(new WriterContext(
            _name,
            _workflowOptions.WorkflowIdentityUser,
            /*dryRun=*/ true,
            revision,
            _destinationFiles.Roots()));

    public ImmutableListMultimap<string, string> GetOriginDescription() => _origin.Describe(_originFiles);

    public ImmutableListMultimap<string, string> GetDestinationDescription() =>
        _destination.Describe(_destinationFiles);

    public IReadOnlyList<ImmutableListMultimap<string, string>> GetCredentialDescription()
    {
        var allCreds = ImmutableArray.CreateBuilder<ImmutableListMultimap<string, string>>();
        allCreds.AddRange(((IConfigItemDescription)_origin).DescribeCredentials("origin"));
        allCreds.AddRange(((IConfigItemDescription)_destination).DescribeCredentials("destination"));
        return allCreds.ToImmutable();
    }

    public Glob GetOriginFiles() => _originFiles;

    public Glob GetDestinationFiles() => _destinationFiles;

    public Console GetConsole() => _console;

    public WorkflowOptions GetWorkflowOptions() => _workflowOptions;

    public bool IsForce() => _force;

    public ITransformation? GetReverseTransformForCheck() => _reverseTransformForCheck;

    public bool IsVerbose() => _verbose;

    internal string? GetLastRevisionFlag() => _lastRevisionFlag;

    internal bool IsInitHistory() => _initHistoryFlag;

    public WorkflowMode GetMode() => _mode;

    public string GetModeString() => _mode.ToString();

    internal bool IsCheckLastRevState() => _checkLastRevState;

    internal bool IsDryRunMode() => _effectiveDryRunMode;

    public bool IsDryRunModeField() => _dryRunModeField;

    public bool IsCheckout() => _checkout;

    /// <summary>
    /// Migration identity tries to create a stable identifier for the migration that is stable between
    /// Copybara invocations for the same reference. For example it will contain the copy.bara.sky
    /// config file location relative to the root, the workflow name or the context reference used in
    /// the request.
    ///
    /// <para>This identifier can be used by destinations to reuse code reviews, etc.</para>
    /// </summary>
    internal string GetMigrationIdentity(IRevision requestedRevision, TransformWork transformWork)
    {
        bool contextRefDefined = requestedRevision.ContextReference() != null;
        // In iterative mode we want to use the revision, since we could have an export from
        // git.origin(master) -> git.gerrit_destination. In that case we want to create one change
        // per origin commit.
        string ctxRef = contextRefDefined && _mode != WorkflowMode.Iterative
            ? requestedRevision.ContextReference()!
            : requestedRevision.AsString();
        if (_changeIdentity.IsEmpty)
        {
            return Identity.ComputeIdentity(
                "ChangeIdentity",
                ctxRef,
                _name,
                _mainConfigFile.GetIdentifier(),
                _workflowOptions.WorkflowIdentityUser);
        }
        var sb = new StringBuilder();
        foreach (var token in _changeIdentity)
        {
            if (token.GetTokenType() == TokenType.Literal)
            {
                sb.Append(token.GetValue());
            }
            else if (token.GetValue().Equals(Workflow.COPYBARA_CONFIG_PATH_IDENTITY_VAR, StringComparison.Ordinal))
            {
                sb.Append(_mainConfigFile.GetIdentifier());
            }
            else if (token.GetValue().Equals(Workflow.COPYBARA_WORKFLOW_NAME_IDENTITY_VAR, StringComparison.Ordinal))
            {
                sb.Append(_name);
            }
            else if (token.GetValue().Equals(Workflow.COPYBARA_REFERENCE_IDENTITY_VAR, StringComparison.Ordinal))
            {
                sb.Append(ctxRef);
            }
            else if (token.GetValue().StartsWith(Workflow.COPYBARA_REFERENCE_LABEL_VAR, StringComparison.Ordinal))
            {
                string label = token.GetValue().Substring(Workflow.COPYBARA_REFERENCE_LABEL_VAR.Length);
                string? labelValue = transformWork.GetLabel(label);
                if (labelValue == null)
                {
                    _console.Warn(string.Format(
                        CultureInfo.InvariantCulture,
                        "Couldn't find label '{0}'. Using the default identity algorithm",
                        label));
                    return Identity.ComputeIdentity(
                        "ChangeIdentity",
                        ctxRef,
                        _name,
                        _mainConfigFile.GetIdentifier(),
                        _workflowOptions.WorkflowIdentityUser);
                }
                sb.Append(labelValue);
            }
        }
        return Identity.HashIdentity(
            new ToStringHelper("custom_identity").Add("text", sb.ToString()),
            _workflowOptions.WorkflowIdentityUser);
    }

    public ConfigFile GetMainConfigFile() => _mainConfigFile;

    public Profiler.Profiler Profiler() => _generalOptions.Profiler();

    public EventMonitors EventMonitors() => _generalOptions.EventMonitors();

    internal Func<ImmutableDictionary<string, ConfigFile>> GetAllConfigFiles() => _allConfigFiles;

    public GeneralOptions GetGeneralOptions() => _generalOptions;

    public IReadOnlyList<IAction> GetAfterMigrationActions() => _afterMigrationActions;

    public IReadOnlyList<IAction> GetAfterAllMigrationActions() => _afterAllMigrationActions;

    internal IReadOnlyList<DestinationEffect> RunHooks(
        IReadOnlyList<DestinationEffect> effects,
        IReadOnlyList<IAction> actions,
        LazyResourceLoader<IEndpoint> originEndpoint,
        LazyResourceLoader<IEndpoint> destinationEndpoint,
        IRevision resolvedRef)
    {
        var console = new Transform.SkylarkConsole(GetConsole());

        var hookDestinationEffects = new List<DestinationEffect>();
        foreach (var action in actions)
        {
            using (Profiler().Start(action.GetName()))
            {
                // logger.atInfo().log("Running after migration hook: %s", action.getName());
                // TODO(port): reconcile - FinishHookContext / Action.run(context) is not yet ported.
                // The hook context construction and dispatch will be restored once feedback/
                // FinishHookContext lands. For now we skip running the hook to preserve compilation.
                _ = action;
                _ = originEndpoint;
                _ = destinationEndpoint;
                _ = resolvedRef;
                _ = console;
            }
        }
        var builder = ImmutableArray.CreateBuilder<DestinationEffect>();
        builder.AddRange(effects);
        builder.AddRange(hookDestinationEffects);
        return builder.ToImmutable();
    }

    internal ImmutableArray<Token> GetChangeIdentity() => _changeIdentity;

    public bool IsSetRevId() => _setRevId;

    internal bool IsSmartPrune() => _smartPrune;

    public bool IsMergeImport() => _mergeImport != null;

    public MergeImportConfiguration? GetMergeImport() => _mergeImport;

    public bool IsConsistencyFileMergeImport() =>
        IsMergeImport()
        && GetMergeImport()!.UseConsistencyFile()
        && GetConsistencyFilePath() != null;

    // return whether the consistency file merge behavior has been toggled off
    public bool DisableConsistencyMergeImport() => _workflowOptions.DisableConsistencyMergeImport;

    public string? GetConsistencyFilePath() => _consistencyFileConfig?.Path();

    public ConsistencyFileConfiguration? GetConsistencyFileConfig() => _consistencyFileConfig;

    public AutoPatchfileConfiguration? GetAutoPatchfileConfiguration() => _autoPatchfileConfiguration;

    public bool IsMigrateNoopChanges() => _migrateNoopChanges;

    public string? CustomRevId() => _customRevId;

    public Glob GetReversibleCheckIgnoreFiles() => _reversibleCheckIgnoreFiles;

    public string? GetExpectedFixedRef() => _expectedFixedRef;

    public string? GetPinnedFixedRef() => _pinnedFixedRef;

    public ImmutableArray<StarlarkThread.CallStackEntry> GetDefinitionStack() => _definitionStack;

    /// <summary>
    /// Returns the Starlark call stack's captured local variables for each level.
    ///
    /// <para>These are only filled if the capture-definition-stack-locals option is set on the
    /// workflow. The outer list aligns to the definition stack's order. The inner map is a mapping of
    /// local variable names to string representations of the variable values, for each level of the
    /// stack.</para>
    /// </summary>
    public ImmutableArray<ImmutableDictionary<string, string>> GetDefinitionStackLocals() =>
        _definitionStackLocals;
}
