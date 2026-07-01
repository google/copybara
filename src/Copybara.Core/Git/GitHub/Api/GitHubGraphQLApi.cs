/*
 * Copyright (C) 2023 Google Inc.
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

using System.Text.Json.Serialization;
using Copybara.Common;
using Copybara.Exceptions;
using ProfilerT = Copybara.Profiler.Profiler;

namespace Copybara.Git.GitHub.Api;

/// <summary>GraphQL implementation for GitHub client.</summary>
public class GitHubGraphQLApi
{
    private readonly IGitHubApiTransport _transport;
    private readonly ProfilerT _profiler;

    public GitHubGraphQLApi(IGitHubApiTransport transport, ProfilerT profiler)
    {
        _transport = Preconditions.CheckNotNull(transport);
        _profiler = Preconditions.CheckNotNull(profiler);
    }

    /// <summary>GraphQL request body.</summary>
    public class GraphQLRequest
    {
        [JsonPropertyName("query")]
        public string? Query { get; set; }

        [JsonPropertyName("variables")]
        public Dictionary<string, object>? Variables { get; set; }

        public GraphQLRequest(string query, Dictionary<string, object> variables)
        {
            Query = query;
            Variables = variables;
        }

        public GraphQLRequest()
        {
        }

        public string? GetQuery() => Query;

        public Dictionary<string, object>? GetVariables() => Variables;

        public override string ToString() =>
            $"GraphQLRequest{{variables={Variables}, query={Query}}}";
    }

    /// <summary>Sets GraphQL first parameters for the getCommitHistory call.</summary>
    public class GetCommitHistoryParams
    {
        private readonly int _commits;
        private readonly int _pullRequests;
        private readonly int _reviews;

        public GetCommitHistoryParams()
        {
        }

        public GetCommitHistoryParams(int commits, int pullRequests, int reviews)
        {
            _commits = commits;
            _pullRequests = pullRequests;
            _reviews = reviews;
        }

        public int GetCommits() => _commits;

        public int GetPullRequests() => _pullRequests;

        public int GetReviews() => _reviews;

        public GetCommitHistoryParams GetCopyWithCommits(int commits) =>
            new(commits, _pullRequests, _reviews);
    }

    public async Task<CommitHistoryResponse> GetCommitHistoryAsync(
        string org, string repo, string branch, GetCommitHistoryParams @params)
    {
        ValidationException.CheckCondition(
            !string.IsNullOrEmpty(org)
            && !string.IsNullOrEmpty(repo)
            && !string.IsNullOrEmpty(branch),
            "Attempted to query for GitHub commit history, but received a empty/null value: org={0},"
            + " repo={1}, branch={2}",
            org,
            repo,
            branch);

        const string getCommitHistoryQuery =
            "query ($repoName: String!, $repoOwner:String!, $branch: String!,"
            + "$numberOfCommits: Int, $numberOfPRs: Int, "
            + "$numberOfReviews: Int) {\n"
            + "repository(name: $repoName, owner: $repoOwner) {\n"
            + "ref(qualifiedName: $branch) {\n"
            + "target {\n"
            + "... on Commit {\n"
            + "id\n"
            + "history(first: $numberOfCommits) {\n"
            + "nodes {\n"
            + "id\n"
            + "oid\n"
            + "associatedPullRequests(first: $numberOfPRs) {\n"
            + "edges {\n"
            + "node {\n"
            + "title\n"
            + "mergedBy {\n"
            + "login\n"
            + "}\n"
            + "author {\n"
            + "login\n"
            + "}\n"
            + "reviewDecision\n"
            + "latestOpinionatedReviews(first: $numberOfReviews)"
            + "{\n"
            + "edges {\n"
            + "node {\n"
            + "author {\n"
            + "login\n"
            + "}\n"
            + "state\n"
            + "}\n"
            + "}\n"
            + "}\n"
            + "}\n"
            + "}\n"
            + "}\n"
            + "}\n"
            + "}\n"
            + "}\n"
            + "}\n"
            + "}\n"
            + "}\n";

        var variables = new Dictionary<string, object>
        {
            ["repoOwner"] = org,
            ["repoName"] = repo,
            ["branch"] = branch,
            ["numberOfCommits"] = @params.GetCommits(),
            ["numberOfPRs"] = @params.GetPullRequests(),
            ["numberOfReviews"] = @params.GetReviews(),
        };

        using ProfilerTaskScope ignore = new(_profiler.Start("github_api_get_commit_history"));
        return (await _transport.PostAsync<CommitHistoryResponse>(
            "/graphql",
            new GraphQLRequest(getCommitHistoryQuery, variables),
            "POST GraphQL").ConfigureAwait(false))!;
    }

    private readonly struct ProfilerTaskScope : IDisposable
    {
        private readonly ProfilerT.ProfilerTask _task;

        public ProfilerTaskScope(ProfilerT.ProfilerTask task) => _task = task;

        public void Dispose() => _task.Close();
    }
}
