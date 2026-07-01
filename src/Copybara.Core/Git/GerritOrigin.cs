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
using Copybara.Checks;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Git.GerritApi;
using Copybara.Revision;
using Copybara.Util;
using Console = Copybara.Util.Console.Console;
using GerritApiClient = Copybara.Git.GerritApi.GerritApi;

namespace Copybara.Git;

/// <summary>
/// An <see cref="IOrigin{R}"/> that can read Gerrit reviews. Port of
/// <c>com.google.copybara.git.GerritOrigin</c>.
/// </summary>
public sealed class GerritOrigin : GitOrigin
{
    private readonly GeneralOptions _generalOptions;
    private readonly GitOptions _gitOptions;
    private readonly GitOriginOptions _gitOriginOptions;
    private readonly GerritOptions _gerritOptions;
    private readonly GitOrigin.SubmoduleStrategy _submoduleStrategy;
    private readonly IReadOnlyList<string> _excludedSubmodules;
    private readonly bool _includeBranchCommitLogs;
    private readonly bool _partialFetch;
    private readonly IChecker? _endpointChecker;
    private readonly ITransformation? _patchTransformation;
    private readonly string? _branch;
    private readonly bool _ignoreGerritNoop;
    private readonly bool _importWipChanges;

    private GerritOrigin(
        GeneralOptions generalOptions,
        string repoUrl,
        string? configRef,
        GitOptions gitOptions,
        GitOriginOptions gitOriginOptions,
        GerritOptions gerritOptions,
        GitOrigin.SubmoduleStrategy submoduleStrategy,
        IReadOnlyList<string> excludedSubmodules,
        bool includeBranchCommitLogs,
        bool firstParent,
        bool partialFetch,
        IChecker? endpointChecker,
        ITransformation? patchTransformation,
        string? branch,
        bool describeVersion,
        bool ignoreGerritNoop,
        bool primaryBranchMigrationMode,
        IApprovalsProvider approvalsProvider,
        bool importWipChanges,
        IGitRepositoryHook? gitRepositoryHook)
        : base(
            generalOptions,
            repoUrl,
            configRef,
            GitRepoType.Gerrit,
            gitOptions,
            gitOriginOptions,
            submoduleStrategy,
            excludedSubmodules,
            includeBranchCommitLogs,
            firstParent,
            partialFetch,
            patchTransformation,
            describeVersion,
            versionSelector: null,
            configPath: null,
            workflowName: null,
            primaryBranchMigrationMode,
            approvalsProvider,
            enableLfs: false,
            credentials: null,
            gitRepositoryHook)
    {
        _generalOptions = Preconditions.CheckNotNull(generalOptions);
        _gitOptions = Preconditions.CheckNotNull(gitOptions);
        _gitOriginOptions = Preconditions.CheckNotNull(gitOriginOptions);
        _gerritOptions = Preconditions.CheckNotNull(gerritOptions);
        _submoduleStrategy = submoduleStrategy;
        _excludedSubmodules = excludedSubmodules;
        _includeBranchCommitLogs = includeBranchCommitLogs;
        _endpointChecker = endpointChecker;
        _patchTransformation = patchTransformation;
        _branch = branch;
        _partialFetch = partialFetch;
        _ignoreGerritNoop = ignoreGerritNoop;
        _importWipChanges = importWipChanges;
    }

    public override ImmutableListMultimap<string, string> Describe(Glob? originFiles)
    {
        var builder = ImmutableListMultimap<string, string>.CreateBuilder();
        builder.PutAll(base.Describe(originFiles));
        if (_branch != null)
        {
            builder.Put("branch", _branch);
        }
        builder.Put("import_wip_changes", _importWipChanges.ToString());
        return builder.Build();
    }

    public override GitRevision Resolve(string? reference)
    {
        _generalOptions.GetConsole().Progress("Gerrit Origin: Initializing local repo");

        ValidationException.CheckCondition(
            !string.IsNullOrEmpty(reference), "Expecting a change number as reference");

        GerritChange? change = GerritChange.Resolve(GetRepository(), RepoUrl, reference!, _generalOptions);
        if (change == null)
        {
            GitRevision gitRevisionResolved = GitRepoType.Git.ResolveRef(
                GetRepository(), RepoUrl, reference!, _generalOptions, DescribeVersion, _partialFetch,
                fetchDepth: null);
            return DescribeVersion
                ? GetRepository().AddDescribeVersion(gitRevisionResolved)
                : gitRevisionResolved;
        }
        GerritApiClient api = _gerritOptions.NewGerritApi(RepoUrl);

        ChangeInfo response = api.GetChangeAsync(
                change.GetChange().ToString(),
                new GetChangeInput(
                    new HashSet<IncludeResult>
                    {
                        IncludeResult.DETAILED_ACCOUNTS,
                        IncludeResult.DETAILED_LABELS,
                    }))
            .GetAwaiter().GetResult();

        if (_branch != null && !_branch.Equals(response.GetBranch()))
        {
            throw new EmptyChangeException(
                $"Skipping import of change {change.GetChange()} for branch {response.GetBranch()}."
                    + $" Only tracking changes for branch {_branch}");
        }

        if (!_importWipChanges && response.IsWorkInProgress())
        {
            throw new EmptyChangeException(
                $"Skipping import of change {change.GetChange()} as it is marked as Work in Progress.");
        }

        var labels = ImmutableListMultimap<string, string>.CreateBuilder();

        labels.Put(GerritChange.GerritChangeBranch, response.GetBranch()!);
        if (response.GetTopic() != null)
        {
            labels.Put(GerritChange.GerritChangeTopic, response.GetTopic()!);
        }
        labels.Put(GerritChange.GerritCompleteChangeIdLabel, response.GetId()!);
        foreach (var e in response.GetReviewers())
        {
            foreach (var info in e.Value)
            {
                if (info.GetEmail() != null)
                {
                    labels.Put("GERRIT_" + e.Key + "_EMAIL", info.GetEmail()!);
                }
            }
        }

        if (response.GetOwner()?.GetEmail() != null)
        {
            labels.Put(GerritChange.GerritOwnerEmailLabel, response.GetOwner()!.GetEmail()!);
        }
        try
        {
            GitRevision gitRevision = change.Fetch(labels.Build());
            return DescribeVersion ? GetRepository().AddDescribeVersion(gitRevision) : gitRevision;
        }
        catch (CannotResolveRevisionException unexpected)
        {
            // We got the change via the API so it is unexpected to fail now.
            throw new RepoException("Unable to fetch change content.", unexpected);
        }
    }

    /// <summary>Builds a new <see cref="GerritOrigin"/>.</summary>
    internal static GerritOrigin NewGerritOrigin(
        GeneralOptions generalOptions,
        GitOptions gitOptions,
        GitOriginOptions gitOriginOptions,
        GerritOptions gerritOptions,
        GitDestinationOptions destinationOptions,
        string url,
        GitOrigin.SubmoduleStrategy submoduleStrategy,
        IReadOnlyList<string> excludedSubmodules,
        bool firstParent,
        bool partialFetch,
        IChecker? endpointChecker,
        ITransformation? patchTransformation,
        string? branch,
        bool describeVersion,
        bool ignoreGerritNoop,
        bool primaryBranchMigrationMode,
        IApprovalsProvider approvalsProvider,
        bool importWipChanges,
        IGitRepositoryHook? gitRepositoryHook) =>
        new(
            generalOptions,
            url,
            configRef: null,
            gitOptions,
            gitOriginOptions,
            gerritOptions,
            submoduleStrategy,
            excludedSubmodules,
            includeBranchCommitLogs: false,
            firstParent,
            partialFetch,
            endpointChecker,
            patchTransformation,
            branch,
            describeVersion,
            ignoreGerritNoop,
            primaryBranchMigrationMode,
            approvalsProvider,
            importWipChanges,
            gitRepositoryHook);

    public override IOrigin<GitRevision>.IReader<GitRevision> NewReader(
        Glob originFiles, Authoring.Authoring authoring) =>
        new GerritReaderImpl(
            RepoUrl,
            originFiles,
            authoring,
            _gitOptions,
            _gitOriginOptions,
            _generalOptions,
            _includeBranchCommitLogs,
            _submoduleStrategy,
            _excludedSubmodules,
            FirstParent,
            _partialFetch,
            _patchTransformation,
            configPath: null,
            workflowName: null,
            credentials: null,
            GitRepositoryHook,
            _gerritOptions,
            _endpointChecker,
            _ignoreGerritNoop);

    private sealed class GerritReaderImpl : ReaderImpl
    {
        private readonly GerritOptions _gerritOptions;
        private readonly IChecker? _endpointChecker;
        private readonly bool _ignoreGerritNoop;

        internal GerritReaderImpl(
            string repoUrl,
            Glob originFiles,
            Authoring.Authoring authoring,
            GitOptions gitOptions,
            GitOriginOptions gitOriginOptions,
            GeneralOptions generalOptions,
            bool includeBranchCommitLogs,
            GitOrigin.SubmoduleStrategy submoduleStrategy,
            IReadOnlyList<string> excludedSubmodules,
            bool firstParent,
            bool partialFetch,
            ITransformation? patchTransformation,
            string? configPath,
            string? workflowName,
            CredentialFileHandler? credentials,
            IGitRepositoryHook? gitRepositoryHook,
            GerritOptions gerritOptions,
            IChecker? endpointChecker,
            bool ignoreGerritNoop)
            : base(
                repoUrl,
                originFiles,
                authoring,
                gitOptions,
                gitOriginOptions,
                generalOptions,
                includeBranchCommitLogs,
                submoduleStrategy,
                excludedSubmodules,
                firstParent,
                partialFetch,
                patchTransformation,
                configPath,
                workflowName,
                credentials,
                gitRepositoryHook)
        {
            _gerritOptions = gerritOptions;
            _endpointChecker = endpointChecker;
            _ignoreGerritNoop = ignoreGerritNoop;
        }

        public override IReadOnlyList<GitRevision> FindBaselinesWithoutLabel(
            GitRevision startRevision, int limit)
        {
            // Skip the initial change as it might be the Gerrit review change.
            var visitor = new BaselinesWithoutLabelVisitor<GitRevision>(
                OriginFiles, limit, startRevision, skipFirst: false);
            VisitChanges(startRevision, visitor);
            return visitor.GetResult();
        }

        public override IEndpoint GetFeedbackEndPoint(Console console)
        {
            _gerritOptions.ValidateEndpointChecker(_endpointChecker, RepoUrl);
            return new GerritEndpoint(
                _gerritOptions.NewGerritApiSupplier(RepoUrl, _endpointChecker),
                RepoUrl,
                console,
                // We disallow submitting to the origin, but this has feasible use cases and we can
                // revisit.
                allowSubmitChange: false);
        }

        public override Origin.ChangesResponse<GitRevision> Changes(
            GitRevision? fromRef, GitRevision toRef)
        {
            Origin.ChangesResponse<GitRevision> result = base.Changes(fromRef, toRef);
            Change<GitRevision> change = Change(toRef);
            if (!_ignoreGerritNoop
                || change.GetChangeFiles() == null
                || !toRef.AssociatedLabels().ContainsKey(GerritChange.GerritCompleteChangeIdLabel))
            {
                return result;
            }
            var pathMatcher = OriginFiles.RelativeTo("/");
            if (!change.GetChangeFiles()!.Any(x => pathMatcher.Matches("/" + x)))
            {
                return Origin.ChangesResponse<GitRevision>.NoChanges(Origin.EmptyReason.NoChanges);
            }
            return result;
        }
    }
}
