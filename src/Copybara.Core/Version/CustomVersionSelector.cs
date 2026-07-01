/*
 * Copyright (C) 2025 Google LLC
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
using Starlark.Eval;
using Console = Copybara.Util.Console.Console;
using StarlarkRt = Starlark.Eval.Starlark;

namespace Copybara.Version;

/// <summary>A version selector that uses a custom comparator to select the version.</summary>
public sealed class CustomVersionSelector : IVersionSelector
{
    private readonly IStarlarkCallable _comparator;
    private readonly string? _filterByRegex;

    /// <summary>Creates a new CustomVersionSelector.</summary>
    /// <param name="comparator">the custom comparator to use.</param>
    /// <param name="filterByRegex">the regex to filter the potential version candidates by.</param>
    /// <exception cref="ArgumentException">
    /// if the comparator is not a StarlarkFunction or if the comparator does not take two string
    /// arguments named 'left' and 'right'.
    /// </exception>
    public CustomVersionSelector(IStarlarkCallable comparator, string? filterByRegex)
    {
        _comparator = EnforceStarlarkCallable(comparator);
        _filterByRegex = filterByRegex;
    }

    /// <summary>
    /// Selects the latest version from the version list that matches the filter regex and the custom
    /// comparator.
    /// </summary>
    /// <param name="versionList">the list of versions to select from.</param>
    /// <param name="requestedRef">the reference of the requested version.</param>
    /// <param name="console">the console to use for logging.</param>
    /// <returns>
    /// the latest version that matches the filter regex and the custom comparator, or <c>null</c>.
    /// </returns>
    /// <exception cref="InvalidOperationException">
    /// if the comparator returns a comparison result outside of [-1, 1] or if the comparator throws
    /// an exception during execution.
    /// </exception>
    public string? Select(IVersionList versionList, string? requestedRef, Console console)
    {
        ImmutableArray<string> filteredVersions = FilterByRegexMatch(versionList.List());

        if (versionList.List().Count == 0)
        {
            return null;
        }

        var comparer = Comparer<string>.Create((left, right) =>
        {
            int result = CallCustomComparator(left, right, console);
            if (result < -1 || result > 1)
            {
                throw new InvalidOperationException(
                    string.Format(
                        "Attempted to call comparator '{0}' left={1}, right={2} and got a comparison"
                            + " result of {3}",
                        _comparator.Name, left, right, result));
            }
            return result;
        });

        // OrderBy performs a stable sort, mirroring Guava's ImmutableList.sortedCopyOf.
        var sortedVersions = filteredVersions.OrderBy(v => v, comparer).ToList();

        return sortedVersions.Count == 0 ? null : sortedVersions[^1];
    }

    private ImmutableArray<string> FilterByRegexMatch(IReadOnlySet<string> versionList)
    {
        if (string.IsNullOrEmpty(_filterByRegex))
        {
            return versionList.ToImmutableArray();
        }
        var pattern = new Regex(_filterByRegex);
        return versionList
            .Where(s => IsFullMatch(pattern, s))
            .ToImmutableArray();
    }

    private static bool IsFullMatch(Regex pattern, string s)
    {
        // Mirror java.util.regex Matcher.matches(): the entire input must match.
        Match m = pattern.Match(s);
        return m.Success && m.Index == 0 && m.Length == s.Length;
    }

    private int CallCustomComparator(string left, string right, Console console)
    {
        try
        {
            using Mutability mutability = Mutability.Create("custom_version_selector_comparator");
            object? result = StarlarkRt.Call(
                StarlarkThread.CreateTransient(mutability, StarlarkSemantics.DEFAULT),
                _comparator,
                /* args= */ ImmutableArray<object?>.Empty,
                new Dictionary<string, object?> { ["left"] = left, ["right"] = right });
            return ((StarlarkInt)result!).ToInt("user comparator");
        }
        catch (EvalException e)
        {
            console.ErrorFmt(
                "Failed to excecute custom comparator. The exception was {0}", e.Message);
            // this is going to propagate an upstream exception since it is not in [-1, 1]
            return -2;
        }
    }

    private static IStarlarkCallable EnforceStarlarkCallable(IStarlarkCallable comparator)
    {
        Preconditions.CheckArgument(
            comparator is StarlarkFunction,
            $"Comparator must be a StarlarkFunction but was {comparator.GetType().FullName}");
        IReadOnlyList<string> parameterNames = ((StarlarkFunction)comparator).GetParameterNames();
        Preconditions.CheckArgument(
            parameterNames.Count == 2
                && parameterNames.OrderBy(n => n, StringComparer.Ordinal)
                    .SequenceEqual(new[] { "left", "right" }),
            "The comparator must take two strings arguments named 'left' and 'right'");

        return comparator;
    }
}
