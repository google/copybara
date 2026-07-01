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

using Copybara.Common;
using Copybara.Effect;
using Copybara.Revision;
using Starlark.Annot;
using Starlark.Eval;
using StarlarkRt = Starlark.Eval.Starlark;
using Console = Copybara.Util.Console.Console;

namespace Copybara;

/// <summary>
/// An origin or destination API in a feedback migration.
///
/// <para>Endpoints are symmetric, that is, they need to be able to act both as an origin and
/// destination of a feedback migration, which means that they need to support both read and write
/// operations on the API.</para>
/// </summary>
[StarlarkBuiltin("endpoint", Doc = "An origin or destination API in a feedback migration.")]
public interface IEndpoint : IStarlarkPrintableValue, IConfigItemDescription
{
    /// <summary>
    /// To be used for core.workflow origin/destinations that don't want to provide an api for giving
    /// feedback.
    /// </summary>
    public static readonly IEndpoint NoopEndpoint = new NoopEndpointImpl();

    void IStarlarkPrintableValue.Repr(Printer printer, StarlarkSemantics semantics) =>
        printer.Append(ToString() ?? "");

    /// <summary>Returns a key-value list of the options the endpoint was instantiated with.</summary>
    ImmutableListMultimap<string, string> Describe();

    [StarlarkMethod(
        "new_origin_ref",
        Doc = "Creates a new origin reference out of this endpoint.")]
    OriginRef NewOriginRef([Param(Name = "ref", Named = true, Doc = "The reference.")] string @ref) =>
        new(@ref);

    [StarlarkMethod(
        "new_destination_ref",
        Doc = "Creates a new destination reference out of this endpoint.")]
    DestinationEffect.DestinationRef NewDestinationRef(
        [Param(Name = "ref", Named = true, Doc = "The reference.")] string @ref,
        [Param(Name = "type", Named = true, Doc = "The type of this reference.")] string type,
        [Param(
            Name = "url",
            Named = true,
            Doc = "The url associated with this reference, if any.",
            DefaultValue = "None")]
        object? urlObj)
    {
        string? url = StarlarkRt.IsNullOrNone(urlObj) ? null : (string?)urlObj;
        return new DestinationEffect.DestinationRef(@ref, type, url);
    }

    [StarlarkMethod(
        "url",
        Doc = "Return the URL of this endpoint.",
        StructField = true,
        AllowReturnNones = true)]
    string? GetUrl() => null;

    /// <summary>Returns an instance of this endpoint with the given console.</summary>
    IEndpoint WithConsole(Console console) => this;

    private sealed class NoopEndpointImpl : IEndpoint
    {
        public ImmutableListMultimap<string, string> Describe() =>
            throw new InvalidOperationException("Instance shouldn't be used for core.feedback");

        void IStarlarkPrintableValue.Repr(Printer printer, StarlarkSemantics semantics) =>
            printer.Append("noop_endpoint");
    }
}
