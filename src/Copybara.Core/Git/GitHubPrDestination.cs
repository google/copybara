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
using Copybara.Checks;
using Copybara.Common;
using Copybara.Config;
using Copybara.Effect;
using Copybara.Exceptions;
using Copybara.Git.GitHub.Api;
using Copybara.Git.GitHub.Util;
using Copybara.Revision;
using Copybara.TemplateToken;
using Copybara.Util;
using Console = Copybara.Util.Console.Console;
using GitHubApiClient = Copybara.Git.GitHub.Api.GitHubApi;

namespace Copybara.Git;

/// <summary>
/// A destination for creating/updating GitHub Pull Requests. Port of
/// <c>com.google.copybara.git.GitHubPrDestination</c>.
/// </summary>
public class GitHubPrDestination : IDestination<GitRevision>
{
    // Mirrors GitModule.PRIMARY_BRANCHES.
    private static readonly ImmutableHashSet<string> PrimaryBranches =
        ImmutableHashSet.Create("master", "main");

    private readonly string _url;
    private readonly string _destinationRef;
    private readonly string? _prBranch;
    private readonly bool _partialFetch;
    private readonly bool _draft;
    private readonly bool _primaryBranchMigrationMode;

    private readonly GeneralOptions _generalOptions;
    private readonly GitHubOptions _gitHubOptions;
    private readonly GitDestinationOptions _destinationOptions;
    private readonly GitHubDestinationOptions _gitHubDestinationOptions;
    private readonly GitOptions _gitOptions;
    private readonly GitHubPrWriteHook _writeHook;
    private readonly IEnumerable<GitIntegrateChanges> _integrates;
    private readonly string? _title;
    private readonly string? _body;
    private readonly bool _updateDescription;
    private readonly GitHubHost _ghHost;
    private readonly IChecker? _checker;
    private readonly LazyResourceLoader<GitRepository> _localRepo;
    private readonly ConfigFile _mainConfigFile;
    private readonly IReadOnlyList<string> _assignees;
    private readonly IChecker? _endpointChecker;

    private string? _resolvedDestinationRef;
    internal CredentialFileHandler? Credentials;

    public GitHubPrDestination(
        string url,
        string destinationRef,
        string? prBranch,
        bool partialFetch,
        bool draft,
        GeneralOptions generalOptions,
        GitHubOptions gitHubOptions,
        GitDestinationOptions destinationOptions,
        GitHubDestinationOptions gitHubDestinationOptions,
        GitOptions gitOptions,
        GitHubPrWriteHook writeHook,
        IEnumerable<GitIntegrateChanges> integrates,
        string? title,
        string? body,
        IReadOnlyList<string> assignees,
        ConfigFile mainConfigFile,
        IChecker? endpointChecker,
        bool updateDescription,
        GitHubHost ghHost,
        bool primaryBranchMigrationMode,
        IChecker? checker,
        CredentialFileHandler? credentials)
    {
        _url = Preconditions.CheckNotNull(url);
        _destinationRef = Preconditions.CheckNotNull(destinationRef);
        _prBranch = prBranch;
        _partialFetch = partialFetch;
        _draft = draft;
        _generalOptions = Preconditions.CheckNotNull(generalOptions);
        _gitHubOptions = Preconditions.CheckNotNull(gitHubOptions);
        _destinationOptions = Preconditions.CheckNotNull(destinationOptions);
        _gitHubDestinationOptions = Preconditions.CheckNotNull(gitHubDestinationOptions);
        _gitOptions = Preconditions.CheckNotNull(gitOptions);
        _writeHook = Preconditions.CheckNotNull(writeHook);
        _integrates = Preconditions.CheckNotNull(integrates);
        _title = title;
        _assignees = assignees;
        _body = body;
        _updateDescription = updateDescription;
        _ghHost = Preconditions.CheckNotNull(ghHost);
        _checker = checker;
        Credentials = credentials;
        _localRepo = LazyResourceLoader.Memoized<GitRepository>(
            _ => destinationOptions.LocalGitRepo(url, credentials));
        _mainConfigFile = Preconditions.CheckNotNull(mainConfigFile);
        _endpointChecker = endpointChecker;
        _primaryBranchMigrationMode = primaryBranchMigrationMode;
    }

    public string GetType() => "git.github_pr_destination";

    public ImmutableListMultimap<string, string> Describe(Glob? destinationFiles)
    {
        var builder = ImmutableListMultimap<string, string>.CreateBuilder();
        builder.Put("type", GetType());
        builder.Put("url", _url);
        builder.Put("destination_ref", _destinationRef);
        builder.Put("primaryBranchMigrationMode", _primaryBranchMigrationMode.ToString());
        if (_checker != null)
        {
            builder.Put("checker", _checker.GetType().FullName ?? _checker.GetType().Name);
        }
        if (destinationFiles != null
            && !destinationFiles.Roots().IsEmpty
            && !destinationFiles.Roots().Contains(""))
        {
            builder.PutAll("root", destinationFiles.Roots());
        }
        return builder.Build();
    }

    public IReadOnlyList<ImmutableListMultimap<string, string>> DescribeCredentials() =>
        Credentials == null
            ? ImmutableArray<ImmutableListMultimap<string, string>>.Empty
            : GitDescribeCredentials.Convert(Credentials.DescribeCredentials());

    public IDestination<GitRevision>.IWriter<GitRevision> NewWriter(WriterContext writerContext)
    {
        string prBranch =
            GetPullRequestBranchName(
                writerContext.GetOriginalRevision(),
                writerContext.GetWorkflowName(),
                writerContext.GetWorkflowIdentityUser());
        GitHubPrWriteHook gitHubPrWriteHook = _writeHook.WithUpdatedPrBranch(prBranch);

        var state = new GitHubWriterState(
            _localRepo,
            _destinationOptions.LocalRepoPath != null
                ? prBranch
                : "copybara/push-"
                    + Guid.NewGuid()
                    + (writerContext.IsDryRun() ? "-dryrun" : ""));

        return new GitHubPrWriterImpl(
            this,
            writerContext,
            prBranch,
            gitHubPrWriteHook,
            state);
    }

    internal string AsHttpsUrl() => _ghHost.ProjectAsUrl(GetProjectName());

    internal string GetProjectName() => _ghHost.GetProjectNameFromUrl(_url);

    public bool IsUpdateDescription() => _updateDescription;

    public IEnumerable<GitIntegrateChanges> GetIntegrates() => _integrates;

    private string GetPullRequestBranchName(
        IRevision? changeRevision, string workflowName, string workflowIdentityUser)
    {
        if (!string.IsNullOrEmpty(_gitHubDestinationOptions.DestinationPrBranch))
        {
            return _gitHubDestinationOptions.DestinationPrBranch!;
        }
        string? contextReference = changeRevision?.ContextReference();
        ValidationException.CheckCondition(
            contextReference != null,
            "git.github_pr_destination is incompatible with the current origin. Origin has to be"
                + " able to provide the contextReference or use '{0}' flag",
            GitHubDestinationOptions.GitHubDestinationPrBranch);
        string? branchNameFromUser = GetCustomBranchName(contextReference!);
        string branchName =
            branchNameFromUser
            ?? Identity.ComputeIdentity(
                "OriginGroupIdentity",
                contextReference!,
                workflowName,
                _mainConfigFile.GetIdentifier(),
                workflowIdentityUser);
        return GitHubUtil.GetValidBranchName(branchName);
    }

    private string? GetCustomBranchName(string contextReference)
    {
        if (_prBranch == null)
        {
            return null;
        }
        try
        {
            return new LabelTemplate(_prBranch)
                .Resolve(e => e == "CONTEXT_REFERENCE" ? contextReference : _prBranch);
        }
        catch (LabelTemplate.LabelNotFoundException e)
        {
            throw new ValidationException(
                "Cannot find some labels in the GitHub PR branch name field: " + e.Message, e);
        }
    }

    public string GetLabelNameWhenOrigin() => GitRepository.GitOriginRevId;

    internal string GetDestinationRef()
    {
        if (!_primaryBranchMigrationMode || !PrimaryBranches.Contains(_destinationRef))
        {
            return _destinationRef;
        }
        if (_resolvedDestinationRef == null)
        {
            try
            {
                GitRepository repo = _localRepo.Load(_generalOptions.GetConsole());
                string? primaryBranch = repo.GetPrimaryBranch(_url);
                _resolvedDestinationRef = primaryBranch ?? _destinationRef;
            }
            catch (RepoException e)
            {
                _generalOptions.GetConsole().WarnFmt("Error detecting primary branch: {0}", e.Message);
                _resolvedDestinationRef = _destinationRef;
            }
        }
        return _resolvedDestinationRef;
    }

    private sealed class GitHubWriterState : GitDestination.WriterState
    {
        internal long? PullRequestNumber;

        internal GitHubWriterState(
            LazyResourceLoader<GitRepository> localRepo, string localBranch)
            : base(localRepo, localBranch)
        {
        }
    }

    private sealed class GitHubPrWriterImpl : GitDestination.WriterImpl<GitHubWriterState>
    {
        private readonly GitHubPrDestination _destination;
        private readonly WriterContext _writerContext;
        private readonly string _prBranch;
        private readonly GitHubWriterState _state;

        internal GitHubPrWriterImpl(
            GitHubPrDestination destination,
            WriterContext writerContext,
            string prBranch,
            GitHubPrWriteHook gitHubPrWriteHook,
            GitHubWriterState state)
            : base(
                writerContext.IsDryRun(),
                destination._url,
                destination.GetDestinationRef(),
                prBranch,
                destination._partialFetch,
                tagNameTemplate: null,
                tagMsgTemplate: null,
                destination._generalOptions,
                destination._gitOptions,
                gitHubPrWriteHook,
                state,
                nonFastForwardPush: true,
                destination._integrates,
                destination._destinationOptions.LastRevFirstParent,
                destination._destinationOptions.IgnoreIntegrationErrors,
                destination._destinationOptions.LocalRepoPath,
                destination._destinationOptions.CommitterName,
                destination._destinationOptions.CommitterEmail,
                destination._destinationOptions.RebaseWhenBaseline(),
                destination._gitOptions.VisitChangePageSize,
                destination._gitOptions.GitTagOverwrite,
                destination._checker,
                destination._destinationOptions,
                destination.Credentials)
        {
            _destination = destination;
            _writerContext = writerContext;
            _prBranch = prBranch;
            _state = state;
        }

        public override IReadOnlyList<DestinationEffect> Write(
            TransformResult transformResult, Glob destinationFiles, Console console)
        {
            var result = new List<DestinationEffect>(
                base.Write(transformResult, destinationFiles, console));
            if (_writerContext.IsDryRun() || _state.PullRequestNumber != null)
            {
                return result;
            }

            if (!_destination._gitHubDestinationOptions.CreatePullRequest)
            {
                console.InfoFmt(
                    "Please create a PR manually following this link: {0}/compare/{1}...{2}"
                        + " (Only needed once)",
                    _destination.AsHttpsUrl(),
                    _destination.GetDestinationRef(),
                    _prBranch);
                _state.PullRequestNumber = -1L;
                return result;
            }

            GitHubApiClient api =
                _destination._gitHubOptions.NewGitHubRestApi(
                    _destination._ghHost.GetHost(),
                    _destination.GetProjectName(),
                    null,
                    _destination.Credentials,
                    console);

            var pullRequests =
                api.GetPullRequestsAsync(
                    _destination.GetProjectName(),
                    GitHubApiClient.PullRequestListParams.Default.WithHead(
                        $"{_destination._ghHost.GetUserNameFromUrl(_destination._url)}:{_prBranch}"))
                    .GetAwaiter().GetResult();

            ChangeMessage msg = ChangeMessage.ParseMessage(transformResult.GetSummary().Trim());

            string? title =
                _destination._title == null
                    ? msg.FirstLine()
                    : LabelFinder.MapLabels(
                        transformResult.GetLabelFinder(), _destination._title, "title");

            string prBody =
                _destination._body == null
                    ? msg.ToString()
                    : LabelFinder.MapLabels(
                        transformResult.GetLabelFinder(), _destination._body, "body");
            var assignees =
                LabelFinder.MapLabels(
                    transformResult.GetLabelFinder(), _destination._assignees.ToList());

            foreach (var pr in pullRequests)
            {
                if (pr.GetHead()!.GetRef() == _prBranch)
                {
                    if (!pr.IsOpen())
                    {
                        console.WarnFmt(
                            "Pull request for branch {0} already exists as {1}/pull/{2}, but is"
                                + " closed - reopening.",
                            _prBranch,
                            _destination.AsHttpsUrl(),
                            pr.GetNumber());
                        api.UpdatePullRequestAsync(
                            _destination.GetProjectName(),
                            pr.GetNumber(),
                            new UpdatePullRequest(null, null, UpdatePullRequestState.OPEN))
                            .GetAwaiter().GetResult();
                    }
                    else
                    {
                        console.InfoFmt(
                            "Pull request for branch {0} already exists as {1}/pull/{2}",
                            _prBranch, _destination.AsHttpsUrl(), pr.GetNumber());
                    }
                    if (pr.GetBase()!.GetRef() != _destination.GetDestinationRef())
                    {
                        console.WarnFmt(
                            "Current base branch '{0}' is different from the PR base branch '{1}'",
                            _destination.GetDestinationRef(), pr.GetBase()!.GetRef());
                    }
                    if (_destination._updateDescription)
                    {
                        ValidationException.CheckCondition(
                            !string.IsNullOrEmpty(title),
                            "Pull Request title cannot be empty. Either use 'title' field in"
                                + " git.github_pr_destination or modify the message to not be empty");
                        api.UpdatePullRequestAsync(
                            _destination.GetProjectName(),
                            pr.GetNumber(),
                            new UpdatePullRequest(title, prBody, state: null))
                            .GetAwaiter().GetResult();
                    }
                    result.Add(
                        new DestinationEffect(
                            DestinationEffect.EffectType.UPDATED,
                            $"Pull Request {pr.GetHtmlUrl()} updated",
                            transformResult.GetChanges().GetCurrent().Cast<OriginRef>().ToList(),
                            new DestinationEffect.DestinationRef(
                                pr.GetNumber().ToString(), "pull_request", pr.GetHtmlUrl())));
                    return result;
                }
            }

            ValidationException.CheckCondition(
                !string.IsNullOrEmpty(title),
                "Pull Request title cannot be empty. Either use 'title' field in"
                    + " git.github_pr_destination or modify the message to not be empty");

            PullRequest newPr =
                api.CreatePullRequestAsync(
                    _destination.GetProjectName(),
                    new CreatePullRequest(
                        title!, prBody, _prBranch, _destination.GetDestinationRef(),
                        _destination._draft))
                    .GetAwaiter().GetResult();
            console.InfoFmt(
                "Pull Request {0}/pull/{1} created using branch '{2}'.",
                _destination.AsHttpsUrl(), newPr.GetNumber(), _prBranch);

            if (assignees.Count != 0)
            {
                try
                {
                    api.AddAssigneesAsync(
                        _destination.GetProjectName(),
                        newPr.GetNumber(),
                        new AddAssignees(assignees.ToList()))
                        .GetAwaiter().GetResult();
                }
                catch (RepoException e)
                {
                    console.WarnFmt(
                        "Could not add all assignees ({0}) to {1}/pull/{2} with error '{3}'",
                        string.Join(", ", assignees),
                        _destination.AsHttpsUrl(),
                        newPr.GetNumber(),
                        e.Message);
                }
            }

            _state.PullRequestNumber = newPr.GetNumber();
            result.Add(
                new DestinationEffect(
                    DestinationEffect.EffectType.CREATED,
                    $"Pull Request {newPr.GetHtmlUrl()} created",
                    transformResult.GetChanges().GetCurrent().Cast<OriginRef>().ToList(),
                    new DestinationEffect.DestinationRef(
                        newPr.GetNumber().ToString(), "pull_request", newPr.GetHtmlUrl())));
            return result;
        }

        public override IEndpoint GetFeedbackEndPoint(Console console)
        {
            _destination._gitHubOptions.ValidateEndpointChecker(_destination._endpointChecker);
            return new GitHubEndPoint(
                _destination._gitHubOptions.NewGitHubApiSupplier(
                    _destination._url,
                    _destination._endpointChecker,
                    _destination.Credentials,
                    _destination._ghHost),
                _destination._url,
                console,
                _destination._ghHost,
                _destination.Credentials);
        }
    }
}
