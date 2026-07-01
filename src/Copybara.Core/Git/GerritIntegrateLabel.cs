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

using System.Collections.Immutable;
using System.Text.RegularExpressions;
using Copybara.Common;

namespace Copybara.Git;

/// <summary>
/// Integrate label for Gerrit changes. Port of
/// <c>com.google.copybara.git.GerritIntegrateLabel</c>.
///
/// <para>Returns a string like:</para>
/// <list type="bullet">
///   <item>"Gerrit https://example.com/project 1271"</item>
///   <item>"Gerrit https://example.com/project 1271 5"</item>
///   <item>"Gerrit https://example.com/project 1271 ChangeId"</item>
///   <item>"Gerrit https://example.com/project 1271 5 ChangeId"</item>
/// </list>
/// Where both the PatchSet and ChangeId are optional.
/// </summary>
internal sealed class GerritIntegrateLabel : IIntegrateLabel
{
    private static readonly Regex LabelPattern =
        new("^gerrit ([^ ]+) ([0-9]+)(?: Patch Set ([0-9]+))?(?: (I[a-f0-9]+))?$",
            RegexOptions.Compiled);

    private readonly GitRepository _repository;
    private readonly GeneralOptions _generalOptions;
    private readonly string _url;
    private readonly int _changeNumber;
    private int? _patchSet;
    private readonly string? _changeId;

    internal GerritIntegrateLabel(
        GitRepository repository,
        GeneralOptions generalOptions,
        string url,
        int changeNumber,
        int? patchSet,
        string? changeId)
    {
        _repository = Preconditions.CheckNotNull(repository);
        _generalOptions = Preconditions.CheckNotNull(generalOptions);
        _url = Preconditions.CheckNotNull(url);
        _changeNumber = changeNumber;
        _patchSet = patchSet;
        _changeId = changeId;
    }

    internal static GerritIntegrateLabel? Parse(
        string str, GitRepository repository, GeneralOptions generalOptions)
    {
        Match matcher = LabelPattern.Match(str.Trim());
        return matcher.Success
            ? new GerritIntegrateLabel(
                repository,
                generalOptions,
                matcher.Groups[1].Value,
                int.Parse(matcher.Groups[2].Value),
                matcher.Groups[3].Success && matcher.Groups[3].Value.Length > 0
                    ? int.Parse(matcher.Groups[3].Value)
                    : null,
                matcher.Groups[4].Success && matcher.Groups[4].Value.Length > 0
                    ? matcher.Groups[4].Value
                    : null)
            : null;
    }

    public override string ToString() =>
        string.Format(
            "gerrit {0} {1}{2}{3}",
            _url,
            _changeNumber,
            _patchSet != null ? " Patch Set " + _patchSet : "",
            _changeId != null ? " " + _changeId : "");

    public string MergeMessage(IReadOnlyList<LabelFinder> labelsToAdd)
    {
        if (_changeId != null)
        {
            var updated = new List<LabelFinder>(labelsToAdd)
            {
                new("Change-Id: " + _changeId),
            };
            labelsToAdd = updated;
        }
        return IIntegrateLabel.WithLabels(
            "Merge Gerrit change " + _changeNumber
                + (_patchSet == null ? "" : " Patch Set " + _patchSet),
            labelsToAdd);
    }

    public GitRevision GetRevision()
    {
        var patchSets = GerritChange.GetGerritPatchSets(_repository, _url, _changeNumber);
        int latestPatchSet = patchSets.Keys.Last();

        if (_patchSet == null)
        {
            _patchSet = latestPatchSet;
        }
        else if (latestPatchSet > _patchSet)
        {
            _generalOptions.GetConsole().WarnFmt(
                "Change {0} has more patch sets after Patch Set {1}. Latest is Patch Set {2}."
                    + " Not all changes might be migrated",
                _changeNumber, _patchSet, latestPatchSet);
        }

        return GitRepoType.Gerrit.ResolveRef(
            _repository,
            _url,
            $"refs/changes/{_changeNumber % 100:D2}/{_changeNumber}" + "/" + _patchSet,
            _generalOptions,
            describeVersion: false,
            partialFetch: false,
            fetchDepth: null);
    }
}
