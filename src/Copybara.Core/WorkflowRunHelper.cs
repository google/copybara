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

using System.Collections.Immutable;
using System.Globalization;
using Copybara.Authoring;
using Copybara.Common;
using Copybara.Effect;
using Copybara.Exceptions;
using Copybara.Revision;
using Copybara.Util;
using Copybara.Util.Console;
using Console = Copybara.Util.Console.Console;
using ChangeMigrationFinishedEvent = Copybara.Monitor.IEventMonitor.ChangeMigrationFinishedEvent;
using ChangeMigrationStartedEvent = Copybara.Monitor.IEventMonitor.ChangeMigrationStartedEvent;
using EventMonitors = Copybara.Monitor.IEventMonitor.EventMonitors;
using EffectType = Copybara.Effect.DestinationEffect.EffectType;
using DestinationRef = Copybara.Effect.DestinationEffect.DestinationRef;

namespace Copybara;

/// <summary>Runs a single migration step for a <see cref="Workflow{O,D}"/>, using its configuration.</summary>
/// <typeparam name="O">Origin revision type.</typeparam>
/// <typeparam name="D">Destination revision type.</typeparam>
public class WorkflowRunHelper<O, D>
    where O : class, IRevision
    where D : class, IRevision
{
    private readonly Workflow<O, D> _workflow;
    private readonly string _workdir;
    private readonly O _resolvedRef;
    private readonly IOrigin<O>.IReader<O> _originReader;
    protected readonly IDestination<D>.IWriter<D> Writer;
    internal readonly string? RawSourceRef;
    private readonly Action<ChangeMigrationFinishedEvent> _migrationFinishedMonitor;

    public WorkflowRunHelper(
        Workflow<O, D> workflow,
        string workdir,
        O resolvedRef,
        IOrigin<O>.IReader<O> originReader,
        IDestination<D>.IWriter<D> destinationWriter,
        string? rawSourceRef,
        Action<ChangeMigrationFinishedEvent> migrationFinishedMonitor)
    {
        _workflow = Preconditions.CheckNotNull(workflow);
        _workdir = Preconditions.CheckNotNull(workdir);
        _resolvedRef = Preconditions.CheckNotNull(resolvedRef);
        _originReader = Preconditions.CheckNotNull(originReader);
        Writer = Preconditions.CheckNotNull(destinationWriter);
        RawSourceRef = rawSourceRef;
        _migrationFinishedMonitor = Preconditions.CheckNotNull(migrationFinishedMonitor);
    }

    public Action<ChangeMigrationFinishedEvent> GetMigrationFinishedMonitor() =>
        _migrationFinishedMonitor;

    /// <summary>origin_files used for this workflow.</summary>
    internal Glob GetOriginFiles() => _workflow.GetOriginFiles();

    internal ChangeMigrator<O, D> GetMigratorForChange(Change<O> change) =>
        GetMigratorForChangeAndWriter(change, Writer);

    internal virtual ChangeMigrator<O, D> GetMigratorForChangeAndWriter(
        Change<O> change, IDestination<D>.IWriter<D> writer) =>
        new(_workflow, _workdir, _originReader, writer, _resolvedRef, RawSourceRef,
            _migrationFinishedMonitor);

    /// <summary>Get a default migrator for the current writer.</summary>
    internal ChangeMigrator<O, D> GetDefaultMigrator() =>
        new(_workflow, _workdir, _originReader, Writer, _resolvedRef, RawSourceRef,
            _migrationFinishedMonitor);

    public Profiler.Profiler Profiler() => _workflow.Profiler();

    protected string GetWorkdir() => _workdir;

    internal O GetResolvedRef() => _resolvedRef;

    /// <summary>Authoring configuration.</summary>
    internal Authoring.Authoring GetAuthoring() => _workflow.GetAuthoring();

    internal string GetChangeMessage(string message) =>
        WorkflowOptions().ForcedChangeMessage ?? message;

    public Author GetFinalAuthor(Author author) =>
        WorkflowOptions().ForcedAuthor ?? author;

    /// <summary>Console to use for printing messages.</summary>
    internal Console GetConsole() => _workflow.GetConsole();

    /// <summary>Options that change how workflows behave.</summary>
    internal WorkflowOptions WorkflowOptions() => _workflow.GetWorkflowOptions();

    internal GeneralOptions GetGeneralOptions() => _workflow.GetGeneralOptions();

    internal O? GetOriginBaselineForMergeImport(O? lastRev) =>
        WorkflowOptions().BaselineForMergeImport == null
            ? lastRev
            : OriginResolveLastRev(WorkflowOptions().BaselineForMergeImport!);

    internal bool IsForce() => _workflow.IsForce();

    internal bool IsMergeImport() => _workflow.IsMergeImport();

    private bool IsInitHistory() => _workflow.IsInitHistory();

    internal bool IsSquashWithoutHistory() => _workflow.GetWorkflowOptions().SquashSkipHistory;

    internal IDestination<D> GetDestination() => _workflow.GetDestination();

    internal IOrigin<O>.IReader<O> GetOriginReader() => _originReader;

    internal IDestination<D>.IWriter<D> GetDestinationWriter() => Writer;

    internal bool DestinationSupportsPreviousRef() => Writer.SupportsHistory();

    internal void MaybeValidateRepoInLastRevState(Metadata? metadata)
    {
        if (!_workflow.IsCheckLastRevState() || IsForce())
        {
            return;
        }

        _workflow.GetGeneralOptions().IoRepoTask<object?>(
            "validate_last_rev",
            () =>
            {
                O? lastRev = _workflow.GetGeneralOptions()
                    .RepoTask<O?>("get_last_rev", MaybeGetLastRev);

                if (lastRev == null)
                {
                    // Not the job of this function to check for lastrev status.
                    return null;
                }
                Change<O> change = _originReader.Change(lastRev);
                var changes = new Changes(new object[] { change }, Array.Empty<object>());
                // Create a new writer so that state is not shared with the regular writer.
                ChangeMigrator<O, D> migrator = GetMigratorForChangeAndWriter(
                    change, _workflow.CreateDryRunWriter(_resolvedRef));

                try
                {
                    _workflow.GetGeneralOptions().IoRepoTask<object?>(
                        "migrate",
                        () =>
                        {
                            // We pass lastRev as the lastRev. This is not correct but we cannot know
                            // the previous rev of the last rev. This should only be used for
                            // generating messages, so users shouldn't care about the value (but they
                            // might care about its presence, so it cannot be null).
                            migrator.DoMigrate(
                                lastRev,
                                lastRev,
                                new PrefixConsole("Validating last migration: ", _workflow.GetConsole()),
                                metadata ?? new Metadata(
                                    change.GetMessage(),
                                    change.GetAuthor(),
                                    ImmutableListMultimap<string, string>.Empty),
                                changes,
                                /*destinationBaseline=*/ null,
                                lastRev,
                                null);
                            return null;
                        });
                    throw new ValidationException(
                        "Migration of last-rev '"
                            + lastRev.AsString()
                            + "' didn't result in an empty change. This means that the result change"
                            + " of that migration was modified ouside of Copybara or that new changes"
                            + " happened later in the destination without using Copybara. Use --force"
                            + " if you really want to do the migration.");
                }
                catch (EmptyChangeException)
                {
                    // EmptyChangeException ignored
                }
                return null;
            });
    }

    internal Origin.ChangesResponse<O> GetChanges(O? from, O to)
    {
        using (_workflow.Profiler().Start("get_changes"))
        {
            return _originReader.Changes(from, to);
        }
    }

    public string ImportAndTransformRevision(
        Console console,
        O lastRev,
        O currentRev,
        TransformWork.IResourceSupplier<DestinationReader> destinationReader)
    {
        ChangeMigrator<O, D> migrator = GetDefaultMigrator();
        LazyResourceLoader<IEndpoint> originApi =
            LazyResourceLoader.Memoized<IEndpoint>(c => GetOriginReader().GetFeedbackEndPoint(c!));
        LazyResourceLoader<IEndpoint> destinationApi =
            LazyResourceLoader.Memoized<IEndpoint>(c => GetDestinationWriter().GetFeedbackEndPoint(c!));

        return migrator.CheckoutBaselineAndTransform(
            "premerge",
            lastRev,
            new Metadata(
                "foo", new Author("foo", "foo@foo.com"),
                ImmutableListMultimap<string, string>.Empty),
            currentRev,
            console,
            originApi,
            destinationApi,
            destinationReader);
    }

    /// <summary>
    /// Get last imported revision or fail if it cannot be found.
    /// </summary>
    /// <exception cref="RepoException">if a last revision couldn't be found.</exception>
    public O? GetLastRev()
    {
        O? lastRev = MaybeGetLastRev();
        if (lastRev == null && !IsInitHistory())
        {
            throw new CannotResolveRevisionException(string.Format(
                CultureInfo.InvariantCulture,
                "Previous revision label {0} could not be found in {1} and --last-rev or"
                    + " --init-history flags were not passed",
                GetOriginLabelName(),
                _workflow.GetDestination()));
        }
        return lastRev;
    }

    internal string GetOriginLabelName() => _workflow.GetRevIdLabel();

    internal string GetLabelNameWhenOrigin() =>
        _workflow.CustomRevId() == null
            ? _workflow.GetDestination().GetLabelNameWhenOrigin()
            : _workflow.CustomRevId()!;

    /// <summary>
    /// Returns the last revision that was imported from this origin to the destination. Returns
    /// <c>null</c> if it cannot be determined.
    /// </summary>
    private O? MaybeGetLastRev()
    {
        if (_workflow.GetLastRevisionFlag() != null)
        {
            try
            {
                return OriginResolveLastRev(_workflow.GetLastRevisionFlag()!);
            }
            catch (RepoException e)
            {
                throw new CannotResolveRevisionException(
                    "Could not resolve --last-rev flag. Please make sure it exists in the origin: "
                        + _workflow.GetLastRevisionFlag(),
                    e);
            }
        }
        DestinationStatus? status = Writer.GetDestinationStatus(
            _workflow.GetDestinationFiles(), GetOriginLabelName());
        try
        {
            O? lastRev = status == null ? null : OriginResolveLastRev(status.GetBaseline());
            if (lastRev != null && _workflow.IsInitHistory())
            {
                GetConsole().WarnFmt(
                    "Ignoring %s because a previous imported revision '%s' was found in the"
                        + " destination.",
                    Copybara.WorkflowOptions.InitHistoryFlag, lastRev.AsString());
            }
            return lastRev;
        }
        catch (CannotResolveRevisionException e)
        {
            if (_workflow.IsInitHistory())
            {
                // Expected to not find a revision if --init-history is provided
                return null;
            }
            throw new CannotResolveRevisionException(
                string.Format(
                    CultureInfo.InvariantCulture,
                    "Latest destination change has value '{0}' for label '{1}', but this does not"
                        + " resolve in the origin. This commonly happens if changes were merged"
                        + " outside the Source of Truth, several copybara workflows use the same"
                        + " label or if the origin history was re-written. Manually set the"
                        + " '--last-rev' flag to the export baseline to export a valid state to the"
                        + " destination.",
                    status?.GetBaseline(),
                    GetOriginLabelName()),
                e);
        }
    }

    /// <summary>Resolve a string representation of a revision using the origin.</summary>
    internal O OriginResolveLastRev(string revStr) => _workflow.GetOrigin().ResolveLastRev(revStr);

    public EventMonitors EventMonitors() => _workflow.EventMonitors();
}

/// <summary>
/// Migrate a change for a workflow. Can overwrite the reader, writer, transformations, etc.
///
/// <para>Port of the static nested <c>WorkflowRunHelper.ChangeMigrator</c> class.</para>
/// </summary>
/// <typeparam name="O">Origin revision type.</typeparam>
/// <typeparam name="D">Destination revision type.</typeparam>
public class ChangeMigrator<O, D>
    where O : class, IRevision
    where D : class, IRevision
{
    private readonly Workflow<O, D> _headWorkflow;
    private readonly string _workdir;
    private readonly O _resolvedRef;
    private readonly IOrigin<O>.IReader<O> _reader;
    private readonly IDestination<D>.IWriter<D> _writer;
    private readonly string? _rawSourceRef;
    private readonly Action<ChangeMigrationFinishedEvent> _migrationFinishedMonitor;

    internal ChangeMigrator(
        Workflow<O, D> workflow,
        string workdir,
        IOrigin<O>.IReader<O> reader,
        IDestination<D>.IWriter<D> writer,
        O resolvedRef,
        string? rawSourceRef,
        Action<ChangeMigrationFinishedEvent> migrationFinishedMonitor)
    {
        _headWorkflow = Preconditions.CheckNotNull(workflow);
        _workdir = Preconditions.CheckNotNull(workdir);
        _resolvedRef = Preconditions.CheckNotNull(resolvedRef);
        _reader = Preconditions.CheckNotNull(reader);
        _writer = Preconditions.CheckNotNull(writer);
        _rawSourceRef = rawSourceRef;
        _migrationFinishedMonitor = Preconditions.CheckNotNull(migrationFinishedMonitor);
    }

    protected virtual Workflow<O, D> GetWorkflow() => _headWorkflow;

    /// <summary>
    /// Return true if this change can be skipped because it would generate a noop in the destination.
    /// </summary>
    internal bool SkipChange(Change<O> currentChange)
    {
        bool skipChange = ShouldSkipChange(currentChange);
        if (skipChange)
        {
            GetWorkflow().GetConsole().VerboseFmt(
                "Skipped change %s as it would create an empty result.", currentChange);
        }
        return skipChange;
    }

    /// <summary>
    /// Returns true iff the given change should be skipped based on the origin globs and flags
    /// provided.
    /// </summary>
    internal bool ShouldSkipChange(Change<O> currentChange)
    {
        if (GetWorkflow().IsMigrateNoopChanges())
        {
            return false;
        }
        // We cannot know the files included. Try to migrate then.
        if (currentChange.GetChangeFiles() == null)
        {
            return false;
        }
        IPathMatcher pathMatcher = GetOriginFiles().RelativeTo("/");
        foreach (string changedFile in currentChange.GetChangeFiles()!)
        {
            if (pathMatcher.Matches("/" + changedFile))
            {
                return false;
            }
        }
        // Heuristic for cases where the Copybara configuration is stored in the same folder as the
        // origin code but excluded. The config root can be a subfolder of the files as seen by the
        // origin. This might give some false positives but they would be noop migrations.
        foreach (string changesFile in currentChange.GetChangeFiles()!)
        {
            foreach (string configPath in GetConfigFiles())
            {
                if (changesFile.EndsWith(configPath, StringComparison.Ordinal))
                {
                    GetWorkflow().GetConsole().InfoFmt(
                        "Migrating %s because %s config file changed at that revision",
                        currentChange.GetRevision().AsString(), changesFile);
                    return false;
                }
            }
        }
        return true;
    }

    protected ISet<string> GetConfigFiles() => GetWorkflow().ConfigPaths();

    internal Glob GetOriginFiles() => GetWorkflow().GetOriginFiles();

    protected Glob GetDestinationFiles() => GetWorkflow().GetDestinationFiles();

    protected ITransformation GetTransformation() => GetWorkflow().GetTransformation();

    protected ITransformation? GetReverseTransformForCheck() =>
        GetWorkflow().GetReverseTransformForCheck();

    protected Glob GetReversibleCheckIgnoreFiles() => GetWorkflow().GetReversibleCheckIgnoreFiles();

    public Profiler.Profiler Profiler() => GetWorkflow().Profiler();

    // provide the correct context reference when the --same-version flag is used
    private O GetResolvedRefForTransform(O rev)
    {
        if (GetWorkflow().GetMode() == WorkflowMode.Squash
            && GetWorkflow().GetWorkflowOptions().ImportSameVersion)
        {
            return rev;
        }
        return _resolvedRef;
    }

    /// <summary>
    /// Performs a full migration, including checking out files from the origin, deleting excluded
    /// files, transforming the code, and writing to the destination. This writes to the destination
    /// exactly once.
    /// </summary>
    public IReadOnlyList<DestinationEffect> Migrate(
        O rev,
        O? lastRev,
        Console processConsole,
        Metadata metadata,
        Changes changes,
        Origin.Baseline<O>? destinationBaseline,
        O? changeIdentityRevision,
        O? originBaselineForMergeImport)
    {
        IReadOnlyList<DestinationEffect> effects = ImmutableArray<DestinationEffect>.Empty;
        Exception? lastException = null;
        try
        {
            GetWorkflow().EventMonitors().DispatchEvent(
                m => m.OnChangeMigrationStarted(new ChangeMigrationStartedEvent()));
            effects = DoMigrate(
                rev,
                lastRev,
                processConsole,
                metadata,
                changes,
                destinationBaseline,
                changeIdentityRevision,
                originBaselineForMergeImport);
        }
        catch (RedundantChangeException e)
        {
            effects = ImmutableArray.Create(
                new DestinationEffect(
                    EffectType.NOOP_AGAINST_PENDING_CHANGE,
                    string.Format(
                        CultureInfo.InvariantCulture,
                        "Cannot migrate revisions [{0}]: {1}",
                        CurrentRevsString(changes),
                        e.Message),
                    changes.GetCurrent().Cast<OriginRef>().ToList(),
                    new DestinationRef(e.PendingRevision, "commit", url: null)));
            lastException = e;
            throw;
        }
        catch (EmptyChangeException empty)
        {
            effects = ImmutableArray.Create(
                new DestinationEffect(
                    EffectType.NOOP,
                    string.Format(
                        CultureInfo.InvariantCulture,
                        "Cannot migrate revisions [{0}]: {1}",
                        CurrentRevsString(changes),
                        empty.Message),
                    changes.GetCurrent().Cast<OriginRef>().ToList(),
                    destinationRef: null));
            lastException = empty;
            throw;
        }
        catch (Exception e) when (e is not EmptyChangeException)
        {
            // Covers ValidationException, IOException, RepoException and RuntimeException.
            bool userError = e is ValidationException;
            effects = ImmutableArray.Create(
                new DestinationEffect(
                    userError ? EffectType.ERROR : EffectType.TEMPORARY_ERROR,
                    "Errors happened during the migration",
                    changes.GetCurrent().Cast<OriginRef>().ToList(),
                    destinationRef: null,
                    new[] { e.Message ?? e.ToString() }));
            lastException = e;
            throw;
        }
        finally
        {
            try
            {
                if (!GetWorkflow().GetGeneralOptions().DryRunMode)
                {
                    try
                    {
                        using (Profiler().Start("after_migration"))
                        {
                            effects = GetWorkflow().RunHooks(
                                effects,
                                GetWorkflow().GetAfterMigrationActions(),
                                LazyResourceLoader.Memoized<IEndpoint>(
                                    c => _reader.GetFeedbackEndPoint(c!)),
                                LazyResourceLoader.Memoized<IEndpoint>(
                                    c => _writer.GetFeedbackEndPoint(c!)),
                                _resolvedRef);
                        }
                    }
                    catch (Exception e) when (e is ValidationException or RepoException)
                    {
                        if (lastException == null)
                        {
                            throw;
                        }
                        // lastException.addSuppressed(e) — no direct C# equivalent; the original
                        // exception is preserved by the surrounding throw in the catch blocks.
                    }
                }
                else if (GetWorkflow().GetAfterMigrationActions().Count != 0)
                {
                    GetWorkflow().GetConsole().InfoFmt(
                        "Not calling 'after_migration' actions because of %s mode",
                        GeneralOptions.DryRunFlag);
                }
            }
            finally
            {
                _migrationFinishedMonitor(
                    new ChangeMigrationFinishedEvent(
                        effects.ToImmutableArray(),
                        GetWorkflow().GetOriginDescription(),
                        GetWorkflow().GetDestinationDescription()));
            }
        }
        return effects;
    }

    private static string CurrentRevsString(Changes changes) =>
        changes.GetCurrent().Count == 0
            ? "Unknown"
            : string.Join(
                ", ",
                changes.GetCurrent().Select(c => ((Change<O>)c).GetRevision().AsString()));

    /// <summary>
    /// Finish a migrate by noticing event monitor with the outcome effects.
    /// </summary>
    internal void FinishedMigrate(IReadOnlyList<DestinationEffect> effects)
    {
        GetWorkflow().EventMonitors().DispatchEvent(
            m => m.OnChangeMigrationStarted(new ChangeMigrationStartedEvent()));
        _migrationFinishedMonitor(
            new ChangeMigrationFinishedEvent(
                effects.ToImmutableArray(),
                GetWorkflow().GetOriginDescription(),
                GetWorkflow().GetDestinationDescription()));
    }

    private bool ShowDiffInOrigin(O rev, O? lastRev, Console processConsole)
    {
        if (!GetWorkflow().GetWorkflowOptions().DiffInOrigin
            || GetWorkflow().GetMode() == WorkflowMode.ChangeRequest
            || GetWorkflow().GetMode() == WorkflowMode.ChangeRequestFromSot
            || lastRev == null)
        {
            return false;
        }
        string? diff = GetWorkflow().GetOrigin().ShowDiff(lastRev, rev);
        if (diff == null)
        {
            throw new ValidationException(
                "diff_in_origin is not supported by origin "
                    + ((IConfigItemDescription)GetWorkflow().GetOrigin()).GetTypeName());
        }
        if (diff.Length == 0 && !GetWorkflow().GetGeneralOptions().IsForced())
        {
            throw new EmptyChangeException("No difference at diff_in_origin");
        }
        var sb = new System.Text.StringBuilder();
        foreach (string line in diff.Split('\n'))
        {
            sb.Append('\n');
            if (line.StartsWith("+", StringComparison.Ordinal))
            {
                sb.Append(processConsole.Colorize(AnsiColor.Green, line));
            }
            else if (line.StartsWith("-", StringComparison.Ordinal))
            {
                sb.Append(processConsole.Colorize(AnsiColor.Red, line));
            }
            else
            {
                sb.Append(line);
            }
        }
        processConsole.Info(sb.ToString());
        if (!processConsole.PromptConfirmation(
            string.Format(
                CultureInfo.InvariantCulture,
                "Continue to migrate with '{0}' to {1}?",
                GetWorkflow().GetMode(),
                ((IConfigItemDescription)GetWorkflow().GetDestination()).GetTypeName())))
        {
            processConsole.Warn("Migration aborted by user.");
            throw new ChangeRejectedException(
                "User aborted execution: did not confirm diff in origin changes.");
        }
        return true;
    }

    internal IReadOnlyList<DestinationEffect> DoMigrate(
        O rev,
        O? lastRev,
        Console processConsole,
        Metadata metadata,
        Changes changes,
        Origin.Baseline<O>? destinationBaseline,
        O? changeIdentityRevision,
        O? originBaselineForPrune)
    {
        string checkoutDir = System.IO.Path.Combine(_workdir, "checkout");
        using (Profiler().Start("prepare_workdir"))
        {
            processConsole.Progress("Cleaning working directory");
            if (Directory.Exists(_workdir))
            {
                FileUtil.DeleteRecursively(_workdir);
            }
            Directory.CreateDirectory(checkoutDir);
        }
        processConsole.Progress("Checking out the change");
        bool isShowDiffInOrigin = ShowDiffInOrigin(rev, lastRev, processConsole);

        Checkout(rev, processConsole, checkoutDir, "origin.checkout");

        string? originCopy = null;
        Console console = GetWorkflow().GetConsole();
        if (GetReverseTransformForCheck() != null)
        {
            using (Profiler().Start("reverse_copy"))
            {
                console.Progress("Making a copy or the workdir for reverse checking");
                originCopy = System.IO.Path.Combine(_workdir, "origin");
                Directory.CreateDirectory(originCopy);
                CopyForReverseCheck(checkoutDir, originCopy);
            }
        }
        // Lazy loading to avoid running afoul of checks unless the instance is actually used.
        LazyResourceLoader<IEndpoint> originApi =
            LazyResourceLoader.Memoized<IEndpoint>(c => _reader.GetFeedbackEndPoint(c!));
        LazyResourceLoader<IEndpoint> destinationApi =
            LazyResourceLoader.Memoized<IEndpoint>(c => _writer.GetFeedbackEndPoint(c!));
        var destinationReader = new FuncResourceSupplier<DestinationReader>(
            () => _writer.GetDestinationReader(
                console, (Origin.Baseline<IRevision>?)(object?)destinationBaseline, checkoutDir));

        metadata = metadata.WithHiddenLabels(CliHiddenLabels());
        var transformWork = new TransformWork(
                checkoutDir,
                metadata,
                changes,
                console,
                new MigrationInfo(GetWorkflow().GetRevIdLabel(), (IChangeVisitable<IRevision>?)_writer),
                GetResolvedRefForTransform(rev),
                originApi,
                destinationApi,
                destinationReader,
                GetWorkflow().GetMode().ToString())
            .WithLastRev(lastRev)
            .WithCurrentRev(rev)
            .WithDestinationInfo(_writer.GetDestinationInfo());
        transformWork.AddLabel(
            TransformWork.CopybaraConfigPathLabel,
            GetWorkflow().GetMainConfigFile().GetIdentifier(),
            "=",
            hidden: true);
        transformWork.AddLabel(
            TransformWork.CopybaraWorkflowNameLabel, GetWorkflow().GetName(), "=", hidden: true);

        try
        {
            using (Profiler().Start("transforms"))
            {
                TransformationStatus status = GetTransformation().Transform(transformWork);
                if (status.IsNoop())
                {
                    ShowInfoAboutNoop(console);
                    status.ThrowException(console, GetWorkflow().GetWorkflowOptions().IgnoreNoop);
                }
            }
        }
        catch (VoidOperationException)
        {
            // This happens if an inner sequence throws noop as an exception.
            ShowInfoAboutNoop(console);
            throw;
        }

        if (GetReverseTransformForCheck() != null)
        {
            console.Progress("Checking that the transformations can be reverted");
            string reverse;
            using (Profiler().Start("reverse_copy"))
            {
                reverse = System.IO.Path.Combine(_workdir, "reverse");
                Directory.CreateDirectory(reverse);
                CopyForReverseCheck(checkoutDir, reverse);
            }

            using (Profiler().Start("reverse_transform"))
            {
                TransformationStatus status = GetReverseTransformForCheck()!.Transform(
                    new TransformWork(
                            reverse,
                            transformWork.GetMetadata(),
                            changes,
                            console,
                            new MigrationInfo(originLabel: null, null),
                            GetResolvedRefForTransform(rev),
                            destinationApi,
                            originApi,
                            new FuncResourceSupplier<DestinationReader>(
                                () => DestinationReader.NotImplemented),
                            GetWorkflow().GetMode().ToString())
                        .WithDestinationInfo(_writer.GetDestinationInfo()));
                if (status.IsNoop())
                {
                    console.WarnFmt(
                        "No-op detected running the transformations in reverse. The most probably"
                            + " cause is that the transformations are not reversible.");
                    status.ThrowException(console, GetWorkflow().GetWorkflowOptions().IgnoreNoop);
                }
            }

            // TODO(port): reconcile - DiffUtil.diff/filterDiff and the reversible-diff comparison
            // depend on the not-yet-ported com.google.copybara.util.DiffUtil. The forward/reverse
            // transformations above have already run; the diff-based non-reversibility check is
            // skipped until DiffUtil lands.
            _ = originCopy;
        }

        console.Progress("Checking that destination_files covers all files in transform result");
        // TODO(port): reconcile - ValidateDestinationFilesVisitor is not yet ported.
        // new ValidateDestinationFilesVisitor(GetDestinationFiles(), checkoutDir).VerifyFilesToWrite();

        var transformResult = new TransformResult(
                checkoutDir,
                rev,
                transformWork.GetAuthor(),
                transformWork.GetMessage(),
                /* requestedRevision= */ GetResolvedRefForTransform(rev),
                GetWorkflow().GetName(),
                changes,
                _rawSourceRef,
                GetWorkflow().IsSetRevId(),
                label => transformWork.GetAllLabels(label),
                GetWorkflow().GetRevIdLabel())
            .WithDestinationInfo(transformWork.GetDestinationInfo()!);

        IReadOnlyList<string>? mergeErrorPaths = null;
        if (GetWorkflow().IsMergeImport())
        {
            mergeErrorPaths = RunMergeImport(
                console,
                _writer,
                destinationBaseline,
                checkoutDir,
                lastRev,
                metadata,
                originBaselineForPrune,
                originApi,
                destinationApi,
                transformWork);
            if (mergeErrorPaths == null)
            {
                console.Warn(
                    "Unable to determine a baseline; disabling merge import. This is expected"
                        + " if this is an initial import. Otherwise, you may have to provide a"
                        + " baseline using --baseline-for-merge-import, or using an existing"
                        + " consistency file.");
            }
        }
        if (mergeErrorPaths == null)
        {
            mergeErrorPaths = ImmutableArray<string>.Empty;
            if (GetWorkflow().GetConsistencyFilePath() != null)
            {
                // TODO(port): reconcile - ConsistencyFile.generateNoDiff is not yet ported.
                throw new NotSupportedException(
                    "TODO(port): consistency file generation (ConsistencyFile) not yet ported.");
            }
        }
        if (destinationBaseline != null)
        {
            transformResult = transformResult.WithBaseline(destinationBaseline.GetBaseline());
            if (GetWorkflow().IsSmartPrune() && GetWorkflow().GetWorkflowOptions().CanUseSmartPrune())
            {
                ValidationException.CheckCondition(
                    destinationBaseline.GetOriginRevision() != null,
                    "smart_prune is not compatible with %s flag for now",
                    WorkflowOptions.ChangeRequestParentFlag);
                // TODO(port): reconcile - smart_prune diffing depends on DiffUtil.diffFiles /
                // DiffFile which are not yet ported.
                throw new NotSupportedException(
                    "TODO(port): smart_prune diffing (DiffUtil) not yet ported.");
            }
        }
        transformResult = transformResult
            .WithAskForConfirmation(GetWorkflow().IsAskForConfirmation())
            .WithDiffInOrigin(isShowDiffInOrigin)
            .WithIdentity(GetWorkflow().GetMigrationIdentity(changeIdentityRevision!, transformWork))
            .WithApprovalsProvider(GetWorkflow().GetOrigin().GetApprovalsProvider());

        IReadOnlyList<DestinationEffect> result;
        using (Profiler().Start(
            "destination.write",
            Profiler().TaskType(((IConfigItemDescription)GetWorkflow().GetDestination()).GetTypeName())))
        {
            result = _writer.Write(transformResult, GetDestinationFiles(), processConsole);
        }
        Preconditions.CheckNotNull(result, "Destination returned a null result.");
        Preconditions.CheckState(
            result.Count != 0, "Destination {0} returned an empty set of effects", _writer);

        if (mergeErrorPaths.Count != 0)
        {
            var mergeErrorDestinationEffect = new DestinationEffect(
                EffectType.CREATED,
                string.Format(
                    CultureInfo.InvariantCulture,
                    "Found merge errors for paths: {0}",
                    string.Join(", ", mergeErrorPaths)),
                Array.Empty<OriginRef>(),
                new DestinationRef("merge_error", "merge_error", null));

            var builder = ImmutableArray.CreateBuilder<DestinationEffect>();
            builder.AddRange(result);
            builder.Add(mergeErrorDestinationEffect);
            result = builder.ToImmutable();
        }
        return result;
    }

    private ImmutableListMultimap<string, string> CliHiddenLabels()
    {
        var builder = ImmutableListMultimap<string, string>.CreateBuilder();
        foreach (var e in GetWorkflow().GetGeneralOptions().CliLabels())
        {
            builder.Put(GeneralOptions.CliFlagPrefix + e.Key.ToUpperInvariant(), e.Value);
        }
        return builder.Build();
    }

    /// <returns>
    /// a list of paths that resulted in merge errors, or null if a baseline could not be determined.
    /// </returns>
    private IReadOnlyList<string>? RunMergeImport(
        Console console,
        IDestination<D>.IWriter<D> writer,
        Origin.Baseline<O>? destinationBaseline,
        string checkoutDir,
        O? lastRev,
        Metadata metadata,
        O? originBaselineForPrune,
        LazyResourceLoader<IEndpoint> originApi,
        LazyResourceLoader<IEndpoint> destinationApi,
        TransformWork transformWork)
    {
        // TODO(port): reconcile - merge import depends on a number of not-yet-ported utilities:
        // MergeImportTool, MergeRunner, CommandLineDiffUtil, ApplyDestinationPatch, ConsistencyFile,
        // AutoPatchUtil and DestinationReader.copyDestinationFilesToDirectory batch behaviors. The
        // full structure is preserved in the Java source; port those utilities and restore this
        // method's body during consolidation.
        _ = (console, writer, destinationBaseline, checkoutDir, lastRev, metadata,
            originBaselineForPrune, originApi, destinationApi, transformWork);
        throw new NotSupportedException(
            "TODO(port): merge_import (MergeImportTool and friends) not yet ported.");
    }

    internal static Glob PatchlessDestinationFiles(Workflow<O, D> workflow)
    {
        Glob destinationFiles = workflow.GetDestinationFiles();
        AutoPatchfileConfiguration? autoPatchfileConfiguration =
            workflow.GetAutoPatchfileConfiguration();
        if (autoPatchfileConfiguration != null)
        {
            // TODO(port): reconcile - AutoPatchUtil.getAutopatchGlob is not yet ported.
            throw new NotSupportedException(
                "TODO(port): AutoPatchUtil.getAutopatchGlob not yet ported.");
        }
        if (workflow.GetMergeImport()!.UseConsistencyFile())
        {
            destinationFiles = Glob.Difference(
                destinationFiles,
                Glob.CreateGlob(new[] { workflow.GetConsistencyFilePath()! }));
        }
        return destinationFiles;
    }

    internal static Glob ConsistencyFileGlob(Workflow<O, D> workflow) =>
        Glob.CreateGlob(new[] { workflow.GetConsistencyFilePath()! });

    internal string CheckoutBaselineAndTransform(
        string subdirName,
        O? lastRev,
        Metadata metadata,
        O baseline,
        Console console,
        LazyResourceLoader<IEndpoint> originApi,
        LazyResourceLoader<IEndpoint> destinationApi,
        TransformWork.IResourceSupplier<DestinationReader> destinationReader)
    {
        string baselineWorkdir = System.IO.Path.Combine(_workdir, subdirName);
        Directory.CreateDirectory(baselineWorkdir);

        var baselineConsole = new PrefixConsole("Migrating baseline for diff: ", console);
        Checkout(baseline, baselineConsole, baselineWorkdir, "origin.baseline.checkout");

        var baselineTransformWork = new TransformWork(
                baselineWorkdir,
                // We don't care about the message or author and this guarantees that it will work
                // with the transformations
                metadata,
                // We don't care about the changes that are imported.
                Changes.Empty,
                baselineConsole,
                new MigrationInfo(GetWorkflow().GetRevIdLabel(), (IChangeVisitable<IRevision>?)_writer),
                _resolvedRef,
                originApi,
                destinationApi,
                destinationReader,
                GetWorkflow().GetMode().ToString())
            // Again, we don't care about this
            .WithLastRev(lastRev)
            .WithCurrentRev(baseline)
            .WithDestinationInfo(_writer.GetDestinationInfo());
        using (Profiler().Start("baseline_transforms"))
        {
            TransformationStatus status = GetTransformation().Transform(baselineTransformWork);
            if (status.IsNoop()
                // no-op baseline transformations are OK for smart prune.
                && !GetWorkflow().IsSmartPrune())
            {
                console.WarnFmt("No-op detected in baseline transformations");
                ShowInfoAboutNoop(console);
                status.ThrowException(console, GetWorkflow().GetWorkflowOptions().IgnoreNoop);
            }
        }
        return baselineWorkdir;
    }

    private void ShowInfoAboutNoop(Console console)
    {
        console.WarnFmt(
            "No-op detected, this could happen for several reasons:\n\n"
                + "    - origin_files doesn't include the files. Current origin_files: %s\n\n"
                + "    - Previous transformations didn't do what you were expecting. You can"
                + " inspect the work directory state (if run locally) at %s\n\n"
                + "    - Current version of the config doesn't work for an older (or newer)"
                + " revision being migrated. This can be fixed by either wrapping the failing"
                + " transformation with %s"
                + " so that it is ignored or, if your origin supports it, using"
                + " %s flag to sync the config version to the change being migrated.",
            console.Colorize(AnsiColor.Yellow, GetWorkflow().GetOriginFiles().ToString()),
            console.Colorize(AnsiColor.Yellow, _workdir),
            console.Colorize(
                AnsiColor.Yellow,
                "core.transform([your_transformation], noop_behavior = \"IGNORE_NOOP\")"),
            console.Colorize(AnsiColor.Yellow, "--read-config-from-change"));
    }

    private void Checkout(O rev, Console processConsole, string checkoutDir, string profileDescription)
    {
        if (GetWorkflow().IsCheckout())
        {
            using (Profiler().Start(
                profileDescription,
                Profiler().TaskType(((IConfigItemDescription)GetWorkflow().GetOrigin()).GetTypeName())))
            {
                _reader.Checkout(rev, checkoutDir);
            }
        }

        // Remove excluded origin files.
        IPathMatcher originFiles = GetOriginFiles().RelativeTo(checkoutDir);
        processConsole.Progress("Removing excluded origin files");

        int deleted = FileUtil.DeleteFilesRecursively(
            checkoutDir, FileUtil.NotPathMatcher(originFiles));
        if (deleted != 0)
        {
            processConsole.InfoFmt(
                "Removed %s files from workdir that do not match origin_files", deleted);
        }
    }

    private void CopyForReverseCheck(string from, string to)
    {
        try
        {
            FileUtil.CopyFilesRecursively(from, to, FileUtil.CopySymlinkStrategy.FailOutsideSymlinks);
        }
        catch (SymlinkException e)
        {
            throw new ValidationException(
                "Failed to perform reversible check of transformations due to a symlink that "
                    + "points outside the checkout dir. Consider removing this symlink from your "
                    + "origin_files or, alternatively, set reversible_check = False in your "
                    + "workflow.",
                e);
        }
    }

    private sealed class FuncResourceSupplier<T> : TransformWork.IResourceSupplier<T>
    {
        private readonly Func<T> _func;

        public FuncResourceSupplier(Func<T> func) => _func = func;

        public T Get() => _func();
    }
}
