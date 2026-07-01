/*
 * Copyright (C) 2019 Google LLC
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
using System.Linq;
using System.Text.RegularExpressions;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.TemplateToken;
using Starlark.Eval;
using Starlark.Syntax;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Version;

/// <summary>
/// Given a <see cref="IVersionList"/> and a regex template, finds the latest version that matches the
/// regex.
/// </summary>
public class LatestVersionSelector : IVersionSelector
{
    private readonly string _format;
    private readonly SortedDictionary<int, VersionElementType> _groupTypes;
    private readonly RegexTemplateTokens _template;

    /// <exception cref="EvalException"/>
    public LatestVersionSelector(
        string format,
        IReadOnlyDictionary<string, Regex> groups,
        SortedDictionary<int, VersionElementType> groupTypes,
        Location location)
    {
        _format = format;
        _groupTypes = Preconditions.CheckNotNull(groupTypes);
        _template = new RegexTemplateTokens(
            Preconditions.CheckNotNull(format), groups, true, location);
    }

    /// <summary>Enum type for different version segments.</summary>
    public enum VersionElementType
    {
        NUMERIC,
        ALPHABETIC,
    }

    public IReadOnlySet<SearchPattern> SearchPatterns() =>
        ImmutableHashSet.Create(new SearchPattern(_template.GetTokens()));

    public string? Select(IVersionList versionList, string? requestedRef, Console console)
    {
        IReadOnlySet<string> refs = versionList.List();

        ImmutableListMultimap<string, int> groupIndexes = _template.GetGroupIndexes();
        List<IComparable> latest = new();
        string? latestRef = null;
        foreach (string @ref in refs)
        {
            Match matcher = _template.GetBefore().Match(@ref);
            if (!IsFullMatch(matcher, @ref))
            {
                console.VerboseFmt(
                    "Ref '{0}' didn't match version_selector didn't match any version for '{1}'. This"
                        + " ref will be ignored, consider correcting the version_selector regular"
                        + " expression if this is not intended.",
                    @ref, _template.GetBefore().ToString());
                continue;
            }
            List<IComparable> objs = new();
            foreach (var groups in _groupTypes)
            {
                string var = VarName(groups.Value, groups.Key);
                ImmutableArray<int> indexes = groupIndexes.Get(var);
                string val = matcher.Groups[indexes[^1]].Value;
                objs.Add(Convert(groups.Value, val, groups.Key));
            }
            if (IsAfter(latest, objs))
            {
                latest = objs;
                latestRef = @ref;
            }
        }
        if (latestRef == null)
        {
            console.WarnFmt(
                "version_selector didn't match any version for '{0}'",
                _template.GetBefore().ToString());
        }

        return latestRef;
    }

    private static bool IsFullMatch(Match match, string input) =>
        match.Success && match.Index == 0 && match.Length == input.Length;

    private static bool IsAfter(List<IComparable> old, List<IComparable> newer)
    {
        if (old.Count == 0)
        {
            return true;
        }
        Preconditions.CheckArgument(old.Count == newer.Count);
        for (int i = 0; i < old.Count; i++)
        {
            int comp = CompareElement(old[i], newer[i]);
            if (comp != 0)
            {
                return comp < 0;
            }
        }
        return false; // Everything equal
    }

    private static int CompareElement(IComparable o, IComparable n) => o.CompareTo(n);

    public IReadOnlyList<string> GetUnmatchedGroups()
    {
        var usedGroups = _template.GetGroupIndexes().Keys.ToHashSet();
        return _groupTypes
            .Select(e => VarName(e.Value, e.Key))
            .Where(s => !usedGroups.Contains(s))
            .ToImmutableArray();
    }

    private static string VarName(VersionElementType type, int idx) =>
        (type == VersionElementType.NUMERIC ? "n" : "s") + idx;

    /// <exception cref="ValidationException"/>
    private static IComparable Convert(VersionElementType type, string val, int idx)
    {
        if (type == VersionElementType.ALPHABETIC)
        {
            return val;
        }

        // NUMERIC. Handles case for ".[0-9]+"
        if (val.StartsWith('.'))
        {
            val = val.Substring(1);
        }
        if (val.Length == 0)
        {
            return int.MinValue;
        }
        if (int.TryParse(val, out int parsed))
        {
            return parsed;
        }
        throw new ValidationException(
            string.Format(
                "Invalid value for numeric group {0}: '{1}'. Use s{2} instead of n{2} as group name"
                    + " or extract the prefix part to the format string.",
                VarName(type, idx), val, idx));
    }

    public override string ToString() =>
        string.Format("core.latest_version(format = '{0}')", _format);
}
