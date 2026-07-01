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
using System.Globalization;
using System.Text;
using Copybara.Common;
using Starlark.Eval;

namespace Copybara.Git.GerritApi;

/// <summary>
/// An object that represents the input parameters for a changes query:
///
/// <para>https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#list-changes</para>
/// </summary>
[Starlark.Annot.StarlarkBuiltin(
    "gerritapi.ChangesQuery",
    Doc =
        "Input for listing Gerrit changes. See "
        + "https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#list-changes")]
public class ChangesQuery : IStarlarkPrintableValue
{
    private readonly string _query;
    private readonly IReadOnlySet<IncludeResult> _include;
    private readonly int? _limit;
    private readonly int? _start;

    public ChangesQuery(string query)
    {
        _query = query;
        _include = ImmutableHashSet<IncludeResult>.Empty;
        _limit = null;
        _start = null;
    }

    private ChangesQuery(string query, IReadOnlySet<IncludeResult> include, int? limit, int? start)
    {
        _query = Preconditions.CheckNotNull(query);
        _include = Preconditions.CheckNotNull(include);
        _limit = limit;
        _start = start;
    }

    public ChangesQuery WithStart(int start) => new(_query, _include, _limit, start);

    public ChangesQuery WithLimit(int limit) => new(_query, _include, limit, _start);

    public ChangesQuery WithInclude(IEnumerable<IncludeResult> include) =>
        new(_query, include.ToImmutableHashSet(), _limit, _start);

    public string AsUrlParams()
    {
        var sb = new StringBuilder("q=").Append(Escape(_query));
        foreach (var includeResult in _include)
        {
            sb.Append("&o=").Append(includeResult);
        }

        if (_limit != null)
        {
            sb.Append("&n=").Append(_limit.Value.ToString(CultureInfo.InvariantCulture));
        }

        if (_start != null)
        {
            sb.Append("&S=").Append(_start.Value.ToString(CultureInfo.InvariantCulture));
        }

        return sb.ToString();
    }

    private static string Escape(string query) => Uri.EscapeDataString(query);

    public void Repr(Printer printer, StarlarkSemantics semantics) => printer.Append(ToString());

    public override string ToString() =>
        $"ChangesQuery{{query={_query}, include=[{string.Join(", ", _include)}], "
        + $"limit={_limit?.ToString(CultureInfo.InvariantCulture) ?? "null"}, "
        + $"start={_start?.ToString(CultureInfo.InvariantCulture) ?? "null"}}}";
}
