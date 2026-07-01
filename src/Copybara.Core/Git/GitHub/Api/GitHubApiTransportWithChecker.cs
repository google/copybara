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

using Copybara.Checks;
using Copybara.Common;

namespace Copybara.Git.GitHub.Api;

/// <summary>
/// An <see cref="IGitHubApiTransport"/> that runs a <see cref="ApiChecker"/> on every request before
/// delegating.
/// </summary>
public class GitHubApiTransportWithChecker : IGitHubApiTransport
{
    private readonly IGitHubApiTransport _delegate;
    private readonly ApiChecker _checker;

    public GitHubApiTransportWithChecker(IGitHubApiTransport @delegate, ApiChecker checker)
    {
        _delegate = Preconditions.CheckNotNull(@delegate);
        _checker = Preconditions.CheckNotNull(checker);
    }

    public Task<T?> GetAsync<T>(
        string path,
        ImmutableListMultimap<string, string> headers,
        string requestDescription)
    {
        _checker.Check("path", path, "response_type", typeof(T));
        return _delegate.GetAsync<T>(path, headers, requestDescription);
    }

    public Task<T?> PostAsync<T>(string path, object request, string requestType)
    {
        _checker.Check("path", path, "request", request, "response_type", typeof(T));
        return _delegate.PostAsync<T>(path, request, requestType);
    }

    public Task DeleteAsync(string path, string requestType)
    {
        _checker.Check("path", path);
        return _delegate.DeleteAsync(path, requestType);
    }
}
