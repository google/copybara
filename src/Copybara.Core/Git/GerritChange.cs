/*
 * Copyright (C) 2019 Google Inc.
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
using Copybara.Exceptions;

namespace Copybara.Git;

/// <summary>
/// A class that represents a Gerrit change. It contains all the necessary objects to do a fetch when
/// <see cref="Fetch"/> is invoked. Port of <c>com.google.copybara.git.GerritChange</c>.
/// </summary>
internal sealed class GerritChange
{
    public const string GerritChangeNumberLabel = "GERRIT_CHANGE_NUMBER";
    public const string GerritChangeIdLabel = "GERRIT_CHANGE_ID";
    public const string GerritCompleteChangeIdLabel = "GERRIT_COMPLETE_CHANGE_ID";
    public const string GerritChangeUrlLabel = "GERRIT_CHANGE_URL";
    public const string GerritChangeBranch = "GERRIT_CHANGE_BRANCH";
    public const string GerritChangeTopic = "GERRIT_CHANGE_TOPIC";
    public const string GerritChangeDescriptionLabel = "GERRIT_CHANGE_DESCRIPTION";
    public const string GerritOwnerEmailLabel = "GERRIT_OWNER_EMAIL";
    public const string GerritOwnerUsernameLabel = "GERRIT_OWNER_USERNAME";
    private const string GerritPatchSetRefPrefix = "PatchSet ";

    // Mirrors GitModule.DEFAULT_INTEGRATE_LABEL (GitModule is ported separately).
    internal const string DefaultIntegrateLabel = "COPYBARA_INTEGRATE_REVIEW";

    private static readonly Regex WholeGerritRef =
        new("^refs/changes/[0-9]{2}/([0-9]+)/([0-9]+)$", RegexOptions.Compiled);

    private static readonly Regex UrlPattern =
        new(@"^https?://.*?/([0-9]+)(?:/([0-9]+))?/?$", RegexOptions.Compiled);

    private readonly GitRepository _repository;
    private readonly GeneralOptions _generalOptions;
    private readonly string _repoUrl;
    private readonly int _change;
    private readonly int _patchSet;
    private readonly string _ref;

    private GerritChange(
        GitRepository repository,
        GeneralOptions generalOptions,
        string repoUrl,
        int change,
        int patchSet,
        string @ref)
    {
        _repository = Preconditions.CheckNotNull(repository);
        _generalOptions = Preconditions.CheckNotNull(generalOptions);
        _repoUrl = repoUrl;
        _change = change;
        _patchSet = patchSet;
        _ref = @ref;
    }

    /// <summary>Get the change number.</summary>
    public int GetChange() => _change;

    /// <summary>Gets the specific PatchSet of the Change.</summary>
    public int GetPatchSet() => _patchSet;

    /// <summary>Context reference for creating GitRevision.</summary>
    public string GetRef() => _ref;

    /// <summary>
    /// Given a local repository, a repo url and a reference, it tries to do its best to resolve the
    /// reference to a Gerrit Change.
    ///
    /// <para>Note that if the PatchSet is not found in the ref, it will go to Gerrit to get the latest
    /// PatchSet number.</para>
    /// </summary>
    /// <returns>a Gerrit change if it can be resolved. Null otherwise.</returns>
    public static GerritChange? Resolve(
        GitRepository repository, string repoUrl, string @ref, GeneralOptions options)
    {
        if (string.IsNullOrEmpty(@ref))
        {
            return null;
        }
        Match refMatcher = WholeGerritRef.Match(@ref);
        if (refMatcher.Success)
        {
            return new GerritChange(
                repository,
                options,
                repoUrl,
                int.Parse(refMatcher.Groups[1].Value),
                int.Parse(refMatcher.Groups[2].Value),
                @ref);
        }
        // A change number like '23423'
        if (@ref.Length > 0 && @ref.All(char.IsDigit))
        {
            return ResolveLatestPatchSet(repository, options, repoUrl, int.Parse(@ref));
        }

        Match urlMatcher = UrlPattern.Match(@ref);
        if (!urlMatcher.Success)
        {
            return null;
        }

        if (!@ref.StartsWith(repoUrl, StringComparison.Ordinal))
        {
            // Assume it is our url. We can make this more strict later
            options.GetConsole().Warn(
                $"Assuming repository '{repoUrl}' for looking for review '{@ref}'");
        }
        int change = int.Parse(urlMatcher.Groups[1].Value);
        int? patchSet =
            urlMatcher.Groups[2].Success && urlMatcher.Groups[2].Value.Length > 0
                ? int.Parse(urlMatcher.Groups[2].Value)
                : null;
        if (patchSet == null)
        {
            return ResolveLatestPatchSet(repository, options, repoUrl, change);
        }
        var patchSets = GetGerritPatchSets(repository, repoUrl, change);
        if (!patchSets.ContainsKey(patchSet.Value))
        {
            throw new CannotResolveRevisionException(
                $"Cannot find patch set {patchSet} for change {change} in {repoUrl}. Available Patch"
                    + $" sets: {string.Join(", ", patchSets.Keys)}");
        }
        return new GerritChange(
            repository, options, repoUrl, change, patchSet.Value,
            patchSets[patchSet.Value].ContextReference()!);
    }

    /// <summary>Fetch the change from Gerrit.</summary>
    /// <param name="additionalLabels">additional labels to add to the GitRevision labels.</param>
    /// <returns>The resolved and fetched SHA-1 of the change.</returns>
    public GitRevision Fetch(ImmutableListMultimap<string, string> additionalLabels)
    {
        string metaRef = $"refs/changes/{_change % 100:D2}/{_change}/meta";
        _repository.Fetch(
            _repoUrl,
            prune: true,
            force: true,
            new[] { _ref + ":refs/gerrit/" + _ref, metaRef + ":refs/gerrit/" + metaRef },
            partialFetch: false,
            depth: null,
            tags: false);
        GitRevision gitRevision = _repository.ResolveReference("refs/gerrit/" + _ref);
        GitRevision metaRevision = _repository.ResolveReference("refs/gerrit/" + metaRef);
        string changeId = GetChangeIdFromMeta(_repository, metaRevision, metaRef);
        string changeNumber = _change.ToString();
        string changeDescription = GetDescriptionFromMeta(_repository, metaRevision, metaRef);

        var labels = ImmutableListMultimap<string, string>.CreateBuilder();
        labels.Put(GerritChangeNumberLabel, changeNumber);
        labels.Put(GerritChangeIdLabel, changeId);
        labels.Put(GerritChangeDescriptionLabel, changeDescription);
        labels.Put(
            DefaultIntegrateLabel,
            new GerritIntegrateLabel(
                _repository, _generalOptions, _repoUrl, _change, _patchSet, changeId).ToString());
        labels.PutAll(additionalLabels);
        foreach (var e in _generalOptions.CliLabels())
        {
            labels.Put(e.Key, e.Value);
        }

        return new GitRevision(
            _repository,
            gitRevision.GetHash(),
            GerritPatchSetAsReviewReference(_patchSet),
            changeNumber,
            labels.Build(),
            _repoUrl);
    }

    private static GerritChange ResolveLatestPatchSet(
        GitRepository repository, GeneralOptions options, string repoUrl, int changeNumber)
    {
        // Last entry is the latest patchset, since it is ordered by patchsetId.
        var patchSets = GetGerritPatchSets(repository, repoUrl, changeNumber);
        var lastPatchset = patchSets[patchSets.Keys.Last()];
        int lastKey = patchSets.Keys.Last();
        return new GerritChange(
            repository, options, repoUrl, changeNumber, lastKey, lastPatchset.ContextReference()!);
    }

    /// <summary>
    /// Use NoteDB for extracting the Change-id. It should be the first commit in the log of the meta
    /// reference.
    /// </summary>
    private static string GetChangeIdFromMeta(
        GitRepository repo, GitRevision metaRevision, string metaRef)
    {
        var changes = GetChanges(repo, metaRevision, metaRef);
        string? changeId = null;
        foreach (var change in changes[^1].GetLabels())
        {
            if (change.IsLabel() && change.GetName().Equals("Change-id")
                && change.GetSeparator().Equals(": "))
            {
                changeId = change.GetValue();
            }
        }
        if (changeId == null)
        {
            throw new RepoException(
                $"Cannot find Change-id in {metaRef}. Not present in: \n{changes[^1].GetText()}");
        }

        return changeId;
    }

    private static string GetDescriptionFromMeta(
        GitRepository repo, GitRevision metaRevision, string metaRef) =>
        GetChanges(repo, metaRevision, metaRef)[0].GetText();

    /// <summary>
    /// Returns the list of <see cref="ChangeMessage"/>s. Guarantees that there is at least one change.
    /// </summary>
    private static IReadOnlyList<ChangeMessage> GetChanges(
        GitRepository repo, GitRevision metaRevision, string metaRef)
    {
        var changes = repo.Log(metaRevision.GetHash()).Run()
            .Select(e => ChangeMessage.ParseMessage(e.Body ?? ""))
            .ToList();

        if (changes.Count == 0)
        {
            throw new RepoException("Cannot find any PatchSet in " + metaRef);
        }
        return changes;
    }

    /// <summary>
    /// Get all the patchsets for a change ordered by the patchset number. Last is the most recent one.
    /// </summary>
    public static SortedDictionary<int, GitRevision> GetGerritPatchSets(
        GitRepository repository, string url, int changeNumber)
    {
        var patchSets = new SortedDictionary<int, GitRevision>();
        string basePath = $"refs/changes/{changeNumber % 100:D2}/{changeNumber}";
        var refsToSha1 = repository.LsRemote(url, new[] { basePath + "/*" });
        if (refsToSha1.Count == 0)
        {
            throw new CannotResolveRevisionException(
                $"Cannot find change number {changeNumber} in '{url}'");
        }
        foreach (var e in refsToSha1)
        {
            if (e.Key.EndsWith("/meta", StringComparison.Ordinal)
                || e.Key.EndsWith("/robot-comments", StringComparison.Ordinal))
            {
                continue;
            }
            Preconditions.CheckState(
                e.Key.StartsWith(basePath + "/", StringComparison.Ordinal),
                "Unexpected response reference {0} for {1}",
                e.Key,
                basePath);
            Match matcher = WholeGerritRef.Match(e.Key);
            Preconditions.CheckArgument(
                matcher.Success,
                "Unexpected format for response reference {0} for {1}",
                e.Key,
                basePath);
            int patchSet = int.Parse(matcher.Groups[2].Value);
            patchSets[patchSet] =
                new GitRevision(
                    repository,
                    e.Value,
                    GerritPatchSetAsReviewReference(patchSet),
                    e.Key,
                    ImmutableListMultimap<string, string>.Empty,
                    url);
        }
        return patchSets;
    }

    internal static string GerritPatchSetAsReviewReference(int patchSet) =>
        GerritPatchSetRefPrefix + patchSet;
}
