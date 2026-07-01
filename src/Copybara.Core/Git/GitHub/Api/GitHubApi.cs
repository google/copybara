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
using System.Text;
using Copybara.Common;
using Copybara.Exceptions;
using ConsoleT = Copybara.Util.Console.Console;
using ProfilerT = Copybara.Profiler.Profiler;

namespace Copybara.Git.GitHub.Api;

/// <summary>
/// A mini API for getting and updating GitHub projects through the GitHub REST API.
/// </summary>
public class GitHubApi
{
    private readonly IGitHubApiTransport _transport;
    private readonly ProfilerT _profiler;
    private readonly ConsoleT? _console;

    public const int MaxPerPage = 100;
    private const int MaxPages = 10;

    public GitHubApi(IGitHubApiTransport transport, ProfilerT profiler)
        : this(transport, profiler, null)
    {
    }

    public GitHubApi(IGitHubApiTransport transport, ProfilerT profiler, ConsoleT? console)
    {
        _transport = Preconditions.CheckNotNull(transport);
        _profiler = Preconditions.CheckNotNull(profiler);
        _console = console;
    }

    /// <summary>Get all the pull requests for a project.</summary>
    /// <param name="projectId">a project in the form of "google/copybara"</param>
    /// <param name="params">query parameters for the list operation</param>
    public Task<IReadOnlyList<PullRequest>> GetPullRequestsAsync(
        string projectId, PullRequestListParams @params)
    {
        Preconditions.CheckNotNull(@params);
        return PaginatedGetAsync<PullRequest, PaginatedList<PullRequest>>(
            "github_api_list_pulls",
            "Project",
            ImmutableListMultimap<string, string>.Empty,
            string.Format(
                "repos/{0}/pulls?per_page={1}{2}", projectId, MaxPerPage, @params.ToParams()),
            "GET repos/%s/pulls");
    }

    /// <summary>
    /// Get a specific pull request for a project.
    /// </summary>
    public async Task<PullRequest> GetPullRequestAsync(string projectId, long number)
    {
        using ProfilerTaskScope ignore = Scope("github_api_get_pull");
        try
        {
            return (await _transport.GetAsync<PullRequest>(
                $"repos/{projectId}/pulls/{number}", "GET repos/%s/pulls/%d").ConfigureAwait(false))!;
        }
        catch (GitHubApiException e)
        {
            throw TreatGitHubException(e, "Pull Request");
        }
    }

    /// <summary>Get comments for a specific pull request.</summary>
    public async Task<PullRequestComment> GetPullRequestCommentAsync(string projectId, long commentId)
    {
        using ProfilerTaskScope ignore = Scope("github_api_get_pull_comment");
        try
        {
            return (await _transport.GetAsync<PullRequestComment>(
                $"repos/{projectId}/pulls/comments/{commentId}",
                "GET repos/%s/pulls/comments/%d").ConfigureAwait(false))!;
        }
        catch (GitHubApiException e)
        {
            throw TreatGitHubException(e, "Pull Request Comment");
        }
    }

    /// <summary>Get comments for a specific pull request.</summary>
    public Task<IReadOnlyList<PullRequestComment>> GetPullRequestCommentsAsync(
        string projectId, long prNumber)
    {
        return PaginatedGetAsync<PullRequestComment, PaginatedList<PullRequestComment>>(
            "github_api_get_reviews",
            "Pull Request Comments",
            ImmutableListMultimap<string, string>.Empty,
            $"repos/{projectId}/pulls/{prNumber}/comments?per_page={MaxPerPage}",
            "GET repos/%s/pulls/%d/comments");
    }

    /// <summary>Get reviews for a pull request.</summary>
    public Task<IReadOnlyList<Review>> GetReviewsAsync(string projectId, long number)
    {
        return PaginatedGetAsync<Review, PaginatedList<Review>>(
            "github_api_get_reviews",
            "Pull Request or project",
            ImmutableListMultimap<string, string>.Empty,
            $"repos/{projectId}/pulls/{number}/reviews?per_page={MaxPerPage}",
            "GET repos/%s/pulls/%d/reviews");
    }

    private async Task<IReadOnlyList<T>> PaginatedGetAsync<T, TResponse>(
        string profilerName,
        string entity,
        ImmutableListMultimap<string, string> headers,
        string path,
        string requestTemplate)
        where TResponse : class, IPaginatedPayload<T>
    {
        var builder = ImmutableArray.CreateBuilder<T>();
        int pages = 0;
        string? currentPath = path;
        while (currentPath != null && pages < MaxPages)
        {
            using ProfilerTaskScope ignore = Scope($"{profilerName}_page_{pages}");
            try
            {
                TResponse? response =
                    await _transport.GetAsync<TResponse>(currentPath, headers, requestTemplate)
                        .ConfigureAwait(false);
                PaginatedList<T> page = response!.GetPayload();
                builder.AddRange(page.GetElements());
                currentPath = page.NextUrl;
                pages++;
            }
            catch (GitHubApiException e)
            {
                throw TreatGitHubException(e, entity);
            }
        }

        if (pages == MaxPages && _console != null && currentPath != null)
        {
            _console.WarnFmt(
                "Copybara ran a paginated GET request {0} to GitHub for {1} pages, and that is the"
                + " maximum number of pages Copybara will read. It is possible that additional pages"
                + " were not read.",
                currentPath, MaxPages);
        }

        return builder.ToImmutable();
    }

    /// <summary>Create a pull request.</summary>
    public async Task<PullRequest> CreatePullRequestAsync(string projectId, CreatePullRequest request)
    {
        using ProfilerTaskScope ignore = Scope("github_api_create_pull");
        return (await _transport.PostAsync<PullRequest>(
            $"repos/{projectId}/pulls", request, "POST repos/%s/pulls").ConfigureAwait(false))!;
    }

    /// <summary>
    /// Adds assignees to an issue or pull request.
    /// https://docs.github.com/en/rest/issues/assignees#add-assignees-to-an-issue
    /// </summary>
    public async Task<Issue> AddAssigneesAsync(string projectId, long number, AddAssignees request)
    {
        using ProfilerTaskScope ignore = Scope("github_api_add_assignees");
        return (await _transport.PostAsync<Issue>(
            $"repos/{projectId}/issues/{number}/assignees", request,
            "POST repos/%s/issues/%d/assignees").ConfigureAwait(false))!;
    }

    /// <summary>Update a pull request.</summary>
    public async Task<PullRequest> UpdatePullRequestAsync(
        string projectId, long number, UpdatePullRequest request)
    {
        using ProfilerTaskScope ignore = Scope("github_api_update_pull");
        return (await _transport.PostAsync<PullRequest>(
            $"repos/{projectId}/pulls/{number}", request, "POST repos/%s/pulls/%s")
            .ConfigureAwait(false))!;
    }

    /// <summary>
    /// Listing issues and pull requests based on <paramref name="params"/>.
    /// https://docs.github.com/en/rest/search#search-issues-and-pull-requests
    /// </summary>
    public async Task<IssuesAndPullRequestsSearchResults> GetIssuesOrPullRequestsSearchResultsAsync(
        IssuesAndPullRequestsSearchRequestParams @params)
    {
        using ProfilerTaskScope ignore = Scope("github_api_search_issues_or_pull_requests");
        return (await _transport.GetAsync<IssuesAndPullRequestsSearchResults>(
            $"search/issues?q={@params.ToParams()}", "GET search/issues?q=%s").ConfigureAwait(false))!;
    }

    /// <summary>
    /// Get a user's permission level.
    /// https://developer.github.com/v3/repos/collaborators/#review-a-users-permission-level
    /// </summary>
    public async Task<UserPermissionLevel> GetUserPermissionLevelAsync(
        string projectId, string usrLogin)
    {
        using ProfilerTaskScope ignore = Scope("github_api_update_pull");
        return (await _transport.GetAsync<UserPermissionLevel>(
            $"repos/{projectId}/collaborators/{usrLogin}/permission",
            "GET repos/%s/collaborators/%s/permission").ConfigureAwait(false))!;
    }

    /// <summary>
    /// Get authenticated User. https://developer.github.com/v3/users/#get-the-authenticated-user
    /// </summary>
    public async Task<User> GetAuthenticatedUserAsync()
    {
        using ProfilerTaskScope ignore = Scope("github_api_get_authenticated_user");
        return (await _transport.GetAsync<User>("user", "GET user").ConfigureAwait(false))!;
    }

    /// <summary>
    /// Get a specific issue for a project.
    ///
    /// <para>Use this method to get the Pull Request labels.</para>
    /// </summary>
    public async Task<Issue> GetIssueAsync(string projectId, long number)
    {
        using ProfilerTaskScope ignore = Scope("github_api_get_issue");
        try
        {
            return (await _transport.GetAsync<Issue>(
                $"repos/{projectId}/issues/{number}", "GET repos/%s/issues/%d")
                .ConfigureAwait(false))!;
        }
        catch (GitHubApiException e)
        {
            throw TreatGitHubException(e, "Issue");
        }
    }

    /// <summary>Create an issue.</summary>
    public async Task<Issue> CreateIssueAsync(string projectId, Issue.CreateIssueRequest request)
    {
        using ProfilerTaskScope ignore = Scope("github_api_create_issue");
        return (await _transport.PostAsync<Issue>(
            $"repos/{projectId}/issues", request, "POST repos/%s/issues").ConfigureAwait(false))!;
    }

    /// <summary>Get all the refs for a repo (git ls-remote).</summary>
    public async Task<IReadOnlyList<Ref>> GetLsRemoteAsync(string projectId)
    {
        using ProfilerTaskScope ignore = Scope("github_api_list_refs");
        try
        {
            List<Ref>? result = await _transport.GetAsync<List<Ref>>(
                $"repos/{projectId}/git/refs?per_page={MaxPerPage}", "GET repos/%s/git/refs")
                .ConfigureAwait(false);
            return (result ?? new List<Ref>()).ToImmutableArray();
        }
        catch (GitHubApiException e)
        {
            // Per https://developer.github.com/v3/git/, GH returns 409 - conflict if the repo is
            // empty or in the process of being created.
            if (e.GetResponseCode() == GitHubApiResponseCode.CONFLICT)
            {
                return ImmutableArray<Ref>.Empty;
            }

            throw;
        }
    }

    public async Task<Status> CreateStatusAsync(
        string projectId, string sha1, CreateStatusRequest request)
    {
        using ProfilerTaskScope ignore = Scope("github_api_create_status");
        Status result = (await _transport.PostAsync<Status>(
            $"repos/{projectId}/statuses/{sha1}", request, "Create status").ConfigureAwait(false))!;
        if (result.GetContext() == null || result.StateRaw == null)
        {
            throw new RepoException(
                "Something went wrong at the GitHub API transport level."
                + $" Context: {result.GetContext()} state: {result.StateRaw}");
        }

        return result;
    }

    public async Task<Ref> UpdateReferenceAsync(
        string projectId, string @ref, UpdateReferenceRequest request)
    {
        Preconditions.CheckArgument(
            @ref.StartsWith("refs/", StringComparison.Ordinal),
            "References has to be complete references in the form of refs/heads/foo. But was: {0}",
            @ref);
        using ProfilerTaskScope ignore = Scope("github_api_update_reference");
        Ref result = (await _transport.PostAsync<Ref>(
            $"repos/{projectId}/git/{@ref}", request, "POST repos/%s/git/%s").ConfigureAwait(false))!;
        if (result.GetRef() == null || result.GetSha() == null || result.GetUrl() == null)
        {
            throw new RepoException(
                "Something went wrong at the GitHub API transport level."
                + $" ref: {result.GetRef()} sha: {result.GetSha()}, url: {result.GetUrl()}");
        }

        return result;
    }

    public async Task DeleteReferenceAsync(string projectId, string @ref)
    {
        Preconditions.CheckArgument(
            @ref.StartsWith("refs/", StringComparison.Ordinal),
            "References has to be complete references in the form of refs/heads/foo. But was: {0}",
            @ref);
        // There is no good reason for deleting master.
        Preconditions.CheckArgument(
            @ref != "refs/heads/master",
            "Copybara doesn't allow to delete master branch for security reasons");

        using ProfilerTaskScope ignore = Scope("github_api_delete_reference");
        await _transport.DeleteAsync($"repos/{projectId}/git/{@ref}", "DELETE repos/%s/git/%s")
            .ConfigureAwait(false);
    }

    public async Task<Ref> GetReferenceAsync(string projectId, string @ref)
    {
        using ProfilerTaskScope ignore = Scope("github_api_get_reference");
        ValidationException.CheckCondition(
            @ref.StartsWith("refs/", StringComparison.Ordinal), "Ref must start with \"refs/\"");
        return (await _transport.GetAsync<Ref>(
            $"repos/{projectId}/git/{@ref}", "GET repos/%s/git/%s").ConfigureAwait(false))!;
    }

    public async Task<Repository> GetRepositoryAsync(string projectId)
    {
        using ProfilerTaskScope ignore = Scope("github_api_get_repository");
        return (await _transport.GetAsync<Repository>(
            $"repos/{projectId}", "GET repos/%s").ConfigureAwait(false))!;
    }

    public Task<IReadOnlyList<Ref>> GetReferencesAsync(string projectId)
    {
        using ProfilerTaskScope ignore = Scope("github_api_get_references");
        return PaginatedGetAsync<Ref, PaginatedList<Ref>>(
            "github_api_get_references",
            "Project",
            ImmutableListMultimap<string, string>.Empty,
            $"repos/{projectId}/git/refs?per_page={MaxPerPage}",
            "GET repos/%s/git/refs");
    }

    public async Task<CombinedStatus> GetCombinedStatusAsync(string projectId, string @ref)
    {
        using ProfilerTaskScope ignore = Scope("github_api_get_combined_status");
        return (await _transport.GetAsync<CombinedStatus>(
            $"repos/{projectId}/commits/{@ref}/status?per_page={MaxPerPage}",
            "GET repos/%s/commits/%s/status").ConfigureAwait(false))!;
    }

    /// <summary>
    /// Calls the GitHub API REST endpoint to list check runs for a specific ref.
    ///
    /// <para>If <paramref name="checkName"/> is provided, only check runs with that name are
    /// returned.</para>
    /// </summary>
    public Task<IReadOnlyList<CheckRun>> GetCheckRunsAsync(
        string projectId, string @ref, string? checkName)
    {
        using ProfilerTaskScope ignore = Scope("github_api_get_check_runs");
        var headers = ImmutableListMultimap<string, string>.Of(
            "Accept", "application/vnd.github.antiope-preview+json");
        if (string.IsNullOrEmpty(checkName))
        {
            return PaginatedGetAsync<CheckRun, CheckRuns>(
                "github_api_get_check_runs_get",
                "Check Run",
                headers,
                $"repos/{projectId}/commits/{@ref}/check-runs?per_page={MaxPerPage}",
                "GET repos/%s/commits/%s/check-runs");
        }

        return PaginatedGetAsync<CheckRun, CheckRuns>(
            "github_api_get_check_runs_get",
            "Check Run",
            headers,
            $"repos/{projectId}/commits/{@ref}/check-runs?per_page={MaxPerPage}&check_name={checkName}",
            "GET repos/%s/commits/%s/check-runs");
    }

    /// <summary>
    /// Calls the GitHub API REST endpoint to list check runs for a specific ref.
    /// </summary>
    public Task<IReadOnlyList<CheckRun>> GetCheckRunsAsync(string projectId, string @ref) =>
        GetCheckRunsAsync(projectId, @ref, checkName: null);

    /// <summary>
    /// https://docs.github.com/en/rest/checks/suites/#list-check-suites-for-a-git-reference
    /// </summary>
    public Task<IReadOnlyList<CheckSuite>> GetCheckSuitesAsync(string projectId, string @ref)
    {
        using ProfilerTaskScope ignore = Scope("github_api_get_check_suites");
        var headers = ImmutableListMultimap<string, string>.Of(
            "Accept", "application/vnd.github.antiope-preview+json");
        return PaginatedGetAsync<CheckSuite, CheckSuites>(
            "github_api_get_check_runs_get",
            "Check Run",
            headers,
            $"repos/{projectId}/commits/{@ref}/check-suites?per_page={MaxPerPage}",
            "GET repos/%s/commits/%s/check-suites");
    }

    public async Task<GitHubCommit> GetCommitAsync(string projectId, string @ref)
    {
        using ProfilerTaskScope ignore = Scope("github_api_get_commit");
        return (await _transport.GetAsync<GitHubCommit>(
            $"repos/{projectId}/commits/{@ref}", "GET repos/%s/commits/%s").ConfigureAwait(false))!;
    }

    /// <summary>https://developer.github.com/v3/issues/labels/#add-labels-to-an-issue</summary>
    public async Task<IReadOnlyList<Label>> AddLabelsAsync(
        string project, long prNumber, IReadOnlyList<string> labels)
    {
        using ProfilerTaskScope ignore = Scope("github_api_add_labels");
        List<Label>? result = await _transport.PostAsync<List<Label>>(
            $"repos/{project}/issues/{prNumber}/labels", new AddLabels(labels),
            "POST repos/%s/issues/%s/labels").ConfigureAwait(false);
        return (result ?? new List<Label>()).ToImmutableArray();
    }

    /// <summary>https://docs.github.com/en/rest/reference/issues#create-an-issue-comment</summary>
    public async Task<PullRequestComment> PostCommentAsync(
        string projectId, int issueNumber, string comment)
    {
        using ProfilerTaskScope ignore = Scope("github_api_post_comment");
        var request = new CommentBody(comment);
        return (await _transport.PostAsync<PullRequestComment>(
            $"repos/{projectId}/issues/{issueNumber}/comments", request,
            "POST repos/%s/issues/%d/comments").ConfigureAwait(false))!;
    }

    /// <summary>
    /// This HTTP request call requires admin:read permissions at the org level.
    /// https://docs.github.com/en/rest/orgs/orgs#list-app-installations-for-an-organization
    /// </summary>
    public Task<IReadOnlyList<Installation>> GetInstallationsAsync(string org)
    {
        using ProfilerTaskScope ignore = Scope("github_api_get_installations");
        var headers = ImmutableListMultimap<string, string>.Of(
            "Accept", "application/vnd.github.groot-preview+json");
        return PaginatedGetAsync<Installation, Installations>(
            "github_api_get_installations",
            "App Installation",
            headers,
            $"orgs/{org}/installations?per_page={MaxPerPage}",
            "GET orgs/%s/installations");
    }

    /// <summary>
    /// This HTTP request call requires admin:read permissions at the org level for some response
    /// values. https://docs.github.com/en/rest/orgs/orgs#get-an-organization
    /// </summary>
    public async Task<Organization> GetOrganizationAsync(string org)
    {
        using ProfilerTaskScope ignore = Scope("github_api_get_an_organization");
        return (await _transport.GetAsync<Organization>(
            $"orgs/{org}", "GET orgs/%s").ConfigureAwait(false))!;
    }

    /// <summary>
    /// Create a release.
    /// https://docs.github.com/en/rest/releases/releases#create-a-release
    /// </summary>
    public async Task<Release> CreateReleaseAsync(string project, CreateReleaseRequest req)
    {
        using ProfilerTaskScope ignore = Scope("github_api_create_release");
        return (await _transport.PostAsync<Release>(
            $"/repos/{project}/releases", req, "POST /repos/%s/releases").ConfigureAwait(false))!;
    }

    /// <summary>https://docs.github.com/en/rest/issues/comments#list-issue-comments</summary>
    public Task<IReadOnlyList<IssueComment>> ListIssueCommentsAsync(
        string projectId, int issueNumber)
    {
        using ProfilerTaskScope ignore = Scope("github_api_list_issue_comments");
        var headers = ImmutableListMultimap<string, string>.Of(
            "Accept", "application/vnd.github.groot-preview+json");
        return PaginatedGetAsync<IssueComment, PaginatedList<IssueComment>>(
            "github_api_list_issue_comments",
            "Issue comment",
            headers,
            $"repos/{projectId}/issues/{issueNumber}/comments?per_page={MaxPerPage}",
            "GET repos/%s/issues/%d/comments");
    }

    public string GetTransportClassName() => _transport.GetType().FullName!;

    /// <exception cref="ValidationException"/>
    /// <exception cref="GitHubApiException"/>
    private RepoException TreatGitHubException(GitHubApiException e, string entity)
    {
        if (e.GetResponseCode() == GitHubApiResponseCode.NOT_FOUND)
        {
            throw new ValidationException($"{entity} not found: {e.GetRawError()}", e);
        }

        throw e;
    }

    private ProfilerTaskScope Scope(string description) => new(_profiler.Start(description));

    /// <summary>Bridges the profiler task to a using-scope.</summary>
    private readonly struct ProfilerTaskScope : IDisposable
    {
        private readonly ProfilerT.ProfilerTask _task;

        public ProfilerTaskScope(ProfilerT.ProfilerTask task) => _task = task;

        public void Dispose() => _task.Close();
    }

    /// <summary>Creates param:value filter components.</summary>
    public class IssuesAndPullRequestsSearchRequestParams
    {
        /// <summary>Filters for issues or pr.</summary>
        public enum SearchType
        {
            ISSUE,
            PULL_REQUEST,
        }

        /// <summary>Filters for closed or open state.</summary>
        public enum State
        {
            OPEN,
            CLOSED,
        }

        private readonly string _commit;
        private readonly string _repo;
        private readonly string _type;
        private readonly string _state;

        private static string WithParameter(string parameter, string? value) =>
            !string.IsNullOrEmpty(value) ? $"{parameter}:{value}" : "";

        private static string TypeParamValue(SearchType type) =>
            type == SearchType.ISSUE ? "issue" : "pr";

        /// <summary>Creates filter params for searching issues and pull requests.</summary>
        /// <param name="repo">project name in the example form of google/copybara</param>
        /// <param name="commit">filter issues and pull requests by involved commit sha.</param>
        /// <param name="type">Filter for issues pull requests.</param>
        /// <param name="state">Filter for closed or open pull requests and issues.</param>
        public IssuesAndPullRequestsSearchRequestParams(
            string repo, string commit, SearchType type, State state)
        {
            _commit = WithParameter("commit", commit);
            _repo = WithParameter("repo", repo);
            _type = WithParameter("is", TypeParamValue(type));
            _state = WithParameter("state", state.ToString().ToLowerInvariant());
        }

        public string ToParams() =>
            string.Join(
                "+",
                new[] { _repo, _commit, _type, _state }.Where(v => !string.IsNullOrEmpty(v)));

        public override bool Equals(object? obj) =>
            obj is IssuesAndPullRequestsSearchRequestParams other
            && ToParams().Equals(other.ToParams());

        public override int GetHashCode() => ToParams().GetHashCode();
    }

    /// <summary>Query parameters for listing pull requests.</summary>
    public class PullRequestListParams
    {
        public enum StateFilter
        {
            OPEN,
            CLOSED,
            ALL,
        }

        public enum SortFilter
        {
            CREATED,
            UPDATED,
            POPULARITY,
        }

        public enum DirectionFilter
        {
            ASC,
            DESC,
        }

        private readonly StateFilter? _state;
        private readonly string? _head;
        private readonly string? _base;
        private readonly SortFilter? _sort;
        private readonly DirectionFilter? _direction;

        public static readonly PullRequestListParams Default =
            new(null, null, null, null, null);

        private PullRequestListParams(
            StateFilter? state,
            string? head,
            string? @base,
            SortFilter? sort,
            DirectionFilter? direction)
        {
            _state = state;
            _head = head;
            _base = @base;
            _sort = sort;
            _direction = direction;
        }

        public PullRequestListParams WithState(StateFilter? state) =>
            new(state, _head, _base, _sort, _direction);

        public PullRequestListParams WithHead(string? head) =>
            new(_state, head, _base, _sort, _direction);

        public PullRequestListParams WithBase(string? @base) =>
            new(_state, _head, @base, _sort, _direction);

        public PullRequestListParams WithSort(SortFilter? sort) =>
            new(_state, _head, _base, sort, _direction);

        public PullRequestListParams WithDirection(DirectionFilter? direction) =>
            new(_state, _head, _base, _sort, direction);

        public string ToParams()
        {
            var result = new StringBuilder();
            if (_state != null)
            {
                result.Append("&state=").Append(_state.ToString()!.ToLowerInvariant());
            }

            if (_head != null)
            {
                result.Append("&head=").Append(_head);
            }

            if (_base != null)
            {
                result.Append("&base=").Append(_base);
            }

            if (_sort != null)
            {
                result.Append("&sort=").Append(_sort.ToString()!.ToLowerInvariant());
            }

            if (_direction != null)
            {
                result.Append("&direction=").Append(_direction.ToString()!.ToLowerInvariant());
            }

            return result.ToString();
        }
    }
}
