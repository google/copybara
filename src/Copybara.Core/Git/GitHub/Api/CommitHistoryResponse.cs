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

namespace Copybara.Git.GitHub.Api;

/// <summary>POJO representing the response from GitHubGraphQLApi.GET_COMMIT_HISTORY_QUERY.</summary>
public class CommitHistoryResponse
{
    [JsonPropertyName("data")]
    public CommitHistoryData? Data { get; set; }

    public CommitHistoryData? GetData() => Data;

    public override string ToString() => $"CommitHistoryResponse{{data={Data}}}";

    /// <summary>Represents 'data' values.</summary>
    public class CommitHistoryData
    {
        [JsonPropertyName("repository")]
        public CommitHistoryRepository? Repository { get; set; }

        public CommitHistoryRepository? GetRepository() => Repository;

        public override string ToString() => $"Data{{repository={Repository}}}";
    }

    /// <summary>Represents 'repository' values.</summary>
    public class CommitHistoryRepository
    {
        [JsonPropertyName("ref")]
        public CommitHistoryRef? Ref { get; set; }

        public CommitHistoryRef? GetRef() => Ref;

        public override string ToString() => $"Repository{{ref={Ref}}}";
    }

    /// <summary>Represents 'ref' values.</summary>
    public class CommitHistoryRef
    {
        [JsonPropertyName("target")]
        public Target? Target { get; set; }

        public Target? GetTarget() => Target;

        public override string ToString() => $"Ref{{target={Target}}}";
    }

    /// <summary>Represents 'target' values.</summary>
    public class Target
    {
        [JsonPropertyName("id")]
        public string? Id { get; set; }

        [JsonPropertyName("history")]
        public HistoryNodes? HistoryNodes { get; set; }

        public string? GetId() => Id;

        public HistoryNodes? GetHistoryNodes() => HistoryNodes;

        public override string ToString() => $"Target{{id={Id}, historyNodes={HistoryNodes}}}";
    }

    /// <summary>Represents 'history.nodes' values.</summary>
    public class HistoryNodes
    {
        [JsonPropertyName("nodes")]
        public List<HistoryNode>? Nodes { get; set; }

        public List<HistoryNode>? GetNodes() => Nodes;

        public override string ToString() => $"HistoryNodes{{nodes={Nodes}}}";
    }

    /// <summary>Represents 'history.node' element values.</summary>
    public class HistoryNode
    {
        [JsonPropertyName("associatedPullRequests")]
        public AssociatedPullRequests? AssociatedPullRequests { get; set; }

        [JsonPropertyName("id")]
        public string? Id { get; set; }

        [JsonPropertyName("oid")]
        public string? Oid { get; set; }

        public AssociatedPullRequests? GetAssociatedPullRequests() => AssociatedPullRequests;

        public string? GetId() => Id;

        public string? GetOid() => Oid;

        public override string ToString() =>
            $"HistoryNode{{associatedPullRequest={AssociatedPullRequests}, id={Id}, oid={Oid}}}";
    }

    /// <summary>Represents 'associatedPullRequests' values.</summary>
    public class AssociatedPullRequests
    {
        [JsonPropertyName("edges")]
        public List<PullRequestEdges>? Edges { get; set; }

        public List<PullRequestEdges>? GetEdges() => Edges;

        public override string ToString() => $"AssociatedPullRequests{{edges={Edges}}}";
    }

    /// <summary>Represents 'associatedPullRequests.edges' values.</summary>
    public class PullRequestEdges
    {
        [JsonPropertyName("node")]
        public AssociatedPullRequestNode? Node { get; set; }

        public AssociatedPullRequestNode? GetNode() => Node;

        public override string ToString() => $"PullRequestEdges{{node={Node}}}";
    }

    /// <summary>Represents 'associatedPullRequests.edges.node' values.</summary>
    public class AssociatedPullRequestNode
    {
        [JsonPropertyName("reviewDecision")]
        public string? ReviewDecision { get; set; }

        [JsonPropertyName("author")]
        public Author? Author { get; set; }

        [JsonPropertyName("latestOpinionatedReviews")]
        public LatestOpinionatedReviews? LatestOpinionatedReviews { get; set; }

        [JsonPropertyName("mergedBy")]
        public MergedBy? MergedBy { get; set; }

        [JsonPropertyName("title")]
        public string? Title { get; set; }

        public string? GetReviewDecision() => ReviewDecision;

        public Author? GetAuthor() => Author;

        public LatestOpinionatedReviews? GetLatestOpinionatedReviews() => LatestOpinionatedReviews;

        public MergedBy? GetMergedBy() => MergedBy;

        public string? GetTitle() => Title;

        public override string ToString() =>
            $"AssociatedPullRequestNode{{reviewDecision={ReviewDecision}, author={Author},"
            + $" latestOpinionatedReviews={LatestOpinionatedReviews}, mergedBy={MergedBy},"
            + $" title={Title}}}";
    }

    /// <summary>Represents 'latestOpinionatedReviews' values.</summary>
    public class LatestOpinionatedReviews
    {
        [JsonPropertyName("edges")]
        public List<AuthorEdges>? Edges { get; set; }

        public List<AuthorEdges>? GetEdges() => Edges;

        public override string ToString() => $"LatestOpinionatedReviews{{edges={Edges}}}";
    }

    /// <summary>Represents 'latestOptionatedReviews.edges' values.</summary>
    public class AuthorEdges
    {
        [JsonPropertyName("node")]
        public AuthorNode? Node { get; set; }

        public AuthorNode? GetNode() => Node;

        public override string ToString() => $"AuthorEdges{{node={Node}}}";
    }

    /// <summary>Represents 'latestOpinionatedReviews.edges.node' values.</summary>
    public class AuthorNode
    {
        [JsonPropertyName("author")]
        public Author? Author { get; set; }

        [JsonPropertyName("state")]
        public string? State { get; set; }

        public string? GetState() => State;

        public Author? GetAuthor() => Author;

        public override string ToString() => $"AuthorNode{{author={Author}, state={State}}}";
    }

    /// <summary>Represents 'author' values.</summary>
    public class Author
    {
        [JsonPropertyName("login")]
        public string? Login { get; set; }

        public string? GetLogin() => Login;

        public override string ToString() => $"Author{{login={Login}}}";
    }

    /// <summary>Represents 'mergedBy' values.</summary>
    public class MergedBy
    {
        [JsonPropertyName("login")]
        public string? Login { get; set; }

        public string? GetLogin() => Login;

        public override string ToString() => $"MergedBy{{login={Login}}}";
    }
}
