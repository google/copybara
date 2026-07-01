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
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Revision;

/// <summary>Information about the changes being imported.</summary>
/// <remarks>
/// Java holds <c>Sequence&lt;? extends Change&lt;?&gt;&gt;</c>. As <see cref="Change{R}"/> is generic
/// over an unbounded revision type, the heterogeneous collection is modelled here as a list of
/// <c>object</c> (each element being some <see cref="Change{R}"/>), mirroring the Java wildcard.
/// </remarks>
[StarlarkBuiltin(
    "Changes",
    Doc =
        "Data about the set of changes that are being migrated. "
        + "Each change includes information like: original author, change message, "
        + "labels, etc. You receive this as a field in TransformWork object for user defined "
        + "transformations")]
public sealed class Changes : IStarlarkValue
{
    public static readonly Changes Empty =
        new(ImmutableArray<object>.Empty, ImmutableArray<object>.Empty);

    private readonly IReadOnlyList<object> _current;
    private readonly IReadOnlyList<object> _migrated;

    public Changes(IEnumerable<object> current, IEnumerable<object> migrated)
    {
        _current = current.ToImmutableArray();
        _migrated = migrated.ToImmutableArray();
    }

    [StarlarkMethod("current", Doc = "List of changes that will be migrated", StructField = true)]
    public IReadOnlyList<object> GetCurrent() => _current;

    [StarlarkMethod(
        "migrated",
        Doc =
            "List of changes that where migrated in previous Copybara executions or if using"
            + " ITERATIVE mode in previous iterations of this workflow.",
        StructField = true)]
    public IReadOnlyList<object> GetMigrated() => _migrated;
}
