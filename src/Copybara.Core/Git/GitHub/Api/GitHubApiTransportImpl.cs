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

using System.Net.Http;
using Copybara.Exceptions;
using Copybara.Git;
using ConsoleT = Copybara.Util.Console.Console;

namespace Copybara.Git.GitHub.Api;

/// <summary>
/// An implementation of <see cref="IGitHubApiTransport"/> that uses
/// <see cref="System.Net.Http.HttpClient"/> and <c>System.Text.Json</c> for the requests.
/// </summary>
public class GitHubApiTransportImpl : AbstractGitHubApiTransport
{
    public GitHubApiTransportImpl(
        GitRepository repo,
        HttpClient httpClient,
        string storePath,
        bool bearerAuth,
        ConsoleT console,
        string webUrl)
        : base(repo, httpClient, storePath, bearerAuth, console, webUrl)
    {
    }

    protected override Task<HttpResponseMessage> ExecuteRequestAsync(HttpRequestMessage request) =>
        HttpClient.SendAsync(request);

    public override async Task DeleteAsync(string path, string requestType)
    {
        GitCredential.UserPassword credentials = GetCredentials();
        Uri url = GetFullEndpointUrl(path);
        try
        {
            Console.VerboseFmt("Executing {0}", requestType);
            using var request = new HttpRequestMessage(HttpMethod.Delete, url);
            ApplyHeaders(request, credentials, Copybara.Common.ImmutableListMultimap<string, string>.Empty);
            using HttpResponseMessage response =
                await ExecuteRequestAsync(request).ConfigureAwait(false);
            await ThrowIfError(response, "DELETE", path, null).ConfigureAwait(false);
        }
        catch (HttpRequestException e)
        {
            throw new RepoException("Error running GitHub API operation " + path, e);
        }
    }
}
