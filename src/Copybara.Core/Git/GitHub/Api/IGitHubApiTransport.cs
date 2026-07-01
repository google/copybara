/*
 * Copyright (C) 2017 Google Inc.
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
using Copybara.Exceptions;

namespace Copybara.Git.GitHub.Api;

/// <summary>HTTP transport interface for talking to GitHub.</summary>
/// <remarks>
/// Port of <c>com.google.copybara.git.github.api.GitHubApiTransport</c>. The Java version dispatches
/// on a reflective <c>java.lang.reflect.Type</c>; the C# port uses generic type parameters instead.
/// Requests are async because the underlying transport is <see cref="System.Net.Http.HttpClient"/>.
/// </remarks>
public interface IGitHubApiTransport
{
    /// <summary>Do an HTTP GET call with headers.</summary>
    /// <exception cref="RepoException"/>
    /// <exception cref="ValidationException"/>
    /// <exception cref="GitHubApiException"/>
    Task<T?> GetAsync<T>(
        string path,
        ImmutableListMultimap<string, string> headers,
        string requestDescription);

    /// <summary>Do an HTTP GET call with no additional headers.</summary>
    Task<T?> GetAsync<T>(string path, string requestDescription) =>
        GetAsync<T>(path, ImmutableListMultimap<string, string>.Empty, requestDescription);

    /// <summary>Do an HTTP POST call.</summary>
    /// <exception cref="RepoException"/>
    /// <exception cref="ValidationException"/>
    /// <exception cref="GitHubApiException"/>
    Task<T?> PostAsync<T>(string path, object request, string requestType);

    /// <summary>Do an HTTP DELETE call.</summary>
    /// <exception cref="RepoException"/>
    /// <exception cref="ValidationException"/>
    /// <exception cref="GitHubApiException"/>
    Task DeleteAsync(string path, string requestType);
}
