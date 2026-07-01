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
using Copybara.Authoring;
using Copybara.Util;

namespace Copybara;

/// <summary>
/// Arguments for Workflow components.
/// </summary>
public class WorkflowOptions : IOption
{
    internal const string ChangeRequestParentFlag = "--change-request-parent";
    internal const string ChangeRequestParentFlagAlt = "--change_request_parent";

    internal const string ReadConfigFromChangeFlag = "--read-config-from-change";
    internal const string ChangeRequestFromSotLimitFlag = "--change-request-from-sot-limit";
    public const string SkipTransformFlagName = "--skip-transforms";
    internal const string InitHistoryFlag = "--init-history";
    public const string CheckLastRevStateFlag = "--check-last-rev-state";

    [Flag(
        new[] { ChangeRequestParentFlag, ChangeRequestParentFlagAlt },
        "Commit revision to be used as parent when importing a commit using CHANGE_REQUEST"
            + " workflow mode. This shouldn't be needed in general as Copybara is able to detect"
            + " the parent commit message.")]
    public string ChangeBaseline { get; set; } = "";

    /// <summary>
    /// Public so that it can be used programmatically.
    /// </summary>
    [Flag("--last-rev", "Last revision that was migrated to the destination")]
    public string? LastRevision { get; set; }

    [Flag(
        "--expected-fixed-ref",
        "The fixed reference that we expect the migrate ref argument to resolve to. If they do"
            + " not match, the tool will exit with a NOOP status.",
        Hidden = true)]
    public string? ExpectedFixedRef { get; set; }

    [Flag(
        "--pinned-fixed-ref",
        "The fixed reference that we pin the migration to. The reference passed in should resolve"
            + " to this SHA1, or the commit referenced by this SHA1 should be an ancestor of the"
            + " provided reference. If this is not the case, the tool will exit with a NOOP"
            + " status.",
        Hidden = true)]
    public string? PinnedFixedRef { get; set; }

    [Flag(
        "--same-version",
        "Re-import the last version imported. This is useful for example to check that"
            + " a refactor in a copy.bara.sky file doesn't introduce accidental changes.")]
    public bool ImportSameVersion { get; set; }

    [Flag(
        InitHistoryFlag,
        "Import all the changes from the beginning of the history up to the resolved"
            + " ref. For 'ITERATIVE' workflows this will import individual changes since the first "
            + "one. For 'SQUASH' it will import the squashed change up to the resolved ref. "
            + "WARNING: Use with care, this flag should be used only for the very first run of "
            + "Copybara for a workflow.")]
    public bool InitHistory { get; set; }

    [Flag(
        "--iterative-limit-changes",
        "Import just a number of changes instead of all the pending ones")]
    public int IterativeLimitChanges { get; set; } = int.MaxValue;

    [Flag(
        "--ignore-noop",
        "Only warn about operations/transforms that didn't have any effect."
            + " For example: A transform that didn't modify any file, non-existent origin"
            + " directories, etc.")]
    public bool IgnoreNoop { get; set; }

    [Flag(
        "--info-include-versions",
        "Include upstream versions in the info command output.")]
    public bool InfoIncludeVersions { get; set; } = true;

    [Flag(
        "--squash-skip-history",
        "Avoid exposing the history of changes that are being migrated. This is"
            + " useful when we want to migrate a new repository but we don't want to expose all"
            + " the change history to metadata.squash_notes.")]
    public bool SquashSkipHistory { get; set; }

    [Flag(
        "--import-noop-changes",
        "By default Copybara will only try to migrate changes that could affect the"
            + " destination. Ignoring changes that only affect excluded files in origin_files. This"
            + " flag disables that behavior and runs for all the changes.")]
    public bool MigrateNoopChanges { get; set; }

    [Flag(
        "--workflow-identity-user",
        "Use a custom string as a user for computing change identity")]
    public string? WorkflowIdentityUser { get; set; }

    [Flag(
        CheckLastRevStateFlag,
        "If enabled, Copybara will validate that the destination didn't change"
            + " since last-rev import for destination_files. Note that this"
            + " flag doesn't work for CHANGE_REQUEST mode.")]
    public bool CheckLastRevState { get; set; }

    [Flag(
        "--threads",
        "Number of threads to use when running transformations that change lot of files")]
    public int Threads { get; set; } = Environment.ProcessorCount;

    [Flag(
        ChangeRequestFromSotLimitFlag,
        "Number of origin baseline changes to use for trying to match one in the"
            + " destination. It can be used if the are many parent changes in the origin that are a"
            + " no-op in the destination")]
    public int ChangeRequestFromSotLimit { get; set; } = 500;

    [Flag(
        "--threads-min-size",
        "Minimum size of the lists to process to run them in parallel")]
    public int ThreadsMinSize { get; set; } = 100;

    [Flag(
        "--notransformation-join",
        "By default Copybara tries to join certain transformations in one so that it"
            + " is more efficient. This disables the feature.")]
    public bool NoTransformationJoin { get; set; }

    [Flag(
        ReadConfigFromChangeFlag,
        "For each imported origin change, load the workflow's origin_files, "
            + "destination_files and transformations from the config version of that change. The "
            + "rest of the fields (more importantly, "
            + "origin and destination) cannot change and the version from the first config will be "
            + "used.")]
    internal bool ReadConfigFromChange { get; set; }

    [Flag(
        ReadConfigFromChangeFlag + "-disable",
        ReadConfigFromChangeFlag
            + " is a arity 0 flag, this flag overrides it to override it being enabled.",
        Arity = 1)]
    internal bool DisableReadConfigFromChange { get; set; }

    [Flag(
        "--read-config-from-head-paths",
        "When " + ReadConfigFromChangeFlag + " flag is used, read the following"
            + " path from head instead. This flag allows to unblock migrations due to config"
            + " libraries bugs. The paths accept globs syntax (**, ?, etc.)",
        Hidden = true)]
    public Glob? ReadConfigFromChangePaths { get; set; }

    [Flag("--nosmart-prune", "Disable smart prunning")]
    internal bool NoSmartPrune { get; set; }

    [Flag(
        "--to-folder",
        "Sometimes a user wants to test what the outcome would be for a workflow without changing"
            + " the configuration or adding an auxiliary testing workflow. This flag allows to"
            + " change an existing workflow to use folder.destination")]
    internal bool ToFolder { get; set; }

    [Flag(
        "--capture-definition-stack-locals",
        "Captures local variables at each level of the Starlark definition stack.",
        Hidden = true)]
    public bool CaptureDefinitionStackLocals { get; set; }

    [Flag(
        "--change-request-from-sot-retry",
        "Number of retries and delay between retries when we cannot find the baseline"
            + " in the destination for CHANGE_REQUEST_FROM_SOT. For example '10,30,60' will retry"
            + " three times. The first retry will be delayed 10s, the second one 30s and the third"
            + " one 60s")]
    public List<int> ChangeRequestFromSotRetry { get; set; } = new();

    [Flag(
        "--default-author",
        "Use this author as default instead of the one in the config file."
            + "Format should be 'Foo Bar <foobar@example.com>'")]
    internal string? DefaultAuthor { get; set; }

    [Flag(
        "--force-message",
        "Force the change description to this. Note that this only changes the message"
            + " before the transformations happen, you can still use the transformations"
            + " to alter it.")]
    internal string? ForcedChangeMessage { get; set; }

    [Flag(
        "--force-author",
        "Force the author to this. Note that this only changes the author"
            + " before the transformations happen, you can still use the transformations"
            + " to alter it.")]
    internal Author? ForcedAuthor { get; set; }

    [Flag(
        "--diff-in-origin",
        "When this flag is enabled, copybara will show different changes between last"
            + " Revision and current revision in origin instead of in destination. NOTE: it Only"
            + " works for SQUASH and ITERATIVE")]
    public bool DiffInOrigin { get; set; }

    [Flag(
        "--baseline-for-merge-import",
        "Origin baseline to use for merge import. This overrides any inferred origin baseline")]
    public string? BaselineForMergeImport { get; set; }

    [Flag(
        "--threads-for-merge-import",
        "Number of threads to use for executing the diff tool for the merge import mode.")]
    internal int ThreadsForMergeImport { get; set; } = 40;

    [Flag(
        "--debug-merge-import",
        "Debug merge import for files matching the regex. You can use something like"
            + " \".*/myfile.cc\" and it will show debugging information for files that matches"
            + " that regex (e.g. foo/myfile.cc)",
        Hidden = true,
        Arity = 1)]
    internal string? DebugMergeImport { get; set; }

    [Flag(
        "--disable-consistency-merge-import",
        "If merge import is set to use consistency in the config, disable it for this run. This"
            + " uses an import baseline instead. A new consistency file will still be generated.",
        Arity = 1)]
    public bool DisableConsistencyMergeImport { get; set; }

    [Flag(
        SkipTransformFlagName,
        "List of transform names that should be skipped.")]
    public List<string> SkipTransforms { get; set; } = new();

    public WorkflowOptions()
    {
    }

    /// <summary>Copy constructor.</summary>
    public WorkflowOptions(WorkflowOptions other)
    {
        ChangeBaseline = other.ChangeBaseline;
        LastRevision = other.LastRevision;
        InitHistory = other.InitHistory;
        IterativeLimitChanges = other.IterativeLimitChanges;
        IgnoreNoop = other.IgnoreNoop;
        SquashSkipHistory = other.SquashSkipHistory;
        MigrateNoopChanges = other.MigrateNoopChanges;
        WorkflowIdentityUser = other.WorkflowIdentityUser;
        CheckLastRevState = other.CheckLastRevState;
        Threads = other.Threads;
        ChangeRequestFromSotLimit = other.ChangeRequestFromSotLimit;
        ThreadsMinSize = other.ThreadsMinSize;
        NoTransformationJoin = other.NoTransformationJoin;
        ReadConfigFromChange = other.ReadConfigFromChange;
        DisableReadConfigFromChange = other.DisableReadConfigFromChange;
        ReadConfigFromChangePaths = other.ReadConfigFromChangePaths;
        NoSmartPrune = other.NoSmartPrune;
        ToFolder = other.ToFolder;
        ChangeRequestFromSotRetry = other.ChangeRequestFromSotRetry;
        DefaultAuthor = other.DefaultAuthor;
        ForcedChangeMessage = other.ForcedChangeMessage;
        ForcedAuthor = other.ForcedAuthor;
        DiffInOrigin = other.DiffInOrigin;
        ExpectedFixedRef = other.ExpectedFixedRef;
        PinnedFixedRef = other.PinnedFixedRef;
    }

    public WorkflowOptions(string changeBaseline, string? lastRevision, bool checkLastRevState)
    {
        ChangeBaseline = changeBaseline;
        LastRevision = lastRevision;
        CheckLastRevState = checkLastRevState;
    }

    public bool CanUseSmartPrune() => !NoSmartPrune;

    /// <summary>
    /// Returns the forced default author, or null if none was specified via the flag.
    /// </summary>
    public Author? GetDefaultAuthorFlag()
    {
        if (DefaultAuthor == null)
        {
            return null;
        }

        return Author.Parse(DefaultAuthor);
    }

    public bool IsReadConfigFromChange() => ReadConfigFromChange && !DisableReadConfigFromChange;

    private LocalParallelizer? _parallelizer;

    private int GetThreads()
    {
        // logger.atInfo().log("Using %d thread(s) for transformations", threads);
        return Threads;
    }

    public LocalParallelizer Parallelizer()
    {
        return _parallelizer ??= new LocalParallelizer(GetThreads(), ThreadsMinSize);
    }

    public bool JoinTransformations() => !NoTransformationJoin;

    public string? GetLastRevision() => LastRevision;

    public bool IsInitHistory() => InitHistory;

    public string GetChangeBaseline() => ChangeBaseline;

    public override bool Equals(object? obj)
    {
        if (ReferenceEquals(this, obj))
        {
            return true;
        }

        if (obj is not WorkflowOptions that)
        {
            return false;
        }

        return ChangeBaseline == that.ChangeBaseline
            && LastRevision == that.LastRevision;
    }

    public override int GetHashCode() => HashCode.Combine(ChangeBaseline, LastRevision);

    public WorkflowOptions WithInitHistory(bool initHistory)
    {
        var other = new WorkflowOptions(this)
        {
            InitHistory = initHistory,
        };
        return other;
    }
}
