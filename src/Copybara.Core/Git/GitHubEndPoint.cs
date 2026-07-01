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
using System.Text.RegularExpressions;
using Copybara.Common;
using Copybara.Config;
using Copybara.Exceptions;
using Copybara.Git.GitHub.Api;
using Copybara.Git.GitHub.Util;
using Starlark.Annot;
using Starlark.Eval;
using Console = Copybara.Util.Console.Console;
using GitHubApiClient = Copybara.Git.GitHub.Api.GitHubApi;
using StarlarkRt = Starlark.Eval.Starlark;

namespace Copybara.Git;

/// <summary>
/// GitHub specific class used in feedback mechanism and migration event hooks to access GitHub.
/// Port of <c>com.google.copybara.git.GitHubEndPoint</c>.
/// </summary>
[StarlarkBuiltin(
    "github_api_obj",
    Doc = "GitHub API endpoint implementation for feedback migrations and after migration hooks.")]
public sealed class GitHubEndPoint : IEndpoint, IStarlarkValue
{
    private readonly LazyResourceLoader<GitHubApiClient> _apiSupplier;
    private readonly string _url;
    private readonly Console _console;
    private readonly GitHubHost _ghHost;
    private readonly CredentialFileHandler? _credentials;

    // This might not be complete but it is only used for filtering get_pull_requests.
    private static readonly Regex SafeBranchNamePrefix =
        new("^[\\w_.-][\\w/_.-]*$", RegexOptions.Compiled);

    internal GitHubEndPoint(
        LazyResourceLoader<GitHubApiClient> apiSupplier,
        string url,
        Console console,
        GitHubHost ghHost,
        CredentialFileHandler? credentials)
    {
        _apiSupplier = Preconditions.CheckNotNull(apiSupplier);
        _url = Preconditions.CheckNotNull(url);
        _console = Preconditions.CheckNotNull(console);
        _ghHost = ghHost;
        _credentials = credentials;
    }

    [StarlarkMethod(
        "create_status",
        Doc = "Create or update a status for a commit. Returns the status created.")]
    public Status CreateStatus(
        [Param(Name = "sha", Named = true,
            Doc = "The SHA-1 for which we want to create or update the status")]
        string sha,
        [Param(Name = "state", Named = true,
            Doc = "The state of the commit status: 'success', 'error', 'pending' or 'failure'")]
        string state,
        [Param(Name = "context", Named = true,
            Doc = "The context for the commit status. Use a value like"
                + " 'copybara/import_successful' or similar")]
        string context,
        [Param(Name = "description", Named = true,
            Doc = "Description about what happened, maximum 140 characters.")]
        string description,
        [Param(Name = "target_url", Named = true,
            Doc = "Url with expanded information about the event", DefaultValue = "None")]
        object? targetUrl)
    {
        try
        {
            ValidationException.CheckCondition(
                StatusStates.ValidValues.Contains(state),
                "Invalid value for state. Valid values: {0}",
                string.Join(", ", StatusStates.ValidValues));
            ValidationException.CheckCondition(
                GitRevision.CompleteGitHashPattern.IsMatch(sha),
                "Not a valid complete SHA-1: {0}", sha);
            ValidationException.CheckCondition(
                !string.IsNullOrEmpty(description), "description cannot be empty");
            ValidationException.CheckCondition(
                !string.IsNullOrEmpty(context), "context cannot be empty");
            ValidationException.CheckCondition(
                description.Length <= 140, "Description cannot be longer than 140 characters");

            string project = _ghHost.GetProjectNameFromUrl(_url);
            return _apiSupplier.Load(_console).CreateStatusAsync(
                project,
                sha,
                new CreateStatusRequest(
                    Enum.Parse<StatusState>(state, ignoreCase: true),
                    SkylarkUtil.ConvertFromNoneable<string>(targetUrl, null),
                    description,
                    context)).GetAwaiter().GetResult();
        }
        catch (GitHubApiException gae)
        {
            if (gae.GetResponseCode() == GitHubApiResponseCode.UNPROCESSABLE_ENTITY)
            {
                throw new ValidationException(
                    "GitHub was unable to process the request " + gae.GetError(), gae);
            }
            throw;
        }
        catch (Exception e) when (e is ValidationException or RepoException)
        {
            throw StarlarkRt.Errorf("Error calling create_status: {0}", e.Message);
        }
    }

    [StarlarkMethod(
        "get_check_runs",
        Doc = "Get the list of check runs for a sha. This returns a maximum of 1000 check runs.")]
    public StarlarkList GetCheckRuns(
        [Param(Name = "sha", Named = true,
            Doc = "The SHA-1 for which we want to get the check runs")]
        string sha)
    {
        try
        {
            ValidationException.CheckCondition(
                GitRevision.CompleteGitHashPattern.IsMatch(sha),
                "Not a valid complete SHA-1: {0}", sha);
            string project = _ghHost.GetProjectNameFromUrl(_url);
            return StarlarkList.ImmutableCopyOf(
                _apiSupplier.Load(_console).GetCheckRunsAsync(project, sha)
                    .GetAwaiter().GetResult());
        }
        catch (Exception e) when (e is ValidationException or RepoException)
        {
            throw StarlarkRt.Errorf("Error calling get_check_runs: {0}", e.Message);
        }
    }

    [StarlarkMethod(
        "get_combined_status",
        Doc = "Get the combined status for a commit. Returns None if not found.",
        AllowReturnNones = true)]
    public CombinedStatus? GetCombinedStatus(
        [Param(Name = "ref", Named = true,
            Doc = "The SHA-1 or ref for which we want to get the combined status")]
        string @ref)
    {
        try
        {
            ValidationException.CheckCondition(
                !string.IsNullOrEmpty(@ref), "Empty reference not allowed");
            string project = _ghHost.GetProjectNameFromUrl(_url);
            return _apiSupplier.Load(_console).GetCombinedStatusAsync(project, @ref)
                .GetAwaiter().GetResult();
        }
        catch (GitHubApiException e)
        {
            return ReturnNullOnNotFound<CombinedStatus>(e);
        }
        catch (Exception e) when (e is ValidationException or RepoException)
        {
            throw StarlarkRt.Errorf("Error calling get_combined_status: {0}", e.Message);
        }
    }

    [StarlarkMethod(
        "get_commit",
        Doc = "Get information for a commit in GitHub. Returns None if not found.",
        AllowReturnNones = true)]
    public GitHubCommit? GetCommit(
        [Param(Name = "ref", Named = true,
            Doc = "The SHA-1 for which we want to get the combined status")]
        string @ref)
    {
        try
        {
            ValidationException.CheckCondition(
                !string.IsNullOrEmpty(@ref), "Empty reference not allowed");
            string project = _ghHost.GetProjectNameFromUrl(_url);
            return _apiSupplier.Load(_console).GetCommitAsync(project, @ref)
                .GetAwaiter().GetResult();
        }
        catch (GitHubApiException e)
        {
            return ReturnNullOnNotFound<GitHubCommit>(e);
        }
        catch (Exception e) when (e is ValidationException or RepoException)
        {
            throw StarlarkRt.Errorf("Error calling get_commit: {0}", e.Message);
        }
    }

    [StarlarkMethod(
        "update_reference",
        Doc = "Update a reference to point to a new commit. Returns the info of the reference.")]
    public Ref UpdateReference(
        [Param(Name = "ref", Named = true, Doc = "The name of the reference.")] string @ref,
        [Param(Name = "sha", Named = true, Doc = "The id for the commit status.")] string sha,
        [Param(Name = "force", Named = true,
            Doc = "Indicates whether to force the update or to make sure the update is a"
                + " fast-forward update. Default: false")]
        bool force)
    {
        try
        {
            ValidationException.CheckCondition(
                GitRevision.CompleteGitHashPattern.IsMatch(sha),
                "Not a valid complete SHA-1: {0}", sha);
            ValidationException.CheckCondition(!string.IsNullOrEmpty(@ref), "ref cannot be empty");

            if (!@ref.StartsWith("refs/", StringComparison.Ordinal))
            {
                _console.WarnFmt(
                    "Non-complete ref passed to update_reference '{0}'. Assuming refs/heads/{1}",
                    @ref, @ref);
                @ref = "refs/heads/" + @ref;
            }
            string project = _ghHost.GetProjectNameFromUrl(_url);
            return _apiSupplier.Load(_console).UpdateReferenceAsync(
                project, @ref, new UpdateReferenceRequest(sha, force)).GetAwaiter().GetResult();
        }
        catch (Exception e) when (e is ValidationException or RepoException)
        {
            throw StarlarkRt.Errorf("Error calling update_reference: {0}", e.Message);
        }
    }

    [StarlarkMethod("delete_reference", Doc = "Delete a reference.")]
    public void DeleteReference(
        [Param(Name = "ref", Named = true, Doc = "The name of the reference.")] string @ref)
    {
        try
        {
            ValidationException.CheckCondition(!string.IsNullOrEmpty(@ref), "ref cannot be empty");
            ValidationException.CheckCondition(
                @ref.StartsWith("refs/", StringComparison.Ordinal),
                "ref needs to be a complete reference. Example: refs/heads/foo");
            string project = _ghHost.GetProjectNameFromUrl(_url);
            _apiSupplier.Load(_console).DeleteReferenceAsync(project, @ref)
                .GetAwaiter().GetResult();
        }
        catch (Exception e) when (e is ValidationException or RepoException)
        {
            throw StarlarkRt.Errorf("Error calling delete_reference: {0}", e.Message);
        }
    }

    [StarlarkMethod(
        "get_reference",
        Doc = "Get a reference SHA-1 from GitHub. Returns None if not found.",
        AllowReturnNones = true)]
    public Ref? GetReference(
        [Param(Name = "ref", Named = true,
            Doc = "The name of the reference. For example: \"refs/heads/branchName\".")]
        string @ref)
    {
        try
        {
            ValidationException.CheckCondition(!string.IsNullOrEmpty(@ref), "Ref cannot be empty");
            string project = _ghHost.GetProjectNameFromUrl(_url);
            return _apiSupplier.Load(_console).GetReferenceAsync(project, @ref)
                .GetAwaiter().GetResult();
        }
        catch (GitHubApiException e)
        {
            return ReturnNullOnNotFound<Ref>(e);
        }
        catch (Exception e) when (e is ValidationException or RepoException)
        {
            throw StarlarkRt.Errorf("Error calling get_reference: {0}", e.Message);
        }
    }

    [StarlarkMethod(
        "get_pull_requests",
        Doc = "Get Pull Requests for a repo",
        AllowReturnNones = true)]
    public StarlarkList? GetPullRequests(
        [Param(Name = "head_prefix", Named = true,
            Doc = "Only return PRs wher the branch name has head_prefix", DefaultValue = "None")]
        object? headPrefixParam,
        [Param(Name = "base_prefix", Named = true,
            Doc = "Only return PRs where the destination branch name has base_prefix",
            DefaultValue = "None")]
        object? basePrefixParam,
        [Param(Name = "state", Named = true,
            Doc = "State of the Pull Request. Can be `\"OPEN\"`, `\"CLOSED\"` or `\"ALL\"`",
            DefaultValue = "\"OPEN\"")]
        string state,
        [Param(Name = "sort", Named = true,
            Doc = "Sort filter for retrieving the Pull Requests. Can be `\"CREATED\"`,"
                + " `\"UPDATED\"` or `\"POPULARITY\"`", DefaultValue = "\"CREATED\"")]
        string sort,
        [Param(Name = "direction", Named = true,
            Doc = "Direction of the filter. Can be `\"ASC\"` or `\"DESC\"`", DefaultValue = "\"ASC\"")]
        string direction)
    {
        try
        {
            string project = _ghHost.GetProjectNameFromUrl(_url);
            GitHubApiClient.PullRequestListParams request = GitHubApiClient.PullRequestListParams.Default;
            string? headPrefix = SkylarkUtil.ConvertFromNoneable<string>(headPrefixParam, null);
            string? basePrefix = SkylarkUtil.ConvertFromNoneable<string>(basePrefixParam, null);
            if (!string.IsNullOrEmpty(headPrefix))
            {
                ValidationException.CheckCondition(
                    SafeBranchNamePrefix.IsMatch(headPrefix),
                    "'{0}' is not a valid head_prefix ({1} is used for validation)",
                    headPrefix, SafeBranchNamePrefix.ToString());
                request = request.WithHead(headPrefix);
            }
            if (!string.IsNullOrEmpty(basePrefix))
            {
                ValidationException.CheckCondition(
                    SafeBranchNamePrefix.IsMatch(basePrefix),
                    "'{0}' is not a valid base_prefix ({1} is used for validation)",
                    basePrefix, SafeBranchNamePrefix.ToString());
                request = request.WithBase(basePrefix);
            }

            return StarlarkList.ImmutableCopyOf(
                _apiSupplier.Load(_console).GetPullRequestsAsync(
                    project,
                    request
                        .WithState(SkylarkUtil.StringToEnum<GitHubApiClient.PullRequestListParams.StateFilter>(
                            "state", state))
                        .WithDirection(SkylarkUtil.StringToEnum<GitHubApiClient.PullRequestListParams.DirectionFilter>(
                            "direction", direction))
                        .WithSort(SkylarkUtil.StringToEnum<GitHubApiClient.PullRequestListParams.SortFilter>(
                            "sort", sort))).GetAwaiter().GetResult());
        }
        catch (GitHubApiException e)
        {
            return ReturnNullOnNotFound<StarlarkList>(e);
        }
        catch (Exception e) when (e is ValidationException or RepoException)
        {
            throw StarlarkRt.Errorf("Error calling get_pull_requests: {0}", e.Message);
        }
    }

    [StarlarkMethod(
        "update_pull_request",
        Doc = "Update Pull Requests for a repo. Returns None if not found",
        AllowReturnNones = true)]
    public PullRequest? UpdatePullRequest(
        [Param(Name = "number", Named = true, Doc = "Pull Request number")] StarlarkInt number,
        [Param(Name = "title", Named = true, Doc = "New Pull Request title", DefaultValue = "None")]
        object? title,
        [Param(Name = "body", Named = true, Doc = "New Pull Request body", DefaultValue = "None")]
        object? body,
        [Param(Name = "state", Named = true,
            Doc = "State of the Pull Request. Can be `\"OPEN\"`, `\"CLOSED\"`", DefaultValue = "None")]
        object? state)
    {
        try
        {
            string project = _ghHost.GetProjectNameFromUrl(_url);
            string? stateStr = SkylarkUtil.ConvertFromNoneable<string>(state, null);
            return _apiSupplier.Load(_console).UpdatePullRequestAsync(
                project,
                number.ToInt("number"),
                new UpdatePullRequest(
                    SkylarkUtil.ConvertFromNoneable<string>(title, null),
                    SkylarkUtil.ConvertFromNoneable<string>(body, null),
                    stateStr == null
                        ? null
                        : SkylarkUtil.StringToEnum<UpdatePullRequestState>("state", stateStr)))
                .GetAwaiter().GetResult();
        }
        catch (GitHubApiException e)
        {
            return ReturnNullOnNotFound<PullRequest>(e);
        }
        catch (Exception e) when (e is ValidationException or RepoException)
        {
            throw StarlarkRt.Errorf("Error calling update_pull_request: {0}", e.Message);
        }
    }

    [StarlarkMethod(
        "get_authenticated_user",
        Doc = "Get autenticated user info, return null if not found",
        AllowReturnNones = true)]
    public User? GetAuthenticatedUser()
    {
        try
        {
            return _apiSupplier.Load(_console).GetAuthenticatedUserAsync().GetAwaiter().GetResult();
        }
        catch (GitHubApiException e)
        {
            return ReturnNullOnNotFoundOrUnauthorized<User>(e);
        }
        catch (Exception e) when (e is ValidationException or RepoException)
        {
            throw StarlarkRt.Errorf("Error calling get_authenticated_user: {0}", e.Message);
        }
    }

    private static T? ReturnNullOnNotFound<T>(GitHubApiException e)
        where T : class
    {
        SkylarkUtil.Check(
            e.GetResponseCode() == GitHubApiResponseCode.NOT_FOUND, "{0}", e.Message);
        return null;
    }

    private static T? ReturnNullOnNotFoundOrUnauthorized<T>(GitHubApiException e)
        where T : class
    {
        SkylarkUtil.Check(
            e.GetResponseCode() == GitHubApiResponseCode.NOT_FOUND
                || e.GetResponseCode() == GitHubApiResponseCode.UNAUTHORIZED,
            "{0}", e.Message);
        return null;
    }

    [StarlarkMethod(
        "get_references",
        Doc = "Get all the reference SHA-1s from GitHub. Note that Copybara only returns a maximum"
            + " number of 500.")]
    public ISequence<object?> GetReferences()
    {
        try
        {
            string project = _ghHost.GetProjectNameFromUrl(_url);
            return StarlarkList.ImmutableCopyOf(
                _apiSupplier.Load(_console).GetReferencesAsync(project).GetAwaiter().GetResult());
        }
        catch (Exception e) when (e is ValidationException or RepoException)
        {
            throw StarlarkRt.Errorf("Error calling get_references: {0}", e.Message);
        }
    }

    [StarlarkMethod("get_pull_request_comment", Doc = "Get a pull request comment")]
    public PullRequestComment GetPullRequestComment(
        [Param(Name = "comment_id", Named = true, Doc = "Comment identifier")] string commentId)
    {
        try
        {
            long commentIdLong;
            if (!long.TryParse(commentId, out commentIdLong))
            {
                throw StarlarkRt.Errorf("Invalid comment id {0}", commentId);
            }
            string project = _ghHost.GetProjectNameFromUrl(_url);
            return _apiSupplier.Load(_console).GetPullRequestCommentAsync(project, commentIdLong)
                .GetAwaiter().GetResult();
        }
        catch (Exception e) when (e is ValidationException or RepoException)
        {
            throw StarlarkRt.Errorf("Error calling get_pull_request_comment: {0}", e.Message);
        }
    }

    [StarlarkMethod("get_pull_request_comments", Doc = "Get all pull request comments")]
    public ISequence<object?> GetPullRequestComments(
        [Param(Name = "number", Named = true, Doc = "Pull Request number")] StarlarkInt prNumber)
    {
        try
        {
            string project = _ghHost.GetProjectNameFromUrl(_url);
            return StarlarkList.ImmutableCopyOf(
                _apiSupplier.Load(_console).GetPullRequestCommentsAsync(project, prNumber.ToInt("number"))
                    .GetAwaiter().GetResult());
        }
        catch (Exception e) when (e is ValidationException or RepoException)
        {
            throw StarlarkRt.Errorf("Error calling get_pull_request_comments: {0}", e.Message);
        }
    }

    [StarlarkMethod("url", Doc = "Return the URL of this endpoint.", StructField = true)]
    public string GetUrl() => _url;

    [StarlarkMethod("add_label", Doc = "Add labels to a PR/issue")]
    public void AddLabels(
        [Param(Name = "number", Named = true, Doc = "Pull Request number")] StarlarkInt prNumber,
        [Param(Name = "labels", Named = true, Doc = "List of labels to add.")] ISequence<object?> labels)
    {
        try
        {
            string project = _ghHost.GetProjectNameFromUrl(_url);
            _apiSupplier.Load(_console).AddLabelsAsync(
                project,
                prNumber.ToInt("number"),
                SkylarkUtil.ConvertStringList(labels, "Expected list of GitHub label names."))
                .GetAwaiter().GetResult();
        }
        catch (Exception e) when (e is ValidationException or RepoException)
        {
            throw StarlarkRt.Errorf("Error calling add_label: {0}", e.Message);
        }
    }

    [StarlarkMethod("create_issue", Doc = "Create a new issue.")]
    public Issue CreateIssue(
        [Param(Name = "title", Named = true, Doc = "Title of the issue")] string title,
        [Param(Name = "body", Named = true, Doc = "Body of the issue.")] string body,
        [Param(Name = "assignees", Named = true,
            Doc = "GitHub users to whom the issue will be assigned.", DefaultValue = "[]")]
        StarlarkList assignees)
    {
        try
        {
            string project = _ghHost.GetProjectNameFromUrl(_url);
            return _apiSupplier.Load(_console).CreateIssueAsync(
                project,
                new Issue.CreateIssueRequest(
                    title, body, Sequence.Cast<string>(assignees, "assignees").ToList()))
                .GetAwaiter().GetResult();
        }
        catch (Exception e) when (e is ValidationException or RepoException)
        {
            throw StarlarkRt.Errorf("Error calling create_issue: {0}", e.Message);
        }
    }

    [StarlarkMethod("post_issue_comment", Doc = "Post a comment on a issue.")]
    public void PostIssueComment(
        [Param(Name = "number", Named = true, Doc = "Issue or Pull Request number")]
        StarlarkInt prNumber,
        [Param(Name = "comment", Named = true, Doc = "Comment body to post.")] string comment)
    {
        try
        {
            string project = _ghHost.GetProjectNameFromUrl(_url);
            _apiSupplier.Load(_console).PostCommentAsync(project, prNumber.ToInt("number"), comment)
                .GetAwaiter().GetResult();
        }
        catch (Exception e) when (e is ValidationException or RepoException)
        {
            throw StarlarkRt.Errorf("Error calling post_issue_comment: {0}", e.Message);
        }
    }

    [StarlarkMethod("list_issue_comments", Doc = "Lists comments for an issue")]
    public ISequence<object?> ListIssueComments(
        [Param(Name = "number", Named = true, Doc = "Issue or Pull Request number")]
        StarlarkInt issueNumber)
    {
        try
        {
            string project = _ghHost.GetProjectNameFromUrl(_url);
            return StarlarkList.ImmutableCopyOf(
                _apiSupplier.Load(_console).ListIssueCommentsAsync(project, issueNumber.ToInt("number"))
                    .GetAwaiter().GetResult());
        }
        catch (Exception e) when (e is ValidationException or RepoException)
        {
            throw StarlarkRt.Errorf("Error calling list_issue_comments: {0}", e.Message);
        }
    }

    [StarlarkMethod("new_release_request", Doc = "Create a handle for creating a new release.")]
    public CreateReleaseRequest NewReleaseRequest(
        [Param(Name = "tag_name", Named = true, Doc = "The git tag to use for the release.")]
        string tagName)
    {
        try
        {
            return new CreateReleaseRequest(tagName);
        }
        catch (Exception e) when (e is ValidationException or RepoException)
        {
            throw StarlarkRt.Errorf("Error calling new_release_request: {0}", e.Message);
        }
    }

    [StarlarkMethod("create_release", Doc = "Create a new GitHub release.")]
    public Release CreateRelease(
        [Param(Name = "request", Named = true,
            Doc = "The populated release object. See new_release_request.")]
        CreateReleaseRequest request)
    {
        try
        {
            string project = _ghHost.GetProjectNameFromUrl(_url);
            return _apiSupplier.Load(_console).CreateReleaseAsync(project, request)
                .GetAwaiter().GetResult();
        }
        catch (Exception e) when (e is ValidationException or RepoException)
        {
            throw StarlarkRt.Errorf("Error calling new_release_request: {0}", e.Message);
        }
    }

    public IEndpoint WithConsole(Console console) =>
        new GitHubEndPoint(_apiSupplier, _url, console, _ghHost, _credentials);

    public ImmutableListMultimap<string, string> Describe()
    {
        var builder = ImmutableListMultimap<string, string>.CreateBuilder();
        builder.Put("type", "github_api");
        builder.Put("url", _url);
        return builder.Build();
    }

    public IReadOnlyList<ImmutableListMultimap<string, string>> DescribeCredentials() =>
        _credentials == null
            ? ImmutableArray<ImmutableListMultimap<string, string>>.Empty
            : GitDescribeCredentials.Convert(_credentials.DescribeCredentials());

    public override string ToString() => $"GitHubEndPoint{{url={_url}}}";
}
