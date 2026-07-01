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

namespace Copybara.Git;

/// <summary>
/// Options related to GitHub destination. Port of
/// <c>com.google.copybara.git.GitHubDestinationOptions</c>.
/// </summary>
public sealed class GitHubDestinationOptions : IOption
{
    internal const string GitHubDestinationPrBranch = "--github-destination-pr-branch";

    /// <summary>
    /// If set, uses this branch for creating the pull request instead of using a generated one.
    /// </summary>
    [Flag(GitHubDestinationPrBranch,
        "If set, uses this branch for creating the pull request instead of using a generated one")]
    public string? DestinationPrBranch { get; set; }

    /// <summary>If the pull request should be created.</summary>
    [Flag("--github-destination-pr-create",
        "If the pull request should be created", Arity = 1)]
    public bool CreatePullRequest { get; set; } = true;
}
