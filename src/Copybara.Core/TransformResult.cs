/*
 * Copyright (C) 2016 Google LLC
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
using Copybara.Approval;
using Copybara.Authoring;
using Copybara.Common;
using Copybara.Revision;
using Copybara.Util;
using Console = Copybara.Util.Console.Console;

namespace Copybara;

/// <summary>
/// Represents the final result of a transformation, including metadata and actual code to be
/// migrated.
/// </summary>
public sealed class TransformResult
{
    private readonly string _path;
    private readonly Author _author;
    private readonly DateTimeOffset _timestamp;
    private readonly string _summary;
    private readonly string? _baseline;
    private readonly bool _askForConfirmation;
    private readonly IRevision _currentRevision;
    private readonly IRevision _requestedRevision;
    private readonly string? _changeIdentity;
    private readonly string _workflowName;
    private readonly string? _rawSourceRef;
    private readonly Changes _changes;
    private readonly bool _setRevId;
    private readonly ImmutableArray<DiffFile>? _affectedFilesForSmartPrune;
    private readonly Func<string, IReadOnlyCollection<string>> _labelFinder;
    private readonly string _revIdLabel;
    private readonly bool _confirmedInOrigin;
    private readonly IApprovalsProvider _approvalsProvider;
    private readonly IDestinationInfo? _destinationInfo;

    private static DateTimeOffset ReadTimestampOrCurrentTime(IRevision originRef)
    {
        var refTimestamp = originRef.ReadTimestamp();
        return refTimestamp ?? DateTimeOffset.Now;
    }

    public TransformResult(
        string path,
        IRevision currentRevision,
        Author author,
        string summary,
        IRevision requestedRevision,
        string workflowName,
        Changes changes,
        string? rawSourceRef,
        bool setRevId,
        Func<string, IReadOnlyCollection<string>> labelFinder,
        string revIdLabel)
        : this(
            path,
            currentRevision,
            author,
            ReadTimestampOrCurrentTime(currentRevision),
            summary,
            baseline: null,
            askForConfirmation: false,
            requestedRevision,
            changeIdentity: null,
            workflowName,
            changes,
            rawSourceRef,
            setRevId,
            affectedFilesForSmartPrune: null,
            labelFinder,
            revIdLabel,
            confirmedInOrigin: false,
            new NoneApprovedProvider(),
            destinationInfo: null)
    {
    }

    private TransformResult(
        string path,
        IRevision currentRevision,
        Author author,
        DateTimeOffset timestamp,
        string summary,
        string? baseline,
        bool askForConfirmation,
        IRevision requestedRevision,
        string? changeIdentity,
        string workflowName,
        Changes changes,
        string? rawSourceRef,
        bool setRevId,
        ImmutableArray<DiffFile>? affectedFilesForSmartPrune,
        Func<string, IReadOnlyCollection<string>> labelFinder,
        string revIdLabel,
        bool confirmedInOrigin,
        IApprovalsProvider approvalsProvider,
        IDestinationInfo? destinationInfo)
    {
        _path = Preconditions.CheckNotNull(path);
        _currentRevision = Preconditions.CheckNotNull(currentRevision);
        _author = Preconditions.CheckNotNull(author);
        _timestamp = timestamp;
        _summary = Preconditions.CheckNotNull(summary);
        _baseline = baseline;
        _askForConfirmation = askForConfirmation;
        _requestedRevision = Preconditions.CheckNotNull(requestedRevision);
        _changeIdentity = changeIdentity;
        _workflowName = Preconditions.CheckNotNull(workflowName);
        _changes = Preconditions.CheckNotNull(changes);
        _rawSourceRef = rawSourceRef;
        _setRevId = setRevId;
        _affectedFilesForSmartPrune = affectedFilesForSmartPrune;
        _labelFinder = Preconditions.CheckNotNull(labelFinder);
        _revIdLabel = Preconditions.CheckNotNull(revIdLabel);
        _confirmedInOrigin = confirmedInOrigin;
        _approvalsProvider = approvalsProvider;
        _destinationInfo = destinationInfo;
    }

    private TransformResult Copy(
        string? baseline = null,
        bool? askForConfirmation = null,
        string? changeIdentity = null,
        Changes? changes = null,
        string? summary = null,
        bool? setRevId = null,
        ImmutableArray<DiffFile>? affectedFilesForSmartPrune = null,
        Func<string, IReadOnlyCollection<string>>? labelFinder = null,
        bool? confirmedInOrigin = null,
        IApprovalsProvider? approvalsProvider = null,
        IDestinationInfo? destinationInfo = null,
        bool keepBaseline = true,
        bool keepChangeIdentity = true,
        bool keepAffected = true,
        bool keepDestinationInfo = true) =>
        new(
            _path,
            _currentRevision,
            _author,
            _timestamp,
            summary ?? _summary,
            baseline ?? (keepBaseline ? _baseline : null),
            askForConfirmation ?? _askForConfirmation,
            _requestedRevision,
            changeIdentity ?? (keepChangeIdentity ? _changeIdentity : null),
            _workflowName,
            changes ?? _changes,
            _rawSourceRef,
            setRevId ?? _setRevId,
            affectedFilesForSmartPrune ?? (keepAffected ? _affectedFilesForSmartPrune : null),
            labelFinder ?? _labelFinder,
            _revIdLabel,
            confirmedInOrigin ?? _confirmedInOrigin,
            approvalsProvider ?? _approvalsProvider,
            destinationInfo ?? (keepDestinationInfo ? _destinationInfo : null));

    public TransformResult WithBaseline(string newBaseline) =>
        Copy(baseline: Preconditions.CheckNotNull(newBaseline));

    public TransformResult WithSummary(string summary) => Copy(summary: summary);

    public TransformResult WithIdentity(string changeIdentity) =>
        Copy(changeIdentity: changeIdentity);

    public TransformResult WithApprovalsProvider(IApprovalsProvider approvalsProvider) =>
        Copy(approvalsProvider: approvalsProvider);

    public TransformResult WithAskForConfirmation(bool askForConfirmation) =>
        Copy(askForConfirmation: askForConfirmation);

    public TransformResult WithChanges(Changes changes) => Copy(changes: changes);

    public TransformResult WithSetRevId(bool setRevId) => Copy(setRevId: setRevId);

    public TransformResult WithAffectedFilesForSmartPrune(
        IReadOnlyList<DiffFile> affectedFilesForSmartPrune) =>
        Copy(affectedFilesForSmartPrune:
            Preconditions.CheckNotNull(affectedFilesForSmartPrune).ToImmutableArray());

    public TransformResult WithLabelFinder(Func<string, IReadOnlyCollection<string>> labelMapper) =>
        Copy(labelFinder: Preconditions.CheckNotNull(labelMapper));

    public TransformResult WithDiffInOrigin(bool diffInOrigin) =>
        Copy(confirmedInOrigin: diffInOrigin);

    public TransformResult WithDestinationInfo(IDestinationInfo destinationInfo) =>
        Copy(destinationInfo: destinationInfo);

    /// <summary>Directory containing the tree of files to put in destination.</summary>
    public string GetPath() => _path;

    /// <summary>The current revision being migrated. In ITERATIVE mode this would change per migration.</summary>
    public IRevision GetCurrentRevision() => _currentRevision;

    /// <summary>The revision that the user asked to migrate to.</summary>
    public IRevision GetRequestedRevision() => _requestedRevision;

    /// <summary>A stable identifier that represents an entity in the origin for this change.</summary>
    public string? GetChangeIdentity() => _changeIdentity;

    /// <summary>Destination author to be used.</summary>
    public Author GetAuthor() => _author;

    /// <summary>The moment when the code was submitted to the origin repository.</summary>
    public DateTimeOffset GetTimestamp() => _timestamp;

    /// <summary>A description of the migrated changes to include in the destination's change description.</summary>
    public string GetSummary() => _summary;

    /// <summary>Destination baseline to be used for updating the code in the destination.</summary>
    public string? GetBaseline() => _baseline;

    /// <summary>If the destination should ask for confirmation.</summary>
    public bool IsAskForConfirmation() => _askForConfirmation;

    /// <summary>If true, the destination will not ask for confirmation, instead showing the diff.</summary>
    public bool IsConfirmedInOrigin() => _confirmedInOrigin;

    /// <summary>The workflow name for the migration.</summary>
    public string GetWorkflowName() => _workflowName;

    /// <summary>Get all the labels from the message.</summary>
    public IReadOnlyList<LabelFinder> FindAllLabels() =>
        ChangeMessage.ParseMessage(_summary).GetLabels();

    /// <summary>Data about the set of changes that are being migrated.</summary>
    public Changes GetChanges() => _changes;

    /// <summary>Reference as requested in the CLI if any.</summary>
    public string? GetRawSourceRef() => _rawSourceRef;

    /// <summary>If RevId should be recorded in the destination.</summary>
    public bool IsSetRevId() => _setRevId;

    /// <summary>The label to use for storing the current migration state.</summary>
    public string GetRevIdLabel() => _revIdLabel;

    /// <summary>If not null, the subset of files that Workflow smart_prune detected as really changed.</summary>
    public IReadOnlyList<DiffFile>? GetAffectedFilesForSmartPrune() =>
        _affectedFilesForSmartPrune is { } arr ? arr : null;

    /// <summary>A function that returns all the label values that match a name.</summary>
    public Func<string, IReadOnlyCollection<string>> GetLabelFinder() => _labelFinder;

    /// <summary>A function that retrieves the DestinationInfo object supplied by the destination.</summary>
    public IDestinationInfo? GetDestinationInfo() => _destinationInfo;

    /// <summary>Get the approvals provider from the Origin.</summary>
    public ApprovalsResult GetOriginApprovals(
        IReadOnlyList<ChangeWithApprovals> changes, Console console) =>
        _approvalsProvider.ComputeApprovals(changes.ToImmutableArray(), _labelFinder, console);
}
