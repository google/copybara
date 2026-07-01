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

using System.Text.RegularExpressions;
using Copybara.Common;

namespace Copybara.Git;

/// <summary>
/// Integrate label for GitHub PR. Port of <c>com.google.copybara.git.GitHubPrIntegrateLabel</c>.
///
/// <para>Format like: "https://github.com/google/copybara/pull/12345 from mikelalcon:master SHA-1".
/// Where SHA is optional: If present it means to integrate the specific SHA. Otherwise the head of
/// the PR is used.</para>
/// </summary>
public sealed class GitHubPrIntegrateLabel : IIntegrateLabel
{
    private static readonly Regex LabelPattern =
        new(
            "https://github.com/([.a-zA-Z0-9_/-]+)/pull/([0-9]+)"
                + " from ([^\\s\\r\\n]*)(?: ([0-9a-f]{7,64}))?",
            RegexOptions.Compiled);

    private readonly GitRepository _repository;
    private readonly GeneralOptions _generalOptions;
    private readonly string _projectId;
    private readonly long _prNumber;
    private readonly string _originBranch;
    private readonly string? _sha1;

    public GitHubPrIntegrateLabel(
        GitRepository repository,
        GeneralOptions generalOptions,
        string projectId,
        long prNumber,
        string originBranch,
        string? sha1)
    {
        _repository = Preconditions.CheckNotNull(repository);
        _generalOptions = Preconditions.CheckNotNull(generalOptions);
        _projectId = Preconditions.CheckNotNull(projectId);
        _prNumber = prNumber;
        _originBranch = Preconditions.CheckNotNull(originBranch);
        _sha1 = sha1;
    }

    public static GitHubPrIntegrateLabel? Parse(
        string str, GitRepository repository, GeneralOptions generalOptions)
    {
        Match matcher = LabelPattern.Match(str.Trim());
        return matcher.Success && matcher.Value == str.Trim()
            ? new GitHubPrIntegrateLabel(
                repository,
                generalOptions,
                matcher.Groups[1].Value,
                long.Parse(matcher.Groups[2].Value),
                matcher.Groups[3].Value,
                matcher.Groups[4].Success ? matcher.Groups[4].Value : null)
            : null;
    }

    public override string ToString() =>
        $"https://github.com/{_projectId}/pull/{_prNumber} from {_originBranch}"
            + (_sha1 != null ? " " + _sha1 : "");

    public string MergeMessage(IReadOnlyList<LabelFinder> labelsToAdd) =>
        IIntegrateLabel.WithLabels(
            $"Merge pull request #{_prNumber} from {_originBranch}", labelsToAdd);

    public GitRevision GetRevision()
    {
        string pr = "https://github.com/" + _projectId + "/pull/" + _prNumber;
        string repoUrl = "https://github.com/" + _projectId;
        GitRevision gitRevision =
            GitRepoType.GitHub.ResolveRef(
                _repository,
                repoUrl,
                pr,
                _generalOptions,
                describeVersion: false,
                partialFetch: false,
                fetchDepth: null);
        if (_sha1 == null)
        {
            return gitRevision;
        }
        if (_sha1 == gitRevision.GetHash())
        {
            return gitRevision;
        }
        _generalOptions
            .GetConsole()
            .WarnFmt(
                "Pull Request {0} has more changes after {1} (PR HEAD is {2})."
                    + " Not all changes might be migrated",
                pr,
                _sha1,
                gitRevision.GetHash());
        return _repository.ResolveReferenceWithContext(
            _sha1, gitRevision.ContextReference(), repoUrl);
    }

    public string GetProjectId() => _projectId;

    public long GetPrNumber() => _prNumber;

    public string GetOriginBranch() => _originBranch;
}
