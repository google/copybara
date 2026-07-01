/*
 * Copyright (C) 2020 Google Inc.
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
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara;

/// <summary>
/// Non-generic view over <see cref="EndpointProvider{T}"/>. Since callers frequently hold an
/// endpoint provider without knowing its concrete endpoint type (Java uses the raw type), this
/// interface exposes the endpoint access that doesn't depend on <c>T</c>.
/// </summary>
public interface IEndpointProvider : IStarlarkValue
{
    /// <summary>Returns the wrapped endpoint.</summary>
    IEndpoint GetEndpoint();

    // TODO(b/269526710): Remove method
    ImmutableListMultimap<string, string> Describe();
}

/// <summary>Wrapper class to prevent arbitrary instantiation of endpoints in starlark.</summary>
[StarlarkBuiltin(
    "endpoint_provider",
    Doc = "An handle for an origin or destination API in a feedback migration.",
    Documented = false)]
public class EndpointProvider<T> : IEndpointProvider
    where T : IEndpoint
{
    internal readonly T Endpoint;

    internal EndpointProvider(T endpoint) => Endpoint = endpoint;

    public T GetEndpoint() => Endpoint;

    IEndpoint IEndpointProvider.GetEndpoint() => Endpoint;

    // TODO(b/269526710): Remove method
    public ImmutableListMultimap<string, string> Describe() => Endpoint.Describe();

    /// <summary>Wrap an Endpoint.</summary>
    public static EndpointProvider<T> Wrap(T e) => new(e);
}

/// <summary>Non-generic factory helpers for <see cref="EndpointProvider{T}"/>.</summary>
public static class EndpointProvider
{
    public static EndpointProvider<T> Wrap<T>(T e)
        where T : IEndpoint => new(e);
}
