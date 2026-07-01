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

namespace Copybara.Git.GitHub.Api;

/// <summary>Type of events that we can receive from GitHub.</summary>
public enum GitHubEventType
{
    /// <summary>Used if we don't know the event type.</summary>
    UNKNOWN,

    CHECK_RUN,
    CHECK_SUITE,
    COMMIT_COMMENT,
    CREATE,
    DELETE,
    DEPLOYMENT,
    DEPLOYMENT_STATUS,
    FORK,
    ISSUES,
    ISSUE_COMMENT,
    LABEL,
    MEMBER,
    PAGE_BUILD,
    PROJECT,
    PROJECT_CARD,
    PROJECT_COLUMN,
    PULL_REQUEST,
    PULL_REQUEST_REVIEW,
    PULL_REQUEST_REVIEW_COMMENT,
    PUSH,
    RELEASE,
    REPOSITORY_VULNERABILITY_ALERT,
    STATUS,
    CHECK_RUNS,
    WATCH,
}

/// <summary>Helpers for <see cref="GitHubEventType"/>.</summary>
public static class GitHubEventTypes
{
    /// <summary>Events that we can watch in triggers.</summary>
    public static readonly IReadOnlySet<GitHubEventType> WatchableEvents =
        ImmutableHashSet.Create(
            GitHubEventType.ISSUES,
            GitHubEventType.ISSUE_COMMENT,
            GitHubEventType.PULL_REQUEST,
            GitHubEventType.PULL_REQUEST_REVIEW_COMMENT,
            GitHubEventType.PUSH,
            GitHubEventType.STATUS,
            GitHubEventType.CHECK_RUN);
}
