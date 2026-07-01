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
using Starlark.Eval;

namespace Copybara.Version;

/// <summary>List all or a subset of the versions of a repository.</summary>
public interface IVersionList : IStarlarkPrintableValue, IConfigItemDescription
{
    /// <summary>List the versions.</summary>
    /// <exception cref="Copybara.Exceptions.ValidationException"/>
    /// <exception cref="Copybara.Exceptions.RepoException"/>
    IReadOnlySet<string> List();

    void IStarlarkPrintableValue.Repr(Printer printer, StarlarkSemantics semantics) =>
        printer.Append(ToString());
}

/// <summary>A version list that comes from a set of Strings.</summary>
public sealed class SetVersionList : IVersionList
{
    private readonly ImmutableHashSet<string> _versions;

    public SetVersionList(IReadOnlySet<string> versions)
    {
        _versions = versions.ToImmutableHashSet();
    }

    public IReadOnlySet<string> List() => _versions;
}
