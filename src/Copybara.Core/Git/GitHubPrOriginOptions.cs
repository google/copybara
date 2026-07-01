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

namespace Copybara.Git;

/// <summary>
/// Options related to GitHub PR origin. Port of
/// <c>com.google.copybara.git.GitHubPrOriginOptions</c>.
/// </summary>
public class GitHubPrOriginOptions : IOption
{
    // TODO(port): Reference GitModule.GITHUB_PR_ORIGIN_NAME once GitModule is ported.
    private const string GitHubPrOriginName = "github_pr_origin";

    [Flag("--github-required-label",
        "Required labels in the Pull Request to be imported by " + GitHubPrOriginName)]
    public List<string> RequiredLabels { get; set; } = new();

    [Flag("--github-required-status-context-name",
        "Required status context names in the Pull Request to be imported by " + GitHubPrOriginName)]
    public List<string> RequiredStatusContextNames { get; set; } = new();

    [Flag("--github-required-check-run",
        "Required check runs in the Pull Request to be imported by " + GitHubPrOriginName)]
    public List<string> RequiredCheckRuns { get; set; } = new();

    [Flag("--github-retryable-label",
        "Required labels in the Pull Request that should be retryed to be imported by "
            + GitHubPrOriginName)]
    public List<string> RetryableLabels { get; set; } = new();

    [Flag("--github-skip-required-labels",
        "Skip checking labels for importing Pull Requests. Note that this is dangerous as it might"
            + " import an unsafe PR.")]
    public bool SkipRequiredLabels { get; set; }

    [Flag("--github-skip-required-status-context-names",
        "Skip checking status context names for importing Pull Requests. Note that this is dangerous"
            + " as it might import an unsafe PR.")]
    public bool SkipRequiredStatusContextNames { get; set; }

    [Flag("--github-skip-required-check-runs",
        "Skip checking check runs for importing Pull Requests. Note that this is dangerous as it"
            + " might import an unsafe PR.")]
    public bool SkipRequiredCheckRuns { get; set; }

    [Flag("--github-force-import", "Force import regardless of the state of the PR")]
    public bool ForceImport { get; set; }

    [Flag("--github-pr-merge", "Override merge bit from config", Arity = 1)]
    public bool? OverrideMerge { get; set; }

    [Flag("--github-use-repo", "Use a different git repository instead", Arity = 1)]
    public string? Repo { get; set; }

    /// <summary>
    /// Compute the labels that should be required by git.github_pr_origin for importing a Pull
    /// Request.
    /// </summary>
    public virtual IReadOnlySet<string> GetRequiredLabels(IEnumerable<string> configLabels)
    {
        if (SkipRequiredLabels)
        {
            return ImmutableHashSet<string>.Empty;
        }
        return RequiredLabels.Count == 0
            ? configLabels.ToImmutableHashSet()
            : RequiredLabels.ToImmutableHashSet();
    }

    /// <summary>
    /// Compute the status context names that should be required by git.github_pr_origin for importing
    /// a Pull Request.
    /// </summary>
    public virtual IReadOnlySet<string> GetRequiredStatusContextNames(
        IEnumerable<string> configStatusContextNames)
    {
        if (SkipRequiredStatusContextNames)
        {
            return ImmutableHashSet<string>.Empty;
        }
        return RequiredStatusContextNames.Count == 0
            ? configStatusContextNames.ToImmutableHashSet()
            : RequiredStatusContextNames.ToImmutableHashSet();
    }

    /// <summary>
    /// Compute the check runs that should be required by git.github_pr_origin for importing a Pull
    /// Request.
    /// </summary>
    public virtual IReadOnlySet<string> GetRequiredCheckRuns(IEnumerable<string> configCheckRuns)
    {
        if (SkipRequiredCheckRuns)
        {
            return ImmutableHashSet<string>.Empty;
        }
        return RequiredCheckRuns.Count == 0
            ? configCheckRuns.ToImmutableHashSet()
            : RequiredCheckRuns.ToImmutableHashSet();
    }

    /// <summary>
    /// Compute the labels that should be retried by git.github_pr_origin for importing a Pull Request.
    /// </summary>
    public virtual IReadOnlySet<string> GetRetryableLabels(IEnumerable<string> configLabels)
    {
        if (SkipRequiredLabels)
        {
            return ImmutableHashSet<string>.Empty;
        }
        return RetryableLabels.Count == 0
            ? configLabels.ToImmutableHashSet()
            : RetryableLabels.ToImmutableHashSet();
    }
}
