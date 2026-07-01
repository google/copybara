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

using Copybara.Common;
using Copybara.Git.GitLab.Api.Entities;

namespace Copybara.Git.GitLab.Api;

/// <summary>An interface for transports that communicate with a GitLab API endpoint.</summary>
/// <remarks>
/// <para>NOTE(port): the Java interface passes a reflective <c>java.lang.reflect.Type</c> to select
/// the GSON parse target. This port uses generic type parameters resolved by
/// <see cref="System.Text.Json"/> instead.</para>
/// </remarks>
public interface IGitLabApiTransport
{
    /// <summary>Perform a GET request on the GitLab API, for the provided path.</summary>
    /// <typeparam name="T">the type the JSON response will be parsed to</typeparam>
    /// <param name="path">the path to call, e.g. projects/13422/merge_requests</param>
    /// <param name="headers">the headers to add to the HTTP request</param>
    /// <returns>the returned <typeparamref name="T"/>, if a response is returned</returns>
    /// <exception cref="GitLabApiException">if there is an issue performing the request</exception>
    /// <exception cref="Copybara.Exceptions.ValidationException">if credential retrieval fails</exception>
    T? Get<T>(string path, ImmutableListMultimap<string, string> headers);

    /// <summary>Perform a PUT request on the GitLab API, for the provided path.</summary>
    /// <typeparam name="T">the type the JSON response will be parsed to</typeparam>
    /// <param name="path">the path to call, e.g. projects/13422/merge_requests</param>
    /// <param name="request">the object to send as part of the request</param>
    /// <param name="headers">the headers to add to the HTTP request</param>
    T? Put<T>(string path, IGitLabApiEntity request, ImmutableListMultimap<string, string> headers);

    /// <summary>Perform a POST request on the GitLab API, for the provided path.</summary>
    /// <typeparam name="T">the type the JSON response will be parsed to</typeparam>
    /// <param name="path">the path to call, e.g. projects/13422/merge_requests</param>
    /// <param name="request">the object to send as part of the request</param>
    /// <param name="headers">the headers to add to the HTTP request</param>
    T? Post<T>(string path, IGitLabApiEntity request, ImmutableListMultimap<string, string> headers);

    /// <summary>Perform a DELETE request on the GitLab API, for the provided path.</summary>
    /// <param name="path">the path to call, e.g. projects/13422/merge_requests/80</param>
    void Delete(string path);
}
