/*
 * Copyright (C) 2018 Google Inc.
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
using Copybara.Common;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara;

/// <summary>Starter of feedback migration executions.</summary>
[StarlarkBuiltin("trigger", Doc = "Starter of feedback migration executions.", Documented = false)]
public interface ITrigger : IStarlarkPrintableValue
{
    IEndpoint GetEndpoint();

    void IStarlarkPrintableValue.Repr(Printer printer, StarlarkSemantics semantics) =>
        printer.Append(ToString() ?? "");

    ImmutableListMultimap<string, string> Describe();

    /// <summary>Returns a key-value list describing the credentials the endpoint was instantiated with.</summary>
    IReadOnlyList<ImmutableListMultimap<string, string>> DescribeCredentials() =>
        ImmutableArray<ImmutableListMultimap<string, string>>.Empty;
}
