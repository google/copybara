/*
 * Copyright (C) 2017 Google Inc.
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

using System.Text.RegularExpressions;
using Copybara.Exceptions;

namespace Copybara.Git.GitHub.Util;

/// <summary>
/// General utilities for manipulating GitHub urls and data. Port of
/// <c>com.google.copybara.git.github.util.GitHubUtil</c>.
/// </summary>
public static class GitHubUtil
{
    /// <summary>The variable name of the list of the required status context names.</summary>
    public const string RequiredStatusContextNames = "required_status_context_names";

    /// <summary>The variable name of the list of the required check runs.</summary>
    public const string RequiredCheckRuns = "required_check_runs";

    /// <summary>The variable name of the list of the required labels.</summary>
    public const string RequiredLabels = "required_labels";

    /// <summary>The variable name of the list of the retryable labels.</summary>
    public const string RetryableLabels = "retryable_labels";

    private static readonly Regex InvalidBranchChars = new("[^A-Za-z0-9/_-]", RegexOptions.Compiled);

    private static readonly Regex GitHubPullRequestRef =
        new("^refs/pull/([0-9]+)/(head|merge)$", RegexOptions.Compiled);

    /// <summary>
    /// Returns a valid branch name by replacing invalid characters with "_".
    /// </summary>
    /// <exception cref="ValidationException">
    /// when branchName starts with "/" or "refs/".
    /// </exception>
    public static string GetValidBranchName(string branchName)
    {
        ValidationException.CheckCondition(
            !branchName.StartsWith('/') && !branchName.StartsWith("refs/", StringComparison.Ordinal),
            "Branch name has invalid prefix: \"/\" or \"refs/\"");
        return InvalidBranchChars.Replace(branchName, "_");
    }

    /// <summary>
    /// Given a ref like 'refs/pull/12345/head' returns 12345 or null if not a GitHub PR head ref.
    /// </summary>
    public static int? MaybeParseGithubPrFromHeadRef(string @ref)
    {
        Match matcher = GitHubPullRequestRef.Match(@ref);
        return matcher.Success && matcher.Groups[2].Value == "head"
            ? int.Parse(matcher.Groups[1].Value)
            : null;
    }

    /// <summary>
    /// Given a ref like 'refs/pull/12345/merge' returns 12345 or null if not a GitHub PR ref.
    /// </summary>
    public static int? MaybeParseGithubPrFromMergeOrHeadRef(string @ref)
    {
        Match matcher = GitHubPullRequestRef.Match(@ref);
        return matcher.Success ? int.Parse(matcher.Groups[1].Value) : null;
    }

    /// <summary>Given a prNumber return a git reference like 'refs/pull/12345/head'.</summary>
    public static string AsHeadRef(int prNumber) => $"refs/pull/{prNumber}/head";

    /// <summary>Given a prNumber return a git reference like 'refs/pull/12345/merge'.</summary>
    public static string AsMergeRef(int prNumber) => $"refs/pull/{prNumber}/merge";
}
