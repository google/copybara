/*
 * Copyright (C) 2022 Google Inc.
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
using Console = Copybara.Util.Console.Console;

namespace Copybara.Version;

/// <summary>
/// A selector of selectors that traverses all the selectors in order and returns the first result
/// that is found.
/// </summary>
public class OrderedVersionSelector : IVersionSelector
{
    private readonly ImmutableArray<IVersionSelector> _selectors;

    public OrderedVersionSelector(ImmutableArray<IVersionSelector> selectors)
    {
        _selectors = selectors;
    }

    public string? Select(IVersionList versionList, string? requestedRef, Console console)
    {
        foreach (IVersionSelector selector in _selectors)
        {
            string? selection = selector.Select(versionList, requestedRef, console);
            if (selection != null)
            {
                return selection;
            }
        }
        return null;
    }

    /// <summary>
    /// Returns the union of all inner <c>searchPattern</c>s.
    ///
    /// <para>Any searchPattern that is "none" is ignored (So composition of selectors that use the
    /// requestedRef can be mixed with version selectors that use <see cref="IVersionList"/>).</para>
    ///
    /// <para>If any searchPattern is "all", it returns "all" (Version selector is interested in all
    /// the versions).</para>
    /// </summary>
    public IReadOnlySet<SearchPattern> SearchPatterns()
    {
        var result = _selectors
            .SelectMany(s => s.SearchPatterns())
            .Where(p => !p.IsNone())
            .ToImmutableHashSet();

        if (result.Any(p => p.IsAll()))
        {
            return SearchPattern.ALL;
        }
        return result;
    }

    public override string ToString() => $"[{string.Join(", ", _selectors)}]";
}
