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
using Copybara.Approval;
using Copybara.Checks;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Git.GitHub.Api;
using Copybara.Git.GitHub.Util;
using Copybara.Revision;
using Copybara.Util;
using Console = Copybara.Util.Console.Console;
using GitHubApiClient = Copybara.Git.GitHub.Api.GitHubApi;

namespace Copybara.Git;

/// <summary>
/// A class for reading GitHub Pull Requests. Port of
/// <c>com.google.copybara.git.GitHubPrOrigin</c>.
/// </summary>
public class GitHubPrOrigin : IOrigin<GitRevision>
{
    internal const int RetryCount = 3;

    /// <summary>
    /// The threshold for the number of check runs to use the manual lookup. Anything more than this,
    /// we are better off using a wildcard query.
    /// </summary>
    private const int ManualCheckRunLookupThreshold = 5;

    public const string GithubPrNumberLabel = "GITHUB_PR_NUMBER";
    public const string GithubBaseBranch = "GITHUB_BASE_BRANCH";
    public const string GithubBaseBranchSha1 = "GITHUB_BASE_BRANCH_SHA1";
    public const string GithubPrUseMerge = "GITHUB_PR_USE_MERGE";
    public const string GithubPrTitle = "GITHUB_PR_TITLE";
    public const string GithubPrUrl = "GITHUB_PR_URL";
    public const string GithubPrBody = "GITHUB_PR_BODY";
    public const string GithubPrUser = "GITHUB_PR_USER";
    public const string GithubPrAssignee = "GITHUB_PR_ASSIGNEE";
    public const string GithubPrReviewerApprover = "GITHUB_PR_REVIEWER_APPROVER";
    public const string GithubPrReviewerOther = "GITHUB_PR_REVIEWER_OTHER";
    public const string GithubPrRequestedReviewer = "GITHUB_PR_REQUESTED_REVIEWER";
    private const string LocalPrHeadRef = "refs/PR_HEAD";
    public const string GithubPrHeadSha = "GITHUB_PR_HEAD_SHA";
    private const string LocalPrMergeRef = "refs/PR_MERGE";
    private const string LocalPrBaseBranch = "refs/PR_BASE_BRANCH";

    // Mirrors GitModule.DEFAULT_INTEGRATE_LABEL (GitModule is ported separately).
    private const string DefaultIntegrateLabel = "COPYBARA_INTEGRATE_REVIEW";

    private readonly string _url;
    private readonly bool _useMerge;
    private readonly GeneralOptions _generalOptions;
    private readonly GitOptions _gitOptions;
    private readonly GitOriginOptions _gitOriginOptions;
    private readonly GitHubOptions _gitHubOptions;
    private readonly IReadOnlySet<string> _requiredLabelsField;
    private readonly IReadOnlySet<string> _requiredStatusContextNamesField;
    private readonly IReadOnlySet<string> _requiredCheckRunsField;
    private readonly IReadOnlySet<string> _retryableLabelsField;
    private readonly GitOrigin.SubmoduleStrategy _submoduleStrategy;
    private readonly IReadOnlyList<string> _excludedSubmodules;
    private readonly Console _console;
    private readonly bool _baselineFromBranch;
    private readonly bool _firstParent;
    private readonly bool _partialFetch;
    private readonly StateFilter _requiredState;
    private readonly ReviewState? _reviewState;
    private readonly ImmutableHashSet<AuthorAssociation> _reviewApprovers;
    private readonly IChecker? _endpointChecker;
    private readonly ITransformation? _patchTransformation;
    private readonly string? _branch;
    private readonly bool _describeVersion;
    private readonly GitHubHost _ghHost;
    private readonly GitHubPrOriginOptions _gitHubPrOriginOptions;
    private readonly IApprovalsProvider _provider;
    private readonly CredentialFileHandler? _credentials;
    private readonly IGitRepositoryHook? _gitRepositoryHook;

    public GitHubPrOrigin(
        string url,
        bool useMerge,
        GeneralOptions generalOptions,
        GitOptions gitOptions,
        GitOriginOptions gitOriginOptions,
        GitHubOptions gitHubOptions,
        GitHubPrOriginOptions gitHubPrOriginOptions,
        IReadOnlySet<string> requiredLabels,
        IReadOnlySet<string> requiredStatusContextNames,
        IReadOnlySet<string> requiredCheckRuns,
        IReadOnlySet<string> retryableLabels,
        GitOrigin.SubmoduleStrategy submoduleStrategy,
        IReadOnlyList<string> excludedSubmodules,
        bool baselineFromBranch,
        bool firstParent,
        bool partialClone,
        StateFilter requiredState,
        ReviewState? reviewState,
        ImmutableHashSet<AuthorAssociation> reviewApprovers,
        IChecker? endpointChecker,
        ITransformation? patchTransformation,
        string? branch,
        bool describeVersion,
        GitHubHost ghHost,
        IApprovalsProvider provider,
        CredentialFileHandler? credentials,
        IGitRepositoryHook? gitRepositoryHook)
    {
        _url = Preconditions.CheckNotNull(url);
        _useMerge = useMerge;
        _generalOptions = Preconditions.CheckNotNull(generalOptions);
        _gitOptions = Preconditions.CheckNotNull(gitOptions);
        _gitOriginOptions = Preconditions.CheckNotNull(gitOriginOptions);
        _gitHubOptions = gitHubOptions;
        _gitHubPrOriginOptions = Preconditions.CheckNotNull(gitHubPrOriginOptions);
        _requiredLabelsField = Preconditions.CheckNotNull(requiredLabels);
        _requiredStatusContextNamesField = Preconditions.CheckNotNull(requiredStatusContextNames);
        _requiredCheckRunsField = Preconditions.CheckNotNull(requiredCheckRuns);
        _retryableLabelsField = Preconditions.CheckNotNull(retryableLabels);
        _submoduleStrategy = submoduleStrategy;
        _excludedSubmodules = excludedSubmodules;
        _console = generalOptions.GetConsole();
        _baselineFromBranch = baselineFromBranch;
        _firstParent = firstParent;
        _partialFetch = partialClone;
        _requiredState = requiredState;
        _reviewState = reviewState;
        _reviewApprovers = Preconditions.CheckNotNull(reviewApprovers);
        _endpointChecker = endpointChecker;
        _patchTransformation = patchTransformation;
        _branch = branch;
        _describeVersion = describeVersion;
        _ghHost = ghHost;
        _provider = Preconditions.CheckNotNull(provider);
        _credentials = credentials;
        _gitRepositoryHook = gitRepositoryHook;
    }

    public GitRevision Resolve(string reference)
    {
        ValidationException.CheckCondition(
            reference != null,
            "A pull request reference is expected as argument in the command line."
                + " Invoke copybara as:\n"
                + "    copybara copy.bara.sky workflow_name 12345");
        _console.Progress("GitHub PR Origin: Resolving reference " + reference);
        string configProjectName = _ghHost.GetProjectNameFromUrl(_url);

        // GitHub's commit 'status' webhook provides only the commit SHA.
        if (GitRevision.CompleteGitHashPattern.IsMatch(reference!))
        {
            PullRequest prBySha = GetPrFromSha(configProjectName, reference!);
            return GetRevisionForPr(configProjectName, prBySha);
        }

        // A whole https pull request url.
        GitHubHost.GitHubPrUrl? githubPrUrl = _ghHost.MaybeParseGithubPrUrl(reference!);
        if (githubPrUrl != null)
        {
            ValidationException.CheckCondition(
                githubPrUrl.GetProject() == configProjectName,
                "Project name should be '{0}' but it is '{1}' instead",
                configProjectName,
                githubPrUrl.GetProject());
            return GetRevisionForPr(
                configProjectName,
                GetPrFromNumber(configProjectName, githubPrUrl.GetPrNumber()));
        }

        // A Pull request number.
        if (reference!.Length != 0 && reference.All(char.IsDigit))
        {
            return GetRevisionForPr(
                configProjectName, GetPrFromNumber(configProjectName, int.Parse(reference)));
        }

        // refs/pull/12345/head
        int? prNumber = GitHubUtil.MaybeParseGithubPrFromHeadRef(reference);
        if (prNumber != null)
        {
            return GetRevisionForPr(
                configProjectName, GetPrFromNumber(configProjectName, prNumber.Value));
        }

        throw new CannotResolveRevisionException(
            $"'{reference}' is not a valid reference for a GitHub Pull Request. Valid formats:"
                + "'https://github.com/project/pull/1234', 'refs/pull/1234/head' or '1234'");
    }

    public GitRevision ResolveLastRev(string reference)
    {
        string sha1Part = reference.Split(' ')[0];
        if (GitRevision.CompleteGitHashPattern.IsMatch(sha1Part))
        {
            return new GitRevision(GetRepository(), GetRepository().ParseRef(sha1Part));
        }
        throw new CannotResolveRevisionException($"'{reference}' is not a valid SHA.");
    }

    public GitRevision ResolveAncestorRef(string ancestorRef, GitRevision descendantRev) =>
        GitOrigin.ResolveAncestorRef(this, GetRepository(), ancestorRef, descendantRev);

    public string? ShowDiff(GitRevision revisionFrom, GitRevision revisionTo) =>
        GetRepository()
            .ShowDiff(
                Preconditions.CheckNotNull(revisionFrom, "revisionFrom should not be null").GetHash(),
                Preconditions.CheckNotNull(revisionTo, "revisionTo should not be null").GetHash());

    /// <summary>Given a commit SHA, use the GitHub API to (try to) look up info for a corresponding PR.</summary>
    private PullRequest GetPrFromSha(string project, string sha)
    {
        GitHubApiClient gitHubApi =
            _gitHubOptions.NewGitHubRestApi(_ghHost.GetHost(), project, null, _credentials, _console);
        IssuesAndPullRequestsSearchResults searchResults =
            gitHubApi.GetIssuesOrPullRequestsSearchResultsAsync(
                new GitHubApiClient.IssuesAndPullRequestsSearchRequestParams(
                    project,
                    sha,
                    GitHubApiClient.IssuesAndPullRequestsSearchRequestParams.SearchType.PULL_REQUEST,
                    GitHubApiClient.IssuesAndPullRequestsSearchRequestParams.State.OPEN))
                .GetAwaiter().GetResult();

        var prNumbers =
            (searchResults.GetItems()
                ?? new List<IssuesAndPullRequestsSearchResults.IssuesAndPullRequestsSearchResult>())
                .Select(item => item.GetNumber())
                .ToList();

        // Only migrate a pr with not-closed state and head being equal to sha. Usually only one pr.
        foreach (long prNumber in prNumbers)
        {
            PullRequest pullRequest =
                gitHubApi.GetPullRequestAsync(project, prNumber).GetAwaiter().GetResult();
            if (StateAccepts(_requiredState, pullRequest) && pullRequest.GetHead()!.GetSha() == sha)
            {
                return pullRequest;
            }
        }
        string stateClause = _requiredState == StateFilter.ALL ? "" : (_requiredState + " state and ");
        throw new EmptyChangeException(
            $"Could not find a pr with {stateClause}head being equal to sha {sha}");
    }

    /// <summary>Given a PR number, use the GitHub API to look up the PR info.</summary>
    private PullRequest GetPrFromNumber(string project, long prNumber)
    {
        using (_generalOptions.Profiler().Start("github_api_get_pr"))
        {
            return _gitHubOptions
                .NewGitHubRestApi(_ghHost.GetHost(), project, null, _credentials, _console)
                .GetPullRequestAsync(project, prNumber)
                .GetAwaiter().GetResult();
        }
    }

    private GitRevision GetRevisionForPr(string project, PullRequest prData)
    {
        GitHubApiClient api =
            _gitHubOptions.NewGitHubRestApi(_ghHost.GetHost(), project, null, _credentials, _console);
        int prNumber = (int)prData.GetNumber();
        bool actuallyUseMerge = _useMerge;
        var labels = ImmutableListMultimap<string, string>.CreateBuilder();

        CheckPrState(prData);
        CheckPrBranch(project, prData);
        CheckRequiredLabels(api, project, prData);
        CheckRequiredStatusContextNames(api, project, prData);
        CheckRequiredCheckRuns(api, project, prData);
        CheckReviewApprovers(api, project, prData, labels);

        _console.ProgressFmt(
            "Fetching Pull Request {0} and branch '{1}'", prNumber, prData.GetBase()!.GetRef()!);
        var refSpecBuilder = new List<string>
        {
            $"{GitHubUtil.AsHeadRef(prNumber)}:{LocalPrHeadRef}",
            // Prefix the branch name with 'refs/heads/' since some implementations need the whole
            // reference name.
            $"refs/heads/{prData.GetBase()!.GetRef()}:{LocalPrBaseBranch}",
        };

        if (actuallyUseMerge)
        {
            if (prData.IsMergeable() != false)
            {
                refSpecBuilder.Add($"{GitHubUtil.AsMergeRef(prNumber)}:{LocalPrMergeRef}");
            }
            else if (ForceImport())
            {
                _console.WarnFmt(
                    "PR {0} is not mergeable, but continuing with PR Head instead because of {1}",
                    prNumber,
                    GeneralOptions.Force);
                actuallyUseMerge = false;
            }
            else
            {
                throw new CannotResolveRevisionException(
                    $"Cannot find a merge reference for Pull Request {prNumber}."
                        + " It might have a conflict with head.");
            }
        }

        IReadOnlyList<string> refspec = refSpecBuilder;
        CannotResolveRevisionException? error = null;
        try
        {
            using (_generalOptions.Profiler().Start("fetch"))
            {
                GetRepository().Fetch(
                    _ghHost.ProjectAsUrl(project),
                    prune: false,
                    force: true,
                    refspec,
                    _partialFetch,
                    depth: null,
                    tags: false);
            }
        }
        catch (CannotResolveRevisionException e)
        {
            error = e;
            if (actuallyUseMerge && prData.IsMergeable() == null && ForceImport())
            {
                // We can perhaps recover by fetching without the merge ref.
                actuallyUseMerge = false;
                refspec = refspec.Take(refspec.Count - 1).ToList();
                try
                {
                    using (_generalOptions.Profiler().Start("fetch"))
                    {
                        GetRepository().Fetch(
                            _ghHost.ProjectAsUrl(project),
                            prune: false,
                            force: true,
                            refspec,
                            _partialFetch,
                            depth: null,
                            tags: false);
                        error = null;
                    }
                }
                catch (CannotResolveRevisionException e2)
                {
                    error = e2;
                }
            }
        }

        if (error != null)
        {
            if (actuallyUseMerge)
            {
                string msg = $"Cannot find a merge reference for Pull Request {prNumber}.";
                if (prData.IsMergeable() == true)
                {
                    msg += " GitHub reported that this merge reference should exist.";
                }
                throw new CannotResolveRevisionException(msg, error);
            }
            throw new CannotResolveRevisionException(
                $"Cannot find Pull Request {prNumber}.", error);
        }

        string refForMigration = actuallyUseMerge ? LocalPrMergeRef : LocalPrHeadRef;
        GitRevision gitRevision = GetRepository().ResolveReference(refForMigration);

        string headPrSha1 = GetRepository().ResolveReference(LocalPrHeadRef).GetHash();
        string integrateLabel =
            new GitHubPrIntegrateLabel(
                GetRepository(),
                _generalOptions,
                project,
                prNumber,
                prData.GetHead()!.GetLabel()!,
                // The integrate SHA has to be HEAD of the PR not the merge ref, even if use_merge.
                headPrSha1).ToString();

        labels.PutAll(
            GithubPrRequestedReviewer,
            prData.GetRequestedReviewers().Select(u => u.GetLogin()!));
        labels.Put(GithubPrNumberLabel, prNumber.ToString());
        labels.Put(DefaultIntegrateLabel, integrateLabel);
        labels.Put(GithubBaseBranch, prData.GetBase()!.GetRef()!);
        labels.Put(GithubPrHeadSha, headPrSha1);
        labels.Put(GithubPrUseMerge, actuallyUseMerge.ToString().ToLowerInvariant());

        string mergeBase = GetRepository().MergeBase(refForMigration, LocalPrBaseBranch);
        labels.Put(GithubBaseBranchSha1, mergeBase);

        labels.Put(GithubPrTitle, prData.GetTitle() ?? "");
        labels.Put(GithubPrBody, prData.GetBody() ?? "");
        labels.Put(GithubPrUrl, prData.GetHtmlUrl() ?? "");
        labels.Put(GithubPrUser, prData.GetUser()!.GetLogin()!);
        labels.PutAll(GithubPrAssignee, prData.GetAssignees().Select(u => u.GetLogin()!));

        var result =
            new GitRevision(
                GetRepository(),
                gitRevision.GetHash(),
                reviewReference: null,
                actuallyUseMerge ? GitHubUtil.AsMergeRef(prNumber) : GitHubUtil.AsHeadRef(prNumber),
                labels.Build(),
                _url);

        return _describeVersion ? GetRepository().AddDescribeVersion(result) : result;
    }

    private void CheckPrState(PullRequest prData)
    {
        if (!ForceImport() && !StateAccepts(_requiredState, prData))
        {
            throw new EmptyChangeException(
                $"Pull Request {prData.GetNumber()} is {prData.GetState()}");
        }
    }

    private void CheckPrBranch(string project, PullRequest prData)
    {
        if (!ForceImport() && _branch != null && prData.GetBase()!.GetRef() != _branch)
        {
            throw new EmptyChangeException(
                $"Cannot migrate http://github.com/{project}/pull/{prData.GetNumber()} because its"
                    + $" base branch is '{prData.GetBase()!.GetRef()}', but the workflow is"
                    + $" configured to only migrate changes for branch '{_branch}'");
        }
    }

    private void CheckRequiredLabels(GitHubApiClient api, string project, PullRequest prData)
    {
        var requiredLabels = GetRequiredLabels();
        var retryableLabels = GetRetryableLabels();
        if (ForceImport() || requiredLabels.Count == 0)
        {
            return;
        }
        int retryCount = 0;
        HashSet<string> requiredButNotPresent;
        do
        {
            Issue issue;
            using (_generalOptions.Profiler().Start("github_api_get_issue"))
            {
                issue = api.GetIssueAsync(project, prData.GetNumber()).GetAwaiter().GetResult();
            }

            requiredButNotPresent = new HashSet<string>(requiredLabels);
            requiredButNotPresent.ExceptWith(
                (issue.GetLabels() ?? new List<Label>()).Select(l => l.GetName()!));
            // If we got all the labels we want or none of the ones we didn't get are retryable, stop.
            if (requiredButNotPresent.Count == 0
                || !requiredButNotPresent.Overlaps(retryableLabels))
            {
                break;
            }
            Thread.Sleep(TimeSpan.FromSeconds(2));
            retryCount++;
        }
        while (retryCount < RetryCount);
        if (requiredButNotPresent.Count != 0)
        {
            throw new EmptyChangeException(
                $"Cannot migrate http://github.com/{project}/pull/{prData.GetNumber()} because it is"
                    + " missing the following labels: ["
                    + string.Join(", ", requiredButNotPresent) + "]");
        }
    }

    private void CheckRequiredStatusContextNames(
        GitHubApiClient api, string project, PullRequest prData)
    {
        var requiredStatusContextNames = GetRequiredStatusContextNames();
        if (ForceImport() || requiredStatusContextNames.Count == 0)
        {
            return;
        }
        using (_generalOptions.Profiler().Start("github_api_get_combined_status"))
        {
            CombinedStatus combinedStatus =
                api.GetCombinedStatusAsync(project, prData.GetHead()!.GetSha()!)
                    .GetAwaiter().GetResult();
            var requiredButNotPresent = new HashSet<string>(requiredStatusContextNames);
            var successContexts =
                combinedStatus.GetStatuses().Cast<Status>()
                    .Where(status => status.GetState() == StatusState.SUCCESS)
                    .Select(status => status.GetContext()!);
            requiredButNotPresent.ExceptWith(successContexts);
            if (requiredButNotPresent.Count != 0)
            {
                throw new EmptyChangeException(
                    $"Cannot migrate http://github.com/{project}/pull/{prData.GetNumber()} because"
                        + " the following ci labels have not been passed: ["
                        + string.Join(", ", requiredButNotPresent) + "]");
            }
        }
    }

    private void CheckRequiredCheckRuns(GitHubApiClient api, string project, PullRequest prData)
    {
        var requiredCheckRuns = GetRequiredCheckRuns();
        if (ForceImport() || requiredCheckRuns.Count == 0)
        {
            return;
        }

        using (_generalOptions.Profiler().Start("github_api_get_combined_status"))
        {
            ImmutableListMultimap<string, CheckRun> observedCheckRuns =
                requiredCheckRuns.Count <= ManualCheckRunLookupThreshold
                    ? GetCheckRunsByName(api, project, prData, requiredCheckRuns)
                    : GetCheckRuns(api, project, prData);

            var missing = new List<string>();
            foreach (string requiredCheckRun in requiredCheckRuns)
            {
                if (!observedCheckRuns.ContainsKey(requiredCheckRun))
                {
                    missing.Add(requiredCheckRun);
                    continue;
                }
                var matchingCheckRuns = observedCheckRuns.Get(requiredCheckRun);
                _console.WarnFmtIf(
                    matchingCheckRuns.Length > 1,
                    "Matching check run with name '{0}' seen {1} times. Consider using a more"
                        + " specific name to avoid ambiguity. The instances of this check run are: {2}",
                    requiredCheckRun,
                    matchingCheckRuns.Length,
                    string.Join(", ", matchingCheckRuns.Select(e => e.ToString())));
                bool hasMatch = matchingCheckRuns.Any(e => e.GetConclusion() == "success");
                if (!hasMatch)
                {
                    missing.Add(requiredCheckRun);
                }
            }

            if (missing.Count != 0)
            {
                throw new EmptyChangeException(
                    $"Cannot migrate http://github.com/{project}/pull/{prData.GetNumber()} because"
                        + " the following check runs have not been passed: ["
                        + string.Join(", ", missing.Distinct()) + "]");
            }
        }
    }

    private ImmutableListMultimap<string, CheckRun> GetCheckRunsByName(
        GitHubApiClient api, string project, PullRequest prData, IReadOnlySet<string> requiredCheckRuns)
    {
        var checkRunAggregator = ImmutableListMultimap<string, CheckRun>.CreateBuilder();
        using (_generalOptions.Profiler().Start("github_api_get_check_runs_by_name"))
        {
            foreach (string requiredCheckRun in requiredCheckRuns)
            {
                var specificCheckRuns =
                    api.GetCheckRunsAsync(project, prData.GetHead()!.GetSha()!, requiredCheckRun)
                        .GetAwaiter().GetResult();
                checkRunAggregator.PutAll(requiredCheckRun, specificCheckRuns);
            }
        }
        return checkRunAggregator.Build();
    }

    private ImmutableListMultimap<string, CheckRun> GetCheckRuns(
        GitHubApiClient api, string project, PullRequest prData)
    {
        using (_generalOptions.Profiler().Start("github_api_get_check_runs"))
        {
            var allCheckRuns =
                api.GetCheckRunsAsync(project, prData.GetHead()!.GetSha()!, checkName: null)
                    .GetAwaiter().GetResult();
            var builder = ImmutableListMultimap<string, CheckRun>.CreateBuilder();
            foreach (var run in allCheckRuns)
            {
                builder.Put(run.GetName()!, run);
            }
            return builder.Build();
        }
    }

    private void CheckReviewApprovers(
        GitHubApiClient api,
        string project,
        PullRequest prData,
        ImmutableListMultimap<string, string>.Builder labelsBuilder)
    {
        if (_reviewState == null)
        {
            return;
        }
        var reviews = api.GetReviewsAsync(project, prData.GetNumber()).GetAwaiter().GetResult();
        ApproverState approverState =
            ShouldMigrateForState(
                _reviewState.Value, reviews, _reviewApprovers, prData.GetHead()!.GetSha()!);
        if (!ForceImport() && !approverState.ShouldMigrate)
        {
            string rejected = "";
            if (!approverState.RejectedReviews.IsEmpty)
            {
                rejected =
                    "\nThe following reviews were ignored because they don't meet the association"
                        + $" requirement of {string.Join(", ", _reviewApprovers)}:\n"
                        + string.Join(
                            "\n",
                            approverState.RejectedReviews.Select(
                                e => $"User {e.Key} - Association: {e.Value}"));
            }
            throw new EmptyChangeException(
                $"Cannot migrate http://github.com/{project}/pull/{prData.GetNumber()} because it is"
                    + $" missing the required approvals (origin is configured as {_reviewState})."
                    + rejected);
        }
        var approvers = new HashSet<string>();
        var others = new HashSet<string>();
        foreach (var review in reviews)
        {
            if (_reviewApprovers.Contains(review.GetAuthorAssociation()))
            {
                approvers.Add(review.GetUser()!.GetLogin()!);
            }
            else
            {
                others.Add(review.GetUser()!.GetLogin()!);
            }
        }
        labelsBuilder.PutAll(GithubPrReviewerApprover, approvers);
        labelsBuilder.PutAll(GithubPrReviewerOther, others);
    }

    public GitRepository GetRepository()
    {
        GitRepository repo = _gitOptions.CachedBareRepoForUrl(_url);
        if (_credentials != null)
        {
            try
            {
                _credentials.Install(repo, _gitOptions.GetConfigCredsFile(_generalOptions));
            }
            catch (IOException e)
            {
                throw new RepoException("Unable to store credentials", e);
            }
        }
        return repo;
    }

    public IOrigin<GitRevision>.IReader<GitRevision> NewReader(
        Glob originFiles, Authoring.Authoring authoring) =>
        new GitHubPrReader(
            this,
            _url,
            originFiles,
            authoring,
            _gitOptions,
            _gitOriginOptions,
            _generalOptions,
            includeBranchCommitLogs: false,
            _submoduleStrategy,
            _excludedSubmodules,
            _firstParent,
            _partialFetch,
            _patchTransformation,
            configPath: null,
            workflowName: null,
            _credentials,
            _gitRepositoryHook);

    public string GetLabelName() => GitRepository.GitOriginRevId;

    public string GetType() => "git.github_pr_origin";

    public ReviewState? GetReviewState() => _reviewState;

    public IReadOnlySet<string> GetRequiredLabels() =>
        _gitHubPrOriginOptions.GetRequiredLabels(_requiredLabelsField);

    public IReadOnlySet<string> GetRequiredStatusContextNames() =>
        _gitHubPrOriginOptions.GetRequiredStatusContextNames(_requiredStatusContextNamesField);

    public IReadOnlySet<string> GetRequiredCheckRuns() =>
        _gitHubPrOriginOptions.GetRequiredCheckRuns(_requiredCheckRunsField);

    public IReadOnlySet<string> GetRetryableLabels() =>
        _gitHubPrOriginOptions.GetRetryableLabels(_retryableLabelsField);

    public ImmutableListMultimap<string, string> Describe(Glob? originFiles)
    {
        var builder = ImmutableListMultimap<string, string>.CreateBuilder();
        builder.Put("type", GetType());
        builder.Put("url", _url);
        if (_branch != null)
        {
            builder.Put("branch", _branch);
        }
        if (originFiles != null
            && !originFiles.Roots().IsEmpty
            && !originFiles.Roots().Contains(""))
        {
            builder.PutAll("root", originFiles.Roots());
        }
        if (_reviewState != null)
        {
            builder.Put("review_state", _reviewState.Value.ToString());
            builder.PutAll("review_approvers", _reviewApprovers.Select(a => a.ToString()));
        }
        if (GetRequiredLabels().Count != 0)
        {
            builder.PutAll(GitHubUtil.RequiredLabels, GetRequiredLabels());
        }
        if (GetRequiredStatusContextNames().Count != 0)
        {
            builder.PutAll(GitHubUtil.RequiredStatusContextNames, GetRequiredStatusContextNames());
        }
        if (GetRequiredCheckRuns().Count != 0)
        {
            builder.PutAll(GitHubUtil.RequiredCheckRuns, GetRequiredCheckRuns());
        }
        if (GetRetryableLabels().Count != 0)
        {
            builder.PutAll(GitHubUtil.RetryableLabels, GetRetryableLabels());
        }
        return builder.Build();
    }

    private bool ForceImport() => _generalOptions.IsForced() || _gitHubPrOriginOptions.ForceImport;

    public IApprovalsProvider GetApprovalsProvider() => _provider;

    public IReadOnlyList<ImmutableListMultimap<string, string>> DescribeCredentials() =>
        _credentials == null
            ? ImmutableArray<ImmutableListMultimap<string, string>>.Empty
            : GitDescribeCredentials.Convert(_credentials.DescribeCredentials());

    public override string ToString() => $"GitHubPrOrigin{{ghHost={_ghHost.GetHost()}, url={_url}}}";

    /// <summary>Only migrate PR in one of the following states:</summary>
    public enum StateFilter
    {
        OPEN,
        CLOSED,
        ALL,
    }

    private static bool StateAccepts(StateFilter state, PullRequest pr) =>
        state switch
        {
            StateFilter.OPEN => pr.GetState() == "open",
            StateFilter.CLOSED => pr.GetState() == "closed",
            StateFilter.ALL => true,
            _ => throw new ArgumentOutOfRangeException(nameof(state)),
        };

    public enum ReviewState
    {
        /// <summary>Requires that the current head commit has at least one valid approval.</summary>
        HEAD_COMMIT_APPROVED,

        /// <summary>Any valid approval, even for old commits is good.</summary>
        ANY_COMMIT_APPROVED,

        /// <summary>Reviewers in the change that have commented, asked for changes or approved.</summary>
        HAS_REVIEWERS,

        /// <summary>Import the change regardless of the review state.</summary>
        ANY,
    }

    private static bool ShouldMigrateReviews(
        ReviewState state, IReadOnlyList<Review> reviews, string sha) =>
        state switch
        {
            ReviewState.HEAD_COMMIT_APPROVED =>
                reviews.Any(e => e.GetCommitId() == sha && e.IsApproved()),
            ReviewState.ANY_COMMIT_APPROVED => reviews.Any(e => e.IsApproved()),
            ReviewState.HAS_REVIEWERS => reviews.Count != 0,
            ReviewState.ANY => true,
            _ => throw new ArgumentOutOfRangeException(nameof(state)),
        };

    private static ApproverState ShouldMigrateForState(
        ReviewState state,
        IReadOnlyList<Review> reviews,
        ImmutableHashSet<AuthorAssociation> approvers,
        string sha)
    {
        var authorReviews = new List<Review>();
        var rejectedReviews = new List<Review>();
        foreach (var review in reviews)
        {
            // Only take into account reviews by valid approver types.
            if (approvers.Contains(review.GetAuthorAssociation()))
            {
                authorReviews.Add(review);
            }
            else
            {
                rejectedReviews.Add(review);
            }
        }
        return ApproverState.Create(
            ShouldMigrateReviews(state, authorReviews, sha), rejectedReviews);
    }

    /// <summary>Holds the result of evaluating a <see cref="ReviewState"/> against reviews.</summary>
    public sealed class ApproverState
    {
        public bool ShouldMigrate { get; }
        public ImmutableListMultimap<string, string> RejectedReviews { get; }

        private ApproverState(
            bool shouldMigrate, ImmutableListMultimap<string, string> rejectedReviews)
        {
            ShouldMigrate = shouldMigrate;
            RejectedReviews = rejectedReviews;
        }

        public static ApproverState Create(bool shouldMigrate, IReadOnlyList<Review> rejectedReviews)
        {
            var rejected = ImmutableListMultimap<string, string>.CreateBuilder();
            foreach (var review in rejectedReviews)
            {
                rejected.Put(review.GetUser()!.GetLogin()!, review.GetAuthorAssociation().ToString());
            }
            return new ApproverState(shouldMigrate, rejected.Build());
        }
    }

    private sealed class GitHubPrReader : GitOrigin.ReaderImpl
    {
        private readonly GitHubPrOrigin _origin;

        internal GitHubPrReader(
            GitHubPrOrigin origin,
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
            IGitRepositoryHook? gitRepositoryHook)
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
            _origin = origin;
        }

        /// <summary>Disable rebase since this is controlled by useMerge field.</summary>
        protected override void MaybeRebase(GitRepository repo, GitRevision reference, string workdir)
        {
        }

        public override Origin.Baseline<GitRevision>? FindBaseline(
            GitRevision startRevision, string label)
        {
            if (!_origin._baselineFromBranch)
            {
                return base.FindBaseline(startRevision, label);
            }
            return FindBaselinesWithoutLabel(startRevision, limit: 1)
                .Select(e => new Origin.Baseline<GitRevision>(e.GetHash(), e))
                .FirstOrDefault();
        }

        public override IReadOnlyList<GitRevision> FindBaselinesWithoutLabel(
            GitRevision startRevision, int limit)
        {
            var baselineLabels = startRevision.AssociatedLabels().Get(GithubBaseBranchSha1);
            string? baseline = baselineLabels.Length > 0 ? baselineLabels[^1] : null;
            Preconditions.CheckNotNull(
                baseline, "{0} label should be present in {1}", GithubBaseBranchSha1, startRevision);

            GitRevision baselineRev = GetRepository().ResolveReference(baseline!);
            // Don't skip the first change as it is already the baseline.
            var visitor = new BaselinesWithoutLabelVisitor<GitRevision>(
                OriginFiles, limit, toSkip: null, skipFirst: false);
            VisitChanges(baselineRev, visitor);
            return visitor.GetResult();
        }

        public override IEndpoint GetFeedbackEndPoint(Console console)
        {
            _origin._gitHubOptions.ValidateEndpointChecker(_origin._endpointChecker);
            return new GitHubEndPoint(
                _origin._gitHubOptions.NewGitHubApiSupplier(
                    _origin._url, _origin._endpointChecker, _origin._credentials, _origin._ghHost),
                _origin._url,
                console,
                _origin._ghHost,
                _origin._credentials);
        }

        public override Origin.ChangesResponse<GitRevision> Changes(
            GitRevision? fromRef, GitRevision toRef)
        {
            ValidationException.CheckCondition(
                toRef.AssociatedLabels().ContainsKey(GithubPrUseMerge),
                "Cannot determine whether 'use_merge' was set.");
            if (toRef.AssociatedLabel(GithubPrUseMerge).Contains("false"))
            {
                return base.Changes(fromRef, toRef);
            }
            var log = GetRepository().Log(toRef.GetHash()).WithLimit(1).Run();
            var merge = log[0];
            // Fast-forward merge.
            if (merge.Parents.Count == 1)
            {
                return base.Changes(fromRef, toRef);
            }
            // HEAD of the Pull Request.
            GitRevision gitRevision = merge.Parents[1].WithLabels(toRef.AssociatedLabels());
            var prChanges = base.Changes(fromRef, gitRevision);
            // Merge might have an effect, but we are not interested if the PR doesn't touch
            // origin_files.
            if (prChanges.IsEmpty())
            {
                return prChanges;
            }
            try
            {
                var all = new List<Change<GitRevision>>(prChanges.GetChanges());
                // merge commit is sourced from git log which doesn't have url context.
                all.Add(Change(merge.Commit.WithUrl(_origin._url)));
                return Origin.ChangesResponse<GitRevision>.ForChanges(all);
            }
            catch (EmptyChangeException e)
            {
                throw new RepoException(
                    "Error getting the merge commit information: " + merge, e);
            }
        }
    }
}
