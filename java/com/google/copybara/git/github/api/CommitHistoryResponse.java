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

package com.google.copybara.git.github.api;

import com.google.api.client.util.Key;
import com.google.common.base.MoreObjects;
import java.util.List;

/** POJO representing the response from GitHubGraphQLApi.GET_COMMIT_HISTORY_QUERY */
public class CommitHistoryResponse {
  @Key private Data data;

  public Data getData() {
    return data;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("data", data).toString();
  }

  /** Represents 'data' values */
  public static class Data {
    @Key private Repository repository;

    public Repository getRepository() {
      return repository;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("repository", repository).toString();
    }
  }

  /** Represents 'repository' values */
  public static class Repository {
    @Key private Ref ref;

    public Ref getRef() {
      return ref;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("ref", ref).toString();
    }
  }

  /** Represents 'ref' values */
  public static class Ref {
    @Key private Target target;

    public Target getTarget() {
      return target;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("target", target).toString();
    }
  }

  /** Represents 'target' values */
  public static class Target {
    @Key private String id;

    @Key("history")
    private HistoryNodes historyNodes;

    public String getId() {
      return id;
    }

    public HistoryNodes getHistoryNodes() {
      return historyNodes;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("id", id)
          .add("historyNodes", historyNodes)
          .toString();
    }
  }

  /** Represents 'history.nodes' values */
  public static class HistoryNodes {
    @Key private List<HistoryNode> nodes;

    public List<HistoryNode> getNodes() {
      return nodes;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("nodes", nodes).toString();
    }
  }

  /** Represents 'history.node' element values */
  public static class HistoryNode {
    @Key private AssociatedPullRequests associatedPullRequests;

    @Key private String id;

    @Key private String oid;

    public AssociatedPullRequests getAssociatedPullRequests() {
      return associatedPullRequests;
    }

    public String getId() {
      return id;
    }

    public String getOid() {
      return oid;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("associatedPullRequest", associatedPullRequests)
          .add("id", id)
          .add("oid", oid)
          .toString();
    }
  }

  /** Represents 'associatedPullRequests' values */
  public static class AssociatedPullRequests {
    @Key private List<PullRequestEdges> edges;

    public List<PullRequestEdges> getEdges() {
      return edges;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("edges", edges).toString();
    }
  }

  /** Represents 'associatedPullRequests.edges' values */
  public static class PullRequestEdges {
    @Key private AssociatedPullRequestNode node;

    public AssociatedPullRequestNode getNode() {
      return node;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("node", node).toString();
    }
  }

  /** Represents 'associatedPullRequests.edges.node' values */
  public static class AssociatedPullRequestNode {
    @Key private String reviewDecision;

    @Key private Author author;

    @Key private LatestOpinionatedReviews latestOpinionatedReviews;

    @Key private MergedBy mergedBy;

    @Key private String title;

    public String getReviewDecision() {
      return reviewDecision;
    }

    public Author getAuthor() {
      return author;
    }

    public LatestOpinionatedReviews getLatestOpinionatedReviews() {
      return latestOpinionatedReviews;
    }

    public MergedBy getMergedBy() {
      return mergedBy;
    }

    public String getTitle() {
      return title;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("reviewDecision", reviewDecision)
          .add("author", author)
          .add("latestOpinionatedReviews", latestOpinionatedReviews)
          .add("mergedBy", mergedBy)
          .add("title", title)
          .toString();
    }
  }

  /** Represents 'latestOpinionatedReviews' values */
  public static class LatestOpinionatedReviews {
    @Key private List<AuthorEdges> edges;

    public List<AuthorEdges> getEdges() {
      return edges;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("edges", edges).toString();
    }
  }

  /** Represents 'latestOptionatedReviews.edges' values */
  public static class AuthorEdges {
    @Key private AuthorNode node;

    public AuthorNode getNode() {
      return node;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("node", node).toString();
    }
  }

  /** Represents 'latestOpinionatedReviews.edges.node' values */
  public static class AuthorNode {
    @Key private Author author;

    @Key private String state;

    public String getState() {
      return state;
    }

    public Author getAuthor() {
      return author;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("author", author).add("state", state).toString();
    }
  }

  /** Represents 'author' values */
  public static class Author {
    @Key private String login;

    public String getLogin() {
      return login;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("login", login).toString();
    }
  }

  /** Represents 'mergedBy' values */
  public static class MergedBy {
    @Key private String login;

    public String getLogin() {
      return login;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("login", login).toString();
    }
  }
}
