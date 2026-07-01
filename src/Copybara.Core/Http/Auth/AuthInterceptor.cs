/*
 * Copyright (C) 2023 Google LLC.
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

using System.Net.Http;
using Copybara.Common;
using Starlark.Eval;

namespace Copybara.Http.Auth;

/// <summary>Interface for adding auth headers to requests.</summary>
public interface IAuthInterceptor : IStarlarkValue
{
    /// <summary>Self-description for all used credentials.</summary>
    IReadOnlyList<ImmutableListMultimap<string, string>> DescribeCredentials();

    /// <summary>
    /// Returns an action that mutates an outgoing request to add authentication.
    ///
    /// <para>The Java version returns a Google http-client
    /// <c>HttpExecuteInterceptor</c>; here we return an <see cref="Action{HttpRequestMessage}"/>
    /// applied to the <see cref="HttpRequestMessage"/> just before it is sent.</para>
    /// </summary>
    /// <exception cref="Copybara.Credentials.CredentialRetrievalException">If credential retrieval fails.</exception>
    /// <exception cref="Copybara.Credentials.CredentialIssuingException">If credential issuing fails.</exception>
    Action<HttpRequestMessage> Interceptor();
}
