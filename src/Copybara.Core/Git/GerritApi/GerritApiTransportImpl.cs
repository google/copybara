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

using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Json;

namespace Copybara.Git.GerritApi;

/// <summary>
/// Implementation of <see cref="IGerritApiTransport"/> that uses direct http calls. Port of
/// <c>com.google.copybara.git.gerritapi.GerritApiTransportImpl</c>.
/// </summary>
/// <remarks>
/// NOTE(port): the Java original uses google-http-client + gson. This port uses
/// <see cref="HttpClient"/> and <see cref="System.Text.Json"/>, and reuses
/// <see cref="GsonParserUtil"/> to strip Gerrit's <c>)]}'</c> XSSI no-execute prefix. Credentials
/// are obtained from the git credential helper via <see cref="GitRepository.CredentialFill"/>, and
/// applied as HTTP Basic auth, mirroring upstream.
/// </remarks>
public class GerritApiTransportImpl : IGerritApiTransport
{
    private static readonly TimeSpan Timeout = TimeSpan.FromMinutes(1);

    /// <summary>Serialization options mirroring gson: omit nulls, no indentation.</summary>
    private static readonly JsonSerializerOptions RequestOptions = new()
    {
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
    };

    private readonly GitRepository _repo;
    private readonly Uri _uri;
    private readonly HttpClient _httpClient;

    public GerritApiTransportImpl(GitRepository repo, Uri uri, HttpClient httpClient)
    {
        _repo = repo;
        _uri = Preconditions.CheckNotNull(uri);
        _httpClient = Preconditions.CheckNotNull(httpClient);
    }

    public async Task<T?> GetAsync<T>(string path)
    {
        var userPassword = GetCredentialsIfPresent(_uri.ToString());
        var url = GetUrl(path);
        using var request = new HttpRequestMessage(HttpMethod.Get, url);
        return await ExecuteAsync<T>(request, userPassword, url).ConfigureAwait(false);
    }

    public async Task<T?> PostAsync<T>(string path, object request)
    {
        var userPassword = GetCredentials(_uri.ToString());
        var url = GetUrl(path);
        using var httpRequest = new HttpRequestMessage(HttpMethod.Post, url)
        {
            Content = JsonContent(request),
        };
        return await ExecuteAsync<T>(httpRequest, userPassword, url).ConfigureAwait(false);
    }

    public async Task<T?> PutAsync<T>(string path, object request)
    {
        var userPassword = GetCredentials(_uri.ToString());
        var url = GetUrl(path);
        using var httpRequest = new HttpRequestMessage(HttpMethod.Put, url)
        {
            Content = JsonContent(request),
        };
        return await ExecuteAsync<T>(httpRequest, userPassword, url).ConfigureAwait(false);
    }

    public Uri GetUrl(string path)
    {
        Preconditions.CheckArgument(path.StartsWith('/'), path);
        return new Uri(_uri, _uri.AbsolutePath + path);
    }

    private static HttpContent JsonContent(object request)
    {
        string json = JsonSerializer.Serialize(request, request.GetType(), RequestOptions);
        return new StringContent(json, Encoding.UTF8, "application/json");
    }

    private async Task<T?> ExecuteAsync<T>(
        HttpRequestMessage request, GitCredential.UserPassword? userPassword, Uri url)
    {
        request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
        if (userPassword != null)
        {
            var raw = $"{userPassword.GetUsername()}:{userPassword.GetPasswordBeCareful()}";
            var encoded = Convert.ToBase64String(Encoding.UTF8.GetBytes(raw));
            request.Headers.Authorization = new AuthenticationHeaderValue("Basic", encoded);
        }

        HttpResponseMessage response;
        try
        {
            using var cts = new CancellationTokenSource(Timeout);
            response = await _httpClient.SendAsync(request, cts.Token).ConfigureAwait(false);
        }
        catch (Exception e) when (e is HttpRequestException or OperationCanceledException)
        {
            throw new RepoException("Error running Gerrit API operation " + url, e);
        }

        using (response)
        {
            if (!response.IsSuccessStatusCode)
            {
                string content = await response.Content.ReadAsStringAsync().ConfigureAwait(false);
                throw new GerritApiException(
                    (int)response.StatusCode, "Error calling gerrit", content, url.ToString());
            }

            try
            {
                return await GsonParserUtil
                    .ParseHttpResponseAsync<T>(response, stripNoExecutePrefix: true)
                    .ConfigureAwait(false);
            }
            catch (ArgumentException e)
            {
                throw new RepoException(
                    string.Format(
                        "Cannot parse response as type {0}.\nRequest: {1}\n", typeof(T), url),
                    e);
            }
        }
    }

    /// <summary>
    /// Credentials for API should be optional for any read operation (GET).
    /// </summary>
    private GitCredential.UserPassword? GetCredentialsIfPresent(string url)
    {
        try
        {
            return GetCredentials(url);
        }
        catch (ValidationException)
        {
            return null;
        }
    }

    /// <summary>Gets the credentials from the git credential helper.</summary>
    /// <exception cref="ValidationException"/>
    /// <exception cref="RepoException"/>
    private GitCredential.UserPassword GetCredentials(string url)
    {
        try
        {
            return _repo.CredentialFill(url);
        }
        catch (ValidationException e)
        {
            throw new ValidationException(
                $"Cannot get credentials for host {url}, from credentials helper", e);
        }
    }
}
